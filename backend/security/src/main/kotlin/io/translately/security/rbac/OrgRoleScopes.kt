package io.translately.security.rbac

import io.translately.security.Scope

/**
 * Organization-membership role, local to `:backend:security`.
 *
 * This enum intentionally shadows `io.translately.data.entity.OrganizationRole`.
 * The `:backend:security` module is a leaf module that must not depend on
 * `:backend:data`, so the RBAC resolver operates on this pure-Kotlin mirror.
 * The service layer (T103 / T110 / etc.) is responsible for translating the
 * JPA enum into this one before calling [ScopeResolver].
 *
 * Value names are kept identical so `enumValueOf<OrgRole>(dataEnum.name)`
 * round-trips cleanly.
 */
enum class OrgRole {
    /** Founding member or equivalent. Full billing + destructive rights. */
    OWNER,

    /** Can manage members, projects, and settings; no billing / destructive-org rights. */
    ADMIN,

    /** Can read org metadata and author translations inside invited projects. */
    MEMBER,
}

/**
 * Static mapping from [OrgRole] to the [Scope] set that role confers inside a
 * single organization.
 *
 * ### Design decisions
 *
 * The three built-in roles are deliberately coarse. Finer-grained rules
 * (per-project member roles, API-key scope intersection, PAT-restricted sets)
 * layer on top of this mapping in [ScopeResolver] and later in the service
 * layer. Keeping this table purely declarative lets us audit role→permission
 * changes in one place and test them exhaustively.
 *
 *  - **OWNER** = every [Scope]. Expressed as `Scope.entries.toSet()` so a
 *    newly-added scope is granted to OWNER by default — new capabilities
 *    should be safe for the founder/destructive-rights role, and forgetting
 *    to add them to OWNER would be a regression.
 *
 *  - **ADMIN** = OWNER minus three levers that should stay with the founder:
 *    - [Scope.PROJECT_SETTINGS_WRITE] — rename / archive / delete a project.
 *    - [Scope.AI_CONFIG_WRITE] — attach or rotate BYOK AI credentials.
 *    - [Scope.API_KEYS_WRITE] — mint or revoke org-level API keys.
 *    ADMIN keeps [Scope.AUDIT_READ] so admins can investigate org activity
 *    without being able to hide their own tracks by rotating API keys.
 *
 *  - **MEMBER** = every `.read` scope plus the minimal "day job" write set:
 *    [Scope.KEYS_WRITE], [Scope.TRANSLATIONS_WRITE], [Scope.IMPORTS_WRITE],
 *    and [Scope.AI_SUGGEST]. Members can author translations, trigger
 *    imports, and call AI suggest, but cannot administer the org or
 *    configure project-wide toggles.
 *
 * The invariant `OWNER ⊃ ADMIN ⊃ MEMBER` is asserted by unit tests and must
 * be preserved as scopes are added.
 */
object OrgRoleScopes {
    /** Scopes withheld from ADMIN but granted to OWNER. */
    val ADMIN_EXCLUSIONS: Set<Scope> =
        setOf(
            Scope.PROJECT_SETTINGS_WRITE,
            Scope.AI_CONFIG_WRITE,
            Scope.API_KEYS_WRITE,
        )

    /** Write scopes granted to MEMBER on top of every `.read` scope. */
    val MEMBER_WRITES: Set<Scope> =
        setOf(
            Scope.KEYS_WRITE,
            Scope.TRANSLATIONS_WRITE,
            Scope.IMPORTS_WRITE,
            Scope.AI_SUGGEST,
        )

    /** Every [Scope] whose token ends in `.read`. */
    val READ_SCOPES: Set<Scope> =
        Scope.entries.filter { it.token.endsWith(".read") }.toSet()

    /** OWNER — full control over the organization. */
    val OWNER: Set<Scope> = Scope.entries.toSet()

    /** ADMIN — everything except the three levers reserved for OWNER. */
    val ADMIN: Set<Scope> = OWNER - ADMIN_EXCLUSIONS

    /** MEMBER — every `.read` scope plus the minimal authoring write set. */
    val MEMBER: Set<Scope> = READ_SCOPES + MEMBER_WRITES

    /** Single lookup: the [Scope] set conferred by a given [OrgRole]. */
    fun forRole(role: OrgRole): Set<Scope> =
        when (role) {
            OrgRole.OWNER -> OWNER
            OrgRole.ADMIN -> ADMIN
            OrgRole.MEMBER -> MEMBER
        }
}
