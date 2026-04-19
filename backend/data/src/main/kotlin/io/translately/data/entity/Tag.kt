package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Freeform label attached to one or more [Key]s inside a [Project]. Used
 * for the key filter + search UX and as a hint to the AI prompt builder
 * (T404). The many-to-many link to [Key] lives in the `key_tags` join
 * table; Hibernate owns it from the [Key] side.
 *
 * `color` is an optional 7-character hex string ("#rrggbb") the webapp
 * uses for the chip. Validation of the format happens at the service
 * boundary, not in the entity.
 */
@Entity
@Table(
    name = "tags",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tags_project_slug", columnNames = ["project_id", "slug"]),
    ],
    indexes = [
        Index(name = "idx_tags_project", columnList = "project_id"),
    ],
)
class Tag : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = ForeignKey(name = "fk_tags_project"))
    lateinit var project: Project

    @Column(name = "slug", nullable = false, length = 64)
    var slug: String = ""
        set(value) {
            field = value.trim().lowercase()
        }

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    /** Optional `#rrggbb` display colour. */
    @Column(name = "color", length = 7)
    var color: String? = null
}
