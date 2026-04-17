package io.translately.api.security

import io.translately.security.RequiresScope
import io.translately.security.SecurityScopes
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ResourceInfo
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.Provider

/**
 * Container request filter that enforces [RequiresScope] declarations at the
 * JAX-RS layer. Runs at `Priorities.AUTHORIZATION` so authentication (which
 * populates [SecurityScopes]) has already executed.
 *
 * Resolution order:
 *   1. Method-level `@RequiresScope` (wins if present)
 *   2. Class-level `@RequiresScope` (fallback)
 *   3. No annotation → request passes through with no check
 *
 * Multiple scopes inside one annotation instance are **all required**
 * (intersection). See [RequiresScope] docs.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
class ScopeAuthorizationFilter(
    private val securityScopes: SecurityScopes,
) : ContainerRequestFilter {
    @Context
    private lateinit var resourceInfo: ResourceInfo

    override fun filter(requestContext: ContainerRequestContext) {
        val annotation =
            resourceInfo.resourceMethod?.getAnnotation(RequiresScope::class.java)
                ?: resourceInfo.resourceClass?.getAnnotation(RequiresScope::class.java)
                ?: return

        val required = annotation.value.toSet()
        if (required.isEmpty()) return

        if (!securityScopes.hasAll(required)) {
            throw InsufficientScopeException(
                required = required,
                missing = securityScopes.missing(required),
            )
        }
    }
}
