package io.translately.service.credentials

import io.translately.data.entity.ApiKey
import io.translately.data.entity.OrganizationMember
import io.translately.data.entity.Pat
import io.translately.security.Scope
import io.translately.security.password.PasswordHasher
import io.translately.security.rbac.Membership
import io.translately.security.rbac.OrgRole
import io.translately.security.rbac.ScopeResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Server-side lookup + verification for long-lived credentials (API keys +
 * PATs). Callers live in `:backend:api` where we can't touch
 * `:backend:data` directly — this service is the boundary.
 *
 * ### Design decisions
 *
 * - Every lookup returns the uniform [CredentialAuthResult] sealed type
 *   rather than throwing on bad input. Authenticator filters hand callers
 *   a 401 for any failure mode, so distinguishing "no match" from "bad
 *   secret" via exception types would only add noise.
 * - `last_used_at` is stamped **synchronously** in the same transaction
 *   that verifies the credential. A future follow-up can move this to a
 *   Quartz job (see [CredentialAuthenticator.markUsed] TODO); the
 *   synchronous path keeps the first cut boring.
 * - Revoked / expired checks run after the Argon2id verify so a malformed
 *   secret on a revoked key still burns the same wall-clock as a live
 *   one — no timing oracle for "is this key revoked?".
 */
@ApplicationScoped
open class CredentialAuthenticator(
    private val em: EntityManager,
    private val passwordHasher: PasswordHasher,
    private val scopeResolver: ScopeResolver,
) {
    private val log = Logger.getLogger(CredentialAuthenticator::class.java)

    /**
     * Look up and verify an API key by `prefix` + `secret`. The caller is
     * expected to have already split the `tr_ak_<prefix>.<secret>` wire
     * format into its two halves.
     *
     * @param prefix the full `tr_ak_<tail>` prefix (stored as-is in
     *   `api_keys.prefix`).
     * @param secret the 43-char base64url secret half.
     * @param now clock seam for tests.
     */
    @Transactional
    open fun authenticateApiKey(
        prefix: String,
        secret: String,
        now: Instant = Instant.now(),
    ): CredentialAuthResult {
        val key = findApiKeyByPrefix(prefix)
        val status = verifyStatus(key?.secretHash, secret, key?.revokedAt, key?.expiresAt, now)
        if (status != null || key == null) return status ?: CredentialAuthResult.Unauthenticated

        markUsedApiKey(key, now)

        return CredentialAuthResult.ApiKey(
            apiKeyExternalId = key.externalId,
            projectExternalId = key.project.externalId,
            organizationExternalId = key.project.organization.externalId,
            organizationSlug = key.project.organization.slug,
            scopes = Scope.parse(key.scopes),
        )
    }

    /**
     * Look up and verify a PAT by `prefix` + `secret`. Computes the
     * effective scope set as the **intersection** of the PAT's stored
     * scopes and the owning user's effective scopes across their org
     * memberships. Cross-org scope reduction keeps a PAT from granting
     * more than the user currently holds.
     */
    @Transactional
    open fun authenticatePat(
        prefix: String,
        secret: String,
        now: Instant = Instant.now(),
    ): CredentialAuthResult {
        val pat = findPatByPrefix(prefix)
        val status = verifyStatus(pat?.secretHash, secret, pat?.revokedAt, pat?.expiresAt, now)
        if (status != null || pat == null) return status ?: CredentialAuthResult.Unauthenticated

        val memberships = findMembershipsForUser(pat.user.id!!)
        val userEffectiveScopes = scopeResolver.resolveFromMemberships(memberships)
        val patScopes = Scope.parse(pat.scopes)
        val effective = patScopes.intersect(userEffectiveScopes)

        markUsedPat(pat, now)

        return CredentialAuthResult.Pat(
            patExternalId = pat.externalId,
            userExternalId = pat.user.externalId,
            scopes = effective,
        )
    }

    /**
     * Shared verification steps for API keys and PATs. Returns one of
     * the [CredentialAuthResult] failure variants when the credential
     * fails the check, or `null` to mean "row is valid and live — keep
     * going". Callers use that null as a cue to run their specialised
     * happy-path branch (the ApiKey / PAT variant).
     *
     * We intentionally run `passwordHasher.verify` even when the prefix
     * miss so the caller can't time-fingerprint the "does this prefix
     * exist?" question — a future hardening would involve verifying
     * against a static fake hash here too; for now a missing row short-
     * circuits with an `Unauthenticated` result.
     */
    private fun verifyStatus(
        storedHash: String?,
        presentedSecret: String,
        revokedAt: Instant?,
        expiresAt: Instant?,
        now: Instant,
    ): CredentialAuthResult? =
        when {
            storedHash == null || !passwordHasher.verify(presentedSecret, storedHash) ->
                CredentialAuthResult.Unauthenticated
            revokedAt != null -> CredentialAuthResult.Revoked
            expiresAt != null && !now.isBefore(expiresAt) -> CredentialAuthResult.Expired
            else -> null
        }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun findApiKeyByPrefix(prefix: String): ApiKey? =
        em
            .createQuery("SELECT a FROM ApiKey a WHERE a.prefix = :prefix", ApiKey::class.java)
            .setParameter("prefix", prefix)
            .resultList
            .firstOrNull()

    private fun findPatByPrefix(prefix: String): Pat? =
        em
            .createQuery("SELECT p FROM Pat p WHERE p.prefix = :prefix", Pat::class.java)
            .setParameter("prefix", prefix)
            .resultList
            .firstOrNull()

    private fun findMembershipsForUser(userId: Long): List<Membership> =
        em
            .createQuery(
                "SELECT m FROM OrganizationMember m WHERE m.user.id = :uid AND m.joinedAt IS NOT NULL",
                OrganizationMember::class.java,
            ).setParameter("uid", userId)
            .resultList
            .map { Membership(organizationId = it.organization.id!!, role = it.role.toDomain()) }

    /**
     * Stamp `last_used_at`. Runs inline with the request for v0.1.0 —
     * a Quartz-backed batch update would move the write off the hot
     * path once we have measurable load. Tracking issue lives in the
     * post-MVP backlog under "move credential last_used_at off the
     * request path".
     */
    private fun markUsedApiKey(
        key: ApiKey,
        now: Instant,
    ) {
        key.lastUsedAt = now
        em.merge(key)
        log.debugv("api-key {0} used", key.externalId)
    }

    private fun markUsedPat(
        pat: Pat,
        now: Instant,
    ) {
        pat.lastUsedAt = now
        em.merge(pat)
        log.debugv("pat {0} used", pat.externalId)
    }

    private fun io.translately.data.entity.OrganizationRole.toDomain(): OrgRole =
        when (this) {
            io.translately.data.entity.OrganizationRole.OWNER -> OrgRole.OWNER
            io.translately.data.entity.OrganizationRole.ADMIN -> OrgRole.ADMIN
            io.translately.data.entity.OrganizationRole.MEMBER -> OrgRole.MEMBER
        }
}

