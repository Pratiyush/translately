package io.translately.api.security

import io.translately.security.SecurityScopes
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
 * `Authorization: Bearer tr_pat_<prefix>.<secret>`.
 *
 * PATs piggy-back on the `Bearer` scheme so the same `Authorization`
 * header works for the three common clients (curl, CLI, browser fetch).
 * The `tr_pat_` token prefix is the disambiguator: a JWT is a compact
 * `header.payload.signature` triple and never starts with `tr_pat_`, so
 * the two authenticators can share the scheme without conflict.
 * [JwtSecurityScopesFilter] silently ignores unparseable tokens, so a
 * PAT request falls through cleanly to this filter even when the two
 * run at the same priority.
 *
 * ### Scope intersection
 *
 * A PAT identifies a **user**. Unlike an API key — where the minting
 * admin already enforces scope intersection at issue time — a user's
 * effective scopes can change after the PAT was minted (role demotion,
 * leaving an org). The authenticator therefore intersects the PAT's
 * stored scope set with the user's *current* effective scopes across
 * every org they belong to. The intersection runs inside
 * [CredentialAuthenticator.authenticatePat]; this filter only pushes
 * the already-reduced set into [SecurityScopes].
 *
 * We intentionally do not bind a [TenantContext] here — PATs span every
 * org the user belongs to. The URL path continues to provide the tenant
 * identifier via `TenantRequestFilter`; service code further reduces
 * scope to that tenant when required.
 *
 * ### Failure modes
 *
 * Identical to [ApiKeyAuthenticator]: unknown prefix and bad secret
 * share a 401 `UNAUTHENTICATED` response, revoked and expired get their
 * dedicated codes.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class PatAuthenticator(
    private val authenticator: CredentialAuthenticator,
    private val securityScopes: SecurityScopes,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION) ?: return
        val token = stripBearerIfPatToken(header) ?: return

        val (prefix, secret) =
            splitPrefixAndSecret(token) ?: run {
                requestContext.abortWith(unauthenticated())
                return
            }

        when (val result = authenticator.authenticatePat(prefix, secret)) {
            is CredentialAuthResult.Pat -> {
                securityScopes.grantAll(securityScopes.granted + result.scopes)
            }
            CredentialAuthResult.Revoked -> requestContext.abortWith(revoked())
            CredentialAuthResult.Expired -> requestContext.abortWith(expired())
            CredentialAuthResult.Unauthenticated -> requestContext.abortWith(unauthenticated())
            is CredentialAuthResult.ApiKey -> requestContext.abortWith(unauthenticated())
        }
    }

    /**
     * Return the token body when [header] is `Bearer tr_pat_...`, or
     * `null` for any other shape (absent header, different scheme,
     * Bearer payload that isn't a PAT — those all route to the JWT
     * authenticator).
     */
    private fun stripBearerIfPatToken(header: String): String? {
        val trimmed = header.trim()
        val spaceIdx = trimmed.indexOf(' ')
        if (spaceIdx <= 0) return null
        val scheme = trimmed.substring(0, spaceIdx)
        if (!scheme.equals(BEARER_SCHEME, ignoreCase = true)) return null
        val token = trimmed.substring(spaceIdx + 1).trim()
        if (!token.startsWith(PAT_PREFIX)) return null
        return token
    }

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

    /** Render the uniform 401 body used for every PAT failure mode. */
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
        const val BEARER_SCHEME: String = "Bearer"

        /**
         * Token prefix that distinguishes PATs from JWTs at dispatch time.
         * Must stay in sync with [io.translately.service.credentials.PatService.PREFIX_ROOT].
         */
        const val PAT_PREFIX: String = "tr_pat_"
    }
}
