package io.translately.data.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Translation key — the atomic unit of localization. One [Key] lives inside
 * one [Project] and one [Namespace], and has exactly one [Translation] per
 * configured language.
 *
 * Uniqueness is `(project_id, namespace_id, key_name)`. Two different
 * namespaces can share the same `keyName` ("ios.settings.save" vs
 * "web.settings.save").
 *
 * Soft-delete via [softDeletedAt]: queries filter `IS NULL` by default so
 * recently-deleted rows can be restored within a retention window without
 * cascading through translations / comments / activity.
 *
 * [KeyMeta] side-table carries platform-specific metadata (iOS developer
 * notes, Android `context`, extractor hints) without polluting the main
 * row.
 */
@Entity
@Table(
    name = "keys",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_keys_project_namespace_name",
            columnNames = ["project_id", "namespace_id", "key_name"],
        ),
    ],
    indexes = [
        Index(name = "idx_keys_project", columnList = "project_id"),
        Index(name = "idx_keys_namespace", columnList = "namespace_id"),
        Index(name = "idx_keys_state", columnList = "state"),
    ],
)
class Key : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = ForeignKey(name = "fk_keys_project"))
    lateinit var project: Project

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "namespace_id", nullable = false, foreignKey = ForeignKey(name = "fk_keys_namespace"))
    lateinit var namespace: Namespace

    /** Caller-facing identifier, e.g. `settings.save.button`. */
    @Column(name = "key_name", nullable = false, length = 256)
    var keyName: String = ""

    @Column(name = "description", length = 1024)
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: KeyState = KeyState.NEW

    @Column(name = "soft_deleted_at")
    var softDeletedAt: Instant? = null

    // Relationships maintained from the Key side; Hibernate cascades soft-
    // parent -> child detaches, not hard deletes, because tables cascade in
    // the DB. We keep `orphanRemoval = false` so detaching a translation
    // doesn't delete the underlying row — the service layer decides.

    @OneToMany(mappedBy = "key", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    var meta: MutableSet<KeyMeta> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "key_tags",
        joinColumns = [JoinColumn(name = "key_id", foreignKey = ForeignKey(name = "fk_key_tags_key"))],
        inverseJoinColumns = [JoinColumn(name = "tag_id", foreignKey = ForeignKey(name = "fk_key_tags_tag"))],
        uniqueConstraints = [UniqueConstraint(name = "uk_key_tags_key_tag", columnNames = ["key_id", "tag_id"])],
    )
    var tags: MutableSet<Tag> = mutableSetOf()

    val isSoftDeleted: Boolean get() = softDeletedAt != null
}
