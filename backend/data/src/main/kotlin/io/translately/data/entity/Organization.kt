package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Top-level tenant. Every business-domain row outside [User] carries an
 * `organization_id` (enforced by [Project] and indirectly by the Hibernate
 * multi-tenant filter in T111).
 *
 * `slug` is the URL-safe public identifier; shown to users in navigation
 * breadcrumbs and some CLI commands.
 */
@Entity
@Table(
    name = "organizations",
    uniqueConstraints = [UniqueConstraint(name = "uk_organizations_slug", columnNames = ["slug"])],
)
class Organization : BaseEntity() {
    @Column(name = "slug", nullable = false, length = 64)
    var slug: String = ""
        set(value) {
            field = value.trim().lowercase()
        }

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
