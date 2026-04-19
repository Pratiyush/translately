package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Freeform comment attached to a [Key]. Used for translator ⇄ reviewer
 * back-and-forth about ambiguity, context, or tone. Ordered newest-first
 * by `createdAt DESC` at the service layer.
 *
 * Author is required — anonymous comments never land; the UI requires a
 * signed-in user to post.
 */
@Entity
@Table(
    name = "key_comments",
    indexes = [
        Index(name = "idx_key_comments_key", columnList = "key_id"),
        Index(name = "idx_key_comments_author", columnList = "author_user_id"),
    ],
)
class Comment : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false, foreignKey = ForeignKey(name = "fk_key_comments_key"))
    lateinit var key: Key

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, foreignKey = ForeignKey(name = "fk_key_comments_author"))
    lateinit var author: User

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String = ""
}
