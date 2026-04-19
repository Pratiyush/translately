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
 * Namespace groups keys inside a [Project]. A mobile app with iOS + web
 * surfaces might use `ios` / `web` namespaces; `(project_id, namespace_id,
 * keyName)` is the uniqueness triple for [Key].
 *
 * Slug is URL-safe lowercase kebab, mirroring how [Project.slug] / tags are
 * handled elsewhere — the setter trims + lowercases so callers don't have
 * to remember.
 */
@Entity
@Table(
    name = "namespaces",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_namespaces_project_slug", columnNames = ["project_id", "slug"]),
    ],
    indexes = [
        Index(name = "idx_namespaces_project", columnList = "project_id"),
    ],
)
class Namespace : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = ForeignKey(name = "fk_namespaces_project"))
    lateinit var project: Project

    @Column(name = "slug", nullable = false, length = 64)
    var slug: String = ""
        set(value) {
            field = value.trim().lowercase()
        }

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    @Column(name = "description", length = 1024)
    var description: String? = null
}
