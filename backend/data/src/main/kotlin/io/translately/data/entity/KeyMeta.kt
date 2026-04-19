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
 * Side-table key/value metadata for a [Key]. Used for platform-specific
 * hints that don't warrant first-class columns — Android `context`, iOS
 * developer notes, extractor hints, export-only flags, etc.
 *
 * Unique per `(key_id, meta_key)` so a single key can only set each
 * meta-key once; overwrites happen via UPDATE, not INSERT-duplicate.
 */
@Entity
@Table(
    name = "key_meta",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_key_meta_key_meta_key", columnNames = ["key_id", "meta_key"]),
    ],
    indexes = [
        Index(name = "idx_key_meta_key", columnList = "key_id"),
    ],
)
class KeyMeta : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false, foreignKey = ForeignKey(name = "fk_key_meta_key"))
    lateinit var key: Key

    @Column(name = "meta_key", nullable = false, length = 128)
    var metaKey: String = ""

    @Column(name = "meta_value", nullable = false, length = 4096)
    var metaValue: String = ""
}
