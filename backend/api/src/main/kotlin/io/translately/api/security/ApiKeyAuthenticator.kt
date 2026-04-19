package io.translately.api.security

import io.translately.security.SecurityScopes
import io.translately.security.tenant.TenantContext
import io.translately.service.credentials.CredentialAuthResult
import io.translately.service.credentials.CredentialAuthenticator
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

/**
 * JAX-RS filter that authenticates a request carrying
 * `Authorization: ApiKey <prefix>.<secret>`.
 *
 * Runs at `Priorities.AUTHENTICATION` alongside [JwtSecurityScopesFilter]
 * and [PatAuthenticator]. The three are mutually exclusive in practice —
 * each inspects the `Authorization` header and returns early when the
 * scheme / prefix doesn't match, so at most one grants scopes per request.
 * `TenantRequestFilter` runs at `AUTHENTICATION - 100` to populate the
 * path-derived tenant identifier before any authenticator runs.
 *
 * ### Wire contract
 *
 * - Header: `Authorization: ApiKey <prefix>.<secret>`.
 * - Token shape: `tr_ak_<8-char-tail>.<43-char-base64url-secret>`. The
 *   prefix is the DB column; the secret half is Argon2id-verified.
 *
 * ### Failure modes
 *
 * | Situation | HTTP | Error code |
 * |---|---|---|
 * | Header absent or wrong scheme | pass-through | — |
 * | Token shape malformed | 401 | `UNAUTHENTICATED` |
 * | Prefix does not resolve | 401 | `UNAUTHENTICATED` |
 * | Argon2id verify fails | 401 | `UNAUTHENTICATED` |
 * | Credential revoked | 401 | `CREDENTIAL_REVOKED` |
 * | Credential expired | 401 | `CREDENTIAL_EXPIRED` |
 *
 * Unknown-prefix and bad-secret share a response so an attacker can't
 * distinguish "does this prefix exist?" from "is my secret right?".
 *
 * On success the filter pushes the credential's scopes into
 * [SecurityScopes] and the owning organization's slug into
 * [TenantContext] (API keys are project-scoped; the parent org is the
 * tenant boundary for multi-tenant queries).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class ApiKeyAuthenticator(
    private val authenticator: CredentialAuthenticator,
    private val securityScopes: SecurityScopes,
    private val tenantContext: TenantContext,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION) ?: return
        val rawToken = stripScheme(header) ?: return

        val (prefix, secret) =
            splitPrefixAndSecret(rawToken) ?: run {
                requestContext.abortWith(unauthenticated())
                return
            }

        when (val result = authenticator.authenticateApiKey(prefix, secret)) {
            is CredentialAuthResult.ApiKey -> {
                securityScopes.grantAll(securityScopes.granted + result.scopes)
                // Only bind the tenant if the path didn't already carry one —
                // `TenantRequestFilter` runs first, and the URL-path tenant
                // wins when present. Unbinding a URL-derived tenant here
                // would break multi-tenant scoping for scoped endpoints.
                if (!tenantContext.isBound()) {
                    tenantContext.set(result.organizationSlug)
                }
            }
            CredentialAuthResult.Revoked -> requestContext.abortWith(revoked())
            CredentialAuthResult.Expired -> requestContext.abortWith(expired())
            CredentialAuthResult.Unauthenticated -> requestContext.abortWith(unauthenticated())
            is CredentialAuthResult.Pat -> requestContext.abortWith(unauthenticated())
        }
    }

    /**
     * Return the token body if [header] is `ApiKey <token>` (case-insensitive
     * scheme). Returns `null` when the header is absent or uses a different
     * scheme — the JWT / PAT authenticators handle those.
     */
    private fun stripScheme(header: String): String? {
        val trimmed = header.trim()
        val spaceIdx = trimmed.indexOf(' ')
        if (spaceIdx <= 0) return null
        val scheme = trimmed.substring(0, spaceIdx)
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null
        val token = trimmed.substring(spaceIdx + 1).trim()
        return token.takeIf { it.isNotEmpty() }
    }

    /**
     * Split `tr_ak_<tail>.<secret>` on the single `.` separator. Returns
     * `null` when the `.` is missing or either side is empty, so the
     * filter can render a clean 401 for a malformed token.
     */
    private fun splitPrefixAndSecret(token: String): Pair<String, String>? {
        val sepIdx = token.indexOf('.')
        if (sepIdx <= 0 || sepIdx == token.length - 1) return null
        val prefix = token.substring(0, sepIdx)
        val secret = token.substring(sepIdx + 1)
        if (prefix.isEmpty() || secret.isEmpty()) return null
        return prefix to secret
    }

    private fun unauthenticated(): Response = authError("UNAUTHENTICATED", "Invalid credential.")

    private fun revoked(): Response = authError("CREDENTIAL_REVOKED", "Credential has been revoked.")

    private fun expired(): Response = authError("CREDENTIAL_EXPIRED", "Credential has expired.")

    /**
     * Render a uniform 401 body for any failure mode this filter emits.
     * Status is always `401 Unauthorized` — the upstream scope filter
     * is responsible for 403s.
     */
    private fun authError(
        code: String,
        message: String,
    ): Response =
        Response
            .status(Response.Status.UNAUTHORIZED)
            .type(MediaType.APPLICATION_JSON)
            .entity(mapOf("error" to mapOf("code" to code, "message" to message)))
            .build()

    companion object {
        const val SCHEME: String = "ApiKey"
    }
}
