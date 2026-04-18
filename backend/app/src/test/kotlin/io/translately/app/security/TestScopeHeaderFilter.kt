package io.translately.app.security

import io.translately.security.Scope
import io.translately.security.SecurityScopes
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider

/**
 * Test-only authenticator: reads the space-separated `X-Test-Scopes` header
 * and **unions** the parsed [Scope] set into the request-scoped
 * [SecurityScopes] bean. Runs at `Priorities.AUTHENTICATION`, i.e. before
 * `ScopeAuthorizationFilter` (which runs at `AUTHORIZATION`).
 *
 * ### Why union, not replace?
 *
 * Multiple authenticators run at `AUTHENTICATION` priority —
 * [io.translately.api.security.JwtSecurityScopesFilter],
 * [io.translately.api.security.ApiKeyAuthenticator],
 * [io.translately.api.security.PatAuthenticator], and this one. JAX-RS
 * ordering between filters at the same priority is not deterministic, so
 * every authenticator treats `SecurityScopes` as additive — whichever runs
 * last must not clobber what earlier ones granted.
 *
 * This filter previously called `grantAll(...)` unconditionally (which
 * clears then adds). When the header was absent that wiped out any
 * already-granted scopes (JWT's, API-key's, PAT's), causing intermittent
 * 403 `INSUFFICIENT_SCOPE` on endpoints that should have passed — see
 * issue #151 for the original regression. The same union invariant now
 * protects the #149 API-key + PAT authenticators from the same trap.
 * The filter no-ops when the header is absent.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class TestScopeHeaderFilter(
    private val scopes: SecurityScopes,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val header = requestContext.getHeaderString(HEADER) ?: return
        val parsed = Scope.parse(header)
        if (parsed.isNotEmpty()) {
            scopes.grantAll(scopes.granted + parsed)
        }
    }

    companion object {
        const val HEADER = "X-Test-Scopes"
    }
}
