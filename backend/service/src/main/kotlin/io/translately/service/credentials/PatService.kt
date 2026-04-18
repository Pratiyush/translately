package io.translately.service.credentials

import io.translately.data.entity.Pat
import io.translately.data.entity.User
import io.translately.security.Scope
import io.translately.security.password.PasswordHasher
import io.translately.security.password.TokenGenerator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Personal Access Token management (T110) — user-scoped credentials that
 * act "as the user" across every project they belong to. Same issuance /
 * listing / revocation contract as [ApiKeyService], but the owner is a
 * [User] instead of a [Project].
 *
 * ### Token format
 *
 * ```
 * tr_pat_<8-char-base32>.<43-char-base64url>
 * └────── prefix ──────┘ └────── secret ─────┘
 * ```
 *
 * The prefix visually distinguishes PATs from API keys in logs and in the
 * UI. Parsers match on `tr_pat_` / `tr_ak_` to dispatch to the right
 * authenticator.
 */
@ApplicationScoped
open class PatService(
    private val em: EntityManager,
    private val passwordHasher: PasswordHasher,
) {
    private val log = Logger.getLogger(PatService::class.java)

    /**
     * Mint a PAT for [userExternalId].
     *
     * @param userExternalId ULID of the acting user (typically from the
     *   caller's JWT `sub`).
     * @param callerScopes the caller's effective scope set. Requested
     *   scopes must be a subset; otherwise [CredentialException.ScopeEscalation].
     * @param body mint request.
     */
    @Transactional
    open fun mint(
        userExternalId: String,
        callerScopes: Set<Scope>,
        body: MintPatRequest,
        now: Instant = Instant.now(),
    ): PatMinted {
        val requested = CredentialValidator.validateMint(body.name, body.scopes, body.expiresAt, now)

        if ((requested - callerScopes).isNotEmpty()) {
            throw CredentialException.ScopeEscalation(requested, callerScopes)
        }

        val user =
            findUserByExternalId(userExternalId)
                ?: throw CredentialException.NotFound("User")

        val (prefix, secret, fullToken) = generateTokenWithUniquePrefix()
        val hash = passwordHasher.hash(secret)

        val entity =
            Pat().apply {
                this.user = user
                this.prefix = prefix
                this.secretHash = hash
                this.name = body.name.trim()
                this.scopes = Scope.serialize(requested.toSortedSet(compareBy(Scope::token)))
                this.expiresAt = body.expiresAt
            }
        em.persist(entity)
        em.flush()

        log.infov(
            "minted pat {0} for user {1} with scopes [{2}]",
            entity.externalId,
            userExternalId,
            entity.scopes,
        )

        return PatMinted(
            id = entity.externalId,
            prefix = entity.prefix,
            secret = fullToken,
            name = entity.name,
            scopes = Scope.parse(entity.scopes),
            expiresAt = entity.expiresAt,
            createdAt = entity.createdAt,
        )
    }

    /** Return summaries (no secrets) for every PAT the user owns. */
    @Transactional
    open fun list(userExternalId: String): List<PatSummary> {
        val user =
            findUserByExternalId(userExternalId)
                ?: throw CredentialException.NotFound("User")
        return em
            .createQuery(
                "SELECT p FROM Pat p WHERE p.user.id = :userId ORDER BY p.id DESC",
                Pat::class.java,
            ).setParameter("userId", user.id)
            .resultList
            .map(::toSummary)
    }

    /** Revoke a PAT. Idempotent. */
    @Transactional
    open fun revoke(
        userExternalId: String,
        patExternalId: String,
        now: Instant = Instant.now(),
    ) {
        val user =
            findUserByExternalId(userExternalId)
                ?: throw CredentialException.NotFound("User")
        val entity =
            em
                .createQuery(
                    "SELECT p FROM Pat p WHERE p.externalId = :id AND p.user.id = :userId",
                    Pat::class.java,
                ).setParameter("id", patExternalId)
                .setParameter("userId", user.id)
                .resultList
                .firstOrNull() ?: throw CredentialException.NotFound("PAT")
        if (entity.revokedAt == null) {
            entity.revokedAt = now
            em.merge(entity)
            log.infov("revoked pat {0}", entity.externalId)
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun findUserByExternalId(externalId: String): User? =
        em
            .createQuery("SELECT u FROM User u WHERE u.externalId = :id", User::class.java)
            .setParameter("id", externalId)
            .resultList
            .firstOrNull()

    private fun generateTokenWithUniquePrefix(): Triple<String, String, String> {
        repeat(MAX_PREFIX_COLLISION_RETRIES) {
            val randomTail = TokenGenerator.generate(PREFIX_ENTROPY_BYTES).take(PREFIX_TAIL_LENGTH)
            val prefix = "$PREFIX_ROOT$randomTail"
            val collides =
                em
                    .createQuery("SELECT COUNT(p) FROM Pat p WHERE p.prefix = :prefix", java.lang.Long::class.java)
                    .setParameter("prefix", prefix)
                    .singleResult
                    .toLong() > 0
            if (!collides) {
                val secret = TokenGenerator.generate()
                val fullToken = "$prefix$SECRET_SEPARATOR$secret"
                return Triple(prefix, secret, fullToken)
            }
        }
        error("PAT prefix generation retries exhausted — check PRNG seeding or collision source")
    }

    private fun toSummary(entity: Pat): PatSummary =
        PatSummary(
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
        const val PREFIX_ROOT: String = "tr_pat_"
        const val PREFIX_TAIL_LENGTH: Int = 8
        const val PREFIX_ENTROPY_BYTES: Int = 16
        const val SECRET_SEPARATOR: String = "."
        const val MAX_PREFIX_COLLISION_RETRIES: Int = 8
    }
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

data class MintPatRequest(
    val name: String,
    val scopes: List<String>,
    val expiresAt: Instant? = null,
)

data class PatMinted(
    val id: String,
    val prefix: String,
    val secret: String,
    val name: String,
    val scopes: Set<Scope>,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

data class PatSummary(
    val id: String,
    val prefix: String,
    val name: String,
    val scopes: Set<Scope>,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
)
