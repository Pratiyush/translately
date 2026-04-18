package io.translately.api.security

import io.translately.security.Scope
import io.translately.security.SecurityScopes
import io.translately.security.jwt.JwtClaims
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.jwt.JsonWebToken

/**
 * Bridges Quarkus / smallrye-jwt authentication into our [SecurityScopes].
 *
 * smallrye-jwt validates the `Authorization: Bearer ...` header and injects
 * a request-scoped [JsonWebToken]. On every request this filter reads the
 * JWT's `scope` claim, parses it into a [Scope] set, and grants it to
 * [SecurityScopes]. Unauthenticated requests (no Authorization header, or
 * a token that failed validation) land here with a "null subject" JWT and
 * we grant nothing — exactly what [SecurityScopes] starts with anyway.
 *
 * Also rejects refresh tokens presented as access credentials: a refresh
 * token's `typ` claim is `"refresh"`, and it has no right to call any
 * protected endpoint. Returning early with no grants means the downstream
 * [ScopeAuthorizationFilter] 403s any protected endpoint, which is the
 * correct UX — the refresh flow lives at a single `/auth/refresh` endpoint
 * that doesn't go through this authenticator.
 *
 * ### Claim reading
 *
 * Claims are read with a **typed `String` generic** (`getClaim<String>(...)`)
 * rather than `getClaim<Any?>(...)?.toString()`. Smallrye stores JSON string
 * claims as `jakarta.json.JsonString` internally, and calling `.toString()`
 * on a `JsonString` yields the quoted JSON literal (e.g. `"access"` with the
 * quote characters included) rather than the underlying value. That broke
 * the `typ != "access"` short-circuit (every access token looked like a
 * refresh token) and the `scope` parser (every scope string came back empty)
 * — see issue #151. The typed generic makes smallrye unwrap via its internal
 * converter. We also belt-and-braces strip a leading/trailing `"` pair from
 * the raw result in case any stray caller still hands us a quoted string.
 *
 * Runs at `Priorities.AUTHENTICATION` so `ScopeAuthorizationFilter` at
 * `AUTHORIZATION` sees the populated bag.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class JwtSecurityScopesFilter(
    private val jwt: Instance<JsonWebToken>,
    private val securityScopes: SecurityScopes,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        // SecurityScopes is @RequestScoped — each request starts empty. We
        // don't revokeAll() here because other authenticators (the test-only
        // X-Test-Scopes filter, eventually API keys / PATs) may run at the
        // same priority. If there's a valid JWT we union its scopes into
        // whatever's already present; if not we leave the bag alone.
        if (jwt.isUnsatisfied) return
        val token = runCatching { jwt.get() }.getOrNull() ?: return
        if (token.subject.isNullOrBlank()) return

        // Refresh tokens are forbidden as bearer credentials.
        val tokenType = readStringClaim(token, JwtClaims.TYPE)
        if (tokenType != JwtClaims.TYPE_ACCESS) return

        // Prefer the "scope" string claim; fall back to JWT "groups" which
        // smallrye-jwt populates from .groups() on the issuer side. Both
        // should agree for tokens we issue ourselves, but belt-and-braces.
        val scopeClaim = readStringClaim(token, JwtClaims.SCOPE)
        val fromScope = Scope.parse(scopeClaim)
        val fromGroups = token.groups.mapNotNull { Scope.fromToken(it) }.toSet()
        val parsed = fromScope + fromGroups
        if (parsed.isNotEmpty()) {
            securityScopes.grantAll(securityScopes.granted + parsed)
        }
    }

    /**
     * Read [claim] as a `String`, asking smallrye-jwt for the underlying
     * type via the typed generic. Defensively strips a single pair of
     * wrapping double-quotes if present — covers the `JsonString.toString()`
     * quoting regression from issue #151 even if another code path ever
     * hands us a pre-quoted claim.
     */
    private fun readStringClaim(
        token: JsonWebToken,
        claim: String,
    ): String? {
        val raw = runCatching { token.getClaim<String>(claim) }.getOrNull() ?: return null
        val trimmed = raw.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }
}
