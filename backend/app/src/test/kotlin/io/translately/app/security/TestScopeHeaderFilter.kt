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
 * and *unions* the parsed [Scope] set into the request-scoped
 * [SecurityScopes] bean. Runs at `Priorities.AUTHENTICATION`, alongside the
 * real `JwtSecurityScopesFilter`.
 *
 * ### Why union, not replace?
 *
 * `JwtSecurityScopesFilter` also runs at `AUTHENTICATION`. Ordering between
 * request filters at the same priority is not deterministic in JAX-RS, so
 * both filters treat `SecurityScopes` as additive — whichever runs second
 * must not clobber what the first one granted. Previously this filter
 * unconditionally called `grantAll(...)` (which clears then adds); when the
 * header was absent that wiped out any JWT-granted scopes, intermittently
 * causing 403 `INSUFFICIENT_SCOPE` on endpoints that should have passed.
 * See issue #151. The filter now no-ops when the header is absent.
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
