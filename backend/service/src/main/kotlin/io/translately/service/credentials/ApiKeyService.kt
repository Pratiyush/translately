package io.translately.service.credentials

import io.translately.data.entity.ApiKey
import io.translately.data.entity.Project
import io.translately.security.Scope
import io.translately.security.password.PasswordHasher
import io.translately.security.password.TokenGenerator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Use-case entry point for API-key management (T110) — project-scoped,
 * long-lived credentials used by server-to-server integrations, the CLI,
 * and CI jobs.
 *
 * ### Lifecycle
 *
 * ```
 * mint  →  secret shown once (201)           persist(prefix + Argon2id hash)
 * list  →  summaries, no secrets (200)
 * revoke → stamp revoked_at (204, idempotent)
 * ```
 *
 * Authentication of API-key-bearing requests against protected endpoints
 * is handled by a separate filter (follow-up PR); this service is only
 * about issuance / observation / revocation.
 *
 * ### Token format
 *
 * Full token shown once at mint time:
 *
 * ```
 * tr_ak_<8-char-base32>.<43-char-base64url>
 * └───── prefix ──────┘ └────── secret ─────┘
 * ```
 *
 * The prefix is the public identifier stored in the DB (`prefix` column)
 * and shown in listings. The secret half is Argon2id-hashed before
 * storage; the plaintext is discarded after the 201 response. The dot
 * separator keeps the prefix distinguishable from the base64url-encoded
 * secret, which may itself contain `_` and `-`.
 */
