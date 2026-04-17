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
 * and grants the parsed [Scope] set into the request-scoped [SecurityScopes]
 * bean. Runs at `Priorities.AUTHENTICATION`, i.e. *before*
 * `ScopeAuthorizationFilter` (which runs at `AUTHORIZATION`) — the real Phase 1
 * JWT / API-key authenticators will replace this.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class TestScopeHeaderFilter(
    private val scopes: SecurityScopes,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val header = requestContext.getHeaderString(HEADER)
        scopes.grantAll(Scope.parse(header))
    }

    companion object {
        const val HEADER = "X-Test-Scopes"
    }
}