/**
 * Outcome of a credential lookup + verify. The authenticator filter in
 * `:backend:api` maps these onto HTTP 200 (Authenticated variants) or 401
 * (failure variants). Keeping the sealed hierarchy here means the filter
 * never needs to touch an [ApiKey] or [Pat] entity.
 */
sealed class CredentialAuthResult {
    /**
     * Prefix did not resolve to any credential, or the Argon2id verify
     * failed. Intentionally collapses "no match" and "bad secret" into
     * one variant to avoid a timing / information side-channel.
     */
    data object Unauthenticated : CredentialAuthResult()

    /** Credential row exists and secret matches, but `revoked_at` is set. */
    data object Revoked : CredentialAuthResult()

    /** Credential row exists and secret matches, but `expires_at` has passed. */
    data object Expired : CredentialAuthResult()

    /** Successful API key authentication — scopes are taken verbatim from the row. */
    data class ApiKey(
        val apiKeyExternalId: String,
        val projectExternalId: String,
        val organizationExternalId: String,
        val organizationSlug: String,
        val scopes: Set<Scope>,
    ) : CredentialAuthResult()

    /** Successful PAT authentication — scopes are already intersected with user's effective set. */
    data class Pat(
        val patExternalId: String,
        val userExternalId: String,
        val scopes: Set<Scope>,
    ) : CredentialAuthResult()
}
