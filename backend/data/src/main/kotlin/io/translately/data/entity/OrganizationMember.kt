package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Join table between [User] and [Organization] with a role.
 *
 * - One membership per (org, user) — enforced by unique constraint.
 * - `invitedAt` records when the invitation was sent; `joinedAt` when accepted.
 *   Pending invitations have `joinedAt = null`.
 * - Removing a membership doesn't soft-delete either side; you just delete the
 *   row and the access grant goes with it.
 */
@Entity
@Table(
    name = "organization_members",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_org_members_org_user", columnNames = ["organization_id", "user_id"]),
    ],
    indexes = [
        Index(name = "idx_org_members_user", columnList = "user_id"),
    ],
)
class OrganizationMember : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_org_members_organization"),
    )
    lateinit var organization: Organization

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_org_members_user"),
    )
    lateinit var user: User

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    var role: OrganizationRole = OrganizationRole.MEMBER

    @Column(name = "invited_at", nullable = false)
    var invitedAt: Instant = Instant.now()

    @Column(name = "joined_at")
    var joinedAt: Instant? = null

    val isPending: Boolean get() = joinedAt == null
}
