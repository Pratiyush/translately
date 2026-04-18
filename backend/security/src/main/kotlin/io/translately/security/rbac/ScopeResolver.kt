package io.translately.security.rbac

import io.translately.security.Scope
import jakarta.enterprise.context.ApplicationScoped

/**
 * Stateless resolver from a collection of [Membership] records to the union
 * of [Scope]s the principal holds.
 *
 * Pure function — no DB access, no caching, no side effects. Authenticators
 * (JWT login, API-key lookup, PAT lookup) call this after they've loaded the
 * principal's memberships, and write the result into
 * [io.translately.security.SecurityScopes] for the rest of the request.
 *
 * The service layer (T103 / T110 / etc.) is responsible for shaping real
 * `organization_members` rows into [Membership] instances. That keeps this
 * class free of any dependency on `:backend:data` and makes it trivial to
 * unit-test: inject nothing, pass in a list, assert on the returned set.
 */
@ApplicationScoped
open class ScopeResolver {
    /**
     * Union of [OrgRoleScopes] for every role in [memberships].
     *
     * An empty input returns the empty set (no grants). Duplicate roles or
     * repeated `(orgId, role)` pairs are collapsed by [Set] semantics — the
     * result carries each [Scope] at most once.
     */
    open fun resolveFromMemberships(memberships: Collection<Membership>): Set<Scope> {
        if (memberships.isEmpty()) return emptySet()
        return memberships.flatMapTo(mutableSetOf()) { OrgRoleScopes.forRole(it.role) }
    }

    /**
     * Scope set a user would see when acting inside a specific organization,
     * or across every org they belong to when [orgId] is null.
     *
     * Semantics:
     *  - `orgId = null` — "cross-org view": union of every role the user
     *    holds anywhere. Used for endpoints that list orgs the caller can
     *    see, or for bootstrap checks before the tenant is known.
     *  - `orgId != null` — filter [memberships] by [Membership.organizationId]
     *    first, then union. A user with no membership in that org gets the
     *    empty set (authorization will then produce a 403).
     *
     * @param userId the acting principal's id, retained for future audit /
     *   logging hooks. The current implementation does not branch on it; the
     *   parameter is intentionally kept in the signature so callers thread
     *   the principal consistently.
     * @param memberships every [Membership] the principal holds, regardless
     *   of org. Typically the full set loaded at login.
     * @param orgId optional internal organization id to filter by.
     */
    @Suppress("UNUSED_PARAMETER")
    open fun canResolveFor(
        userId: Long,
        memberships: Collection<Membership>,
        orgId: Long?,
    ): Set<Scope> {
        val filtered =
            if (orgId == null) {
                memberships
            } else {
                memberships.filter { it.organizationId == orgId }
            }
        return resolveFromMemberships(filtered)
    }
}
