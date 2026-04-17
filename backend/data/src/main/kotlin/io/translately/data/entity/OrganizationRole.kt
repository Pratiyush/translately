package io.translately.data.entity

/**
 * Role of a user within an organization. Orthogonal to project-level RBAC (which
 * ships in T109). The highest role wins.
 */
enum class OrganizationRole {
    /** Founding member or equivalent. Full billing + destructive rights. */
    OWNER,

    /** Can manage members, projects, and settings; no billing / delete-org. */
    ADMIN,

    /** Can read org metadata and join projects they are explicitly invited to. */
    MEMBER,
}
