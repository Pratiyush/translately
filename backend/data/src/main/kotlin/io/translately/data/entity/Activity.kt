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

/**
 * Append-only activity entry for a [Key]. Written on every state
 * transition, edit, translation, tag change, and comment.
 *
 * Phase 2 records who/what/when via [actor] + [actionType] + `createdAt`.
 * The structured `diff` JSONB column is reserved for the Phase 7 audit
 * log (T706) to fill in rich before/after payloads — Phase 2 leaves it
 * null.
 *
 * Never UPDATE or DELETE a row; inserts only. Service layer enforces.
 */
@Entity
@Table(
    name = "key_activity",
    indexes = [
        Index(name = "idx_key_activity_key", columnList = "key_id"),
        Index(name = "idx_key_activity_actor", columnList = "actor_user_id"),
        Index(name = "idx_key_activity_created", columnList = "created_at"),
    ],
)
class Activity : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false, foreignKey = ForeignKey(name = "fk_key_activity_key"))
    lateinit var key: Key

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", foreignKey = ForeignKey(name = "fk_key_activity_actor"))
    var actor: User? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 24)
    var actionType: ActivityType = ActivityType.CREATED

    /**
     * Reserved for the Phase 7 audit log (T706). Phase 2 leaves this null.
     * Stored as Postgres JSONB so the eventual payload queries without a
     * type migration.
     */
    @Column(name = "diff_json", columnDefinition = "JSONB")
    var diffJson: String? = null
}
