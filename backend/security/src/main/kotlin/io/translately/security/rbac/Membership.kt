package io.translately.security.rbac

/**
 * Domain-level organization membership record used by [ScopeResolver].
 *
 * This is intentionally a tiny, immutable value class that doesn't depend on
 * any JPA entity — it's a pure input to the role-to-scope resolver. The
 * service layer translates rows from `organization_members` into instances
 * of this record before calling [ScopeResolver]; keeping the resolver free
 * of persistence types means we can unit-test it without Quarkus, JPA, or
 * Testcontainers in the loop.
 *
 * @property organizationId the internal bigserial organization identifier
 *   (matches `organizations.id`). Used by [ScopeResolver.canResolveFor] to
 *   filter memberships when the caller asks about a specific org.
 * @property role the user's [OrgRole] in that organization.
 */
data class Membership(
    val organizationId: Long,
    val role: OrgRole,
)
