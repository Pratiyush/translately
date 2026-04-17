package io.translately.api.tenant

import io.translately.security.tenant.TenantContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider

/**
 * JAX-RS filter that extracts the tenant identifier from the URL path and
 * binds it to the request-scoped TenantContext.
 *
 * Runs at `Priorities.AUTHENTICATION - 100` (before the real authenticator
 * populates `SecurityScopes`) so downstream authenticators and the scope
 * authorization filter can scope their work to the current tenant.
 *
 * Recognised URL shape: `/api/v1/organizations/{orgIdOrSlug}/...`.
 * Anything else (root, auth endpoints, health, metrics) leaves the context
 * with identifier = null. Resource methods reject tenant-less requests if
 * they require one.
 *
 * Validation is syntactic only: the identifier must be non-empty and
 * conform to either a ULID (26 chars, Crockford base32) or a slug
 * (lowercase kebab-case). Resolution to an internal organization_id
 * happens in service code because it requires a database hit.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
@ApplicationScoped
class TenantRequestFilter(
    private val tenantContext: TenantContext,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        tenantContext.set(extractTenant(requestContext.uriInfo.path))
    }

    /** Public so tests can exercise the regex without bootstrapping JAX-RS. */
    companion object {
        private val PATH_PATTERN =
            Regex(
                """^(?:api/v\d+/)?organizations/([^/]+)(?:/.*)?$""",
                RegexOption.IGNORE_CASE,
            )

        // ULID = 26 Crockford base32 chars; slug = lowercase kebab-case, ≤64 chars.
        private const val ULID_PATTERN = "[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}"
        private const val SLUG_PATTERN = "[a-z0-9][a-z0-9-]{0,62}[a-z0-9]"
        private val VALID_IDENTIFIER = Regex("^(?:$ULID_PATTERN|$SLUG_PATTERN)$")

        fun extractTenant(path: String): String? {
            val trimmed = path.trimStart('/')
            val match = PATH_PATTERN.matchEntire(trimmed) ?: return null
            val candidate = match.groupValues[1]
            return if (VALID_IDENTIFIER.matches(candidate)) candidate else null
        }
    }
}