@ApplicationScoped
open class ApiKeyService(
    private val em: EntityManager,
    private val passwordHasher: PasswordHasher,
) {
    private val log = Logger.getLogger(ApiKeyService::class.java)

    /**
     * Mint a new API key for [projectExternalId].
     *
     * @param projectExternalId ULID of the target project.
     * @param callerScopes the effective scope set the caller holds in the
     *   target organization. Used to reject privilege escalation.
     * @param body mint request (name, scopes, optional expiry).
     * @throws CredentialException.ValidationFailed on boundary-rule failure.
     * @throws CredentialException.UnknownScope on an unrecognised scope token.
     * @throws CredentialException.ScopeEscalation when a requested scope
     *   is outside [callerScopes].
     * @throws CredentialException.NotFound when [projectExternalId] does
     *   not resolve.
     */
    @Transactional
    open fun mint(
        projectExternalId: String,
        callerScopes: Set<Scope>,
        body: MintApiKeyRequest,
        now: Instant = Instant.now(),
    ): ApiKeyMinted {
        val requested = CredentialValidator.validateMint(body.name, body.scopes, body.expiresAt, now)

        val missing = requested - callerScopes
        if (missing.isNotEmpty()) {
            throw CredentialException.ScopeEscalation(requested, callerScopes)
        }

        val project =
            findProjectByExternalId(projectExternalId)
                ?: throw CredentialException.NotFound("Project")

        // Retry prefix generation a bounded number of times in the
        // astronomically unlikely event of a collision on the 8-char random
        // tail. 32^8 = 2^40; at 1M keys per project a birthday-collision
        // still needs ~2^20 trials. Two retries are defensive overkill.
        val (prefix, secret, fullToken) = generateTokenWithUniquePrefix()
        val hash = passwordHasher.hash(secret)

        val entity =
            ApiKey().apply {
                this.project = project
                this.prefix = prefix
                this.secretHash = hash
                this.name = body.name.trim()
                this.scopes = Scope.serialize(requested.toSortedSet(compareBy(Scope::token)))
                this.expiresAt = body.expiresAt
            }
        em.persist(entity)
        em.flush()

        log.infov(
            "minted api-key {0} for project {1} with scopes [{2}]",
            entity.externalId,
            projectExternalId,
            entity.scopes,
        )

        return ApiKeyMinted(
            id = entity.externalId,
            prefix = entity.prefix,
            secret = fullToken,
            name = entity.name,
            scopes = Scope.parse(entity.scopes),
            expiresAt = entity.expiresAt,
            createdAt = entity.createdAt,
        )
    }

    /** Return summaries (no secrets) for every API key in the project. */
    @Transactional
    open fun list(projectExternalId: String): List<ApiKeySummary> {
        val project =
            findProjectByExternalId(projectExternalId)
                ?: throw CredentialException.NotFound("Project")
        return em
            .createQuery(
                "SELECT a FROM ApiKey a WHERE a.project.id = :projectId ORDER BY a.id DESC",
                ApiKey::class.java,
            ).setParameter("projectId", project.id)
            .resultList
            .map(::toSummary)
    }

    /** Revoke an API key. Idempotent: revoking a revoked key is a no-op. */
    @Transactional
    open fun revoke(
        projectExternalId: String,
        apiKeyExternalId: String,
        now: Instant = Instant.now(),
    ) {
        val project =
            findProjectByExternalId(projectExternalId)
                ?: throw CredentialException.NotFound("Project")
        val entity =
            em
                .createQuery(
                    "SELECT a FROM ApiKey a WHERE a.externalId = :id AND a.project.id = :projectId",
                    ApiKey::class.java,
                ).setParameter("id", apiKeyExternalId)
                .setParameter("projectId", project.id)
                .resultList
                .firstOrNull() ?: throw CredentialException.NotFound("API key")
        if (entity.revokedAt == null) {
            entity.revokedAt = now
            em.merge(entity)
            log.infov("revoked api-key {0}", entity.externalId)
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun findProjectByExternalId(externalId: String): Project? =
        em
            .createQuery("SELECT p FROM Project p WHERE p.externalId = :id", Project::class.java)
            .setParameter("id", externalId)
            .resultList
            .firstOrNull()

    private fun generateTokenWithUniquePrefix(): Triple<String, String, String> {
        repeat(MAX_PREFIX_COLLISION_RETRIES) {
            val randomTail = TokenGenerator.generate(PREFIX_ENTROPY_BYTES).take(PREFIX_TAIL_LENGTH)
            val prefix = "$PREFIX_ROOT$randomTail"
            val collides =
                em
                    .createQuery("SELECT COUNT(a) FROM ApiKey a WHERE a.prefix = :prefix", java.lang.Long::class.java)
                    .setParameter("prefix", prefix)
                    .singleResult
                    .toLong() > 0
            if (!collides) {
                val secret = TokenGenerator.generate()
                val fullToken = "$prefix$SECRET_SEPARATOR$secret"
                return Triple(prefix, secret, fullToken)
            }
        }
        error("API-key prefix generation retries exhausted — check PRNG seeding or collision source")
    }

    private fun toSummary(entity: ApiKey): ApiKeySummary =
        ApiKeySummary(
            id = entity.externalId,
            prefix = entity.prefix,
            name = entity.name,
            scopes = Scope.parse(entity.scopes),
            expiresAt = entity.expiresAt,
            lastUsedAt = entity.lastUsedAt,
            revokedAt = entity.revokedAt,
            createdAt = entity.createdAt,
        )

    companion object {
        /** `tr_ak_` — visual signal that this credential is an API key. */
        const val PREFIX_ROOT: String = "tr_ak_"

        /** 8 Crockford-ish base32 chars after the root. Total prefix = 14. */
        const val PREFIX_TAIL_LENGTH: Int = 8

        /** 32-byte entropy → far more than 8 chars can display; we trim. */
        const val PREFIX_ENTROPY_BYTES: Int = 16

        /** `.` — keeps the secret (base64url, may contain `_` or `-`) parseable. */
        const val SECRET_SEPARATOR: String = "."

        /** Paranoid retry budget; collisions are astronomically unlikely. */
        const val MAX_PREFIX_COLLISION_RETRIES: Int = 8
    }
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

/** Inbound — mint an API key. */
data class MintApiKeyRequest(
    val name: String,
    val scopes: List<String>,
    val expiresAt: Instant? = null,
)

/** Outbound — the 201 body. The `secret` field is populated exactly once. */
data class ApiKeyMinted(
    val id: String,
    val prefix: String,
    /** Full token — `{prefix}.{secret}`, shown once. Never persisted in plaintext. */
    val secret: String,
    val name: String,
    val scopes: Set<Scope>,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

/** Outbound — list row. No secrets. */
data class ApiKeySummary(
    val id: String,
    val prefix: String,
    val name: String,
    val scopes: Set<Scope>,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
)
