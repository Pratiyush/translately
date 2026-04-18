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
import java.time.Instant

/**
 * Single-use email-verification token.
 *
 * Generated on signup and again on explicit "resend verification email"
 * requests. The raw token body is emailed to the user exactly once; only an
 * Argon2id hash is kept here so a DB dump alone cannot replay the token.
 *
 * The token is considered valid iff `consumedAt IS NULL` and `expiresAt > now`.
 * The verify flow atomically sets `consumedAt` to the current instant in
 * the same transaction that flips `users.email_verified_at`.
 */
@Entity
@Table(
    name = "email_verification_tokens",
    uniqueConstraints = [UniqueConstraint(name = "uk_email_verif_tokens_token_hash", columnNames = ["token_hash"])],
    indexes = [Index(name = "idx_email_verif_tokens_user", columnList = "user_id")],
)
class EmailVerificationToken : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_email_verif_tokens_user"))
    lateinit var user: User

    @Column(name = "token_hash", nullable = false, length = 256)
    var tokenHash: String = ""

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH

    /** True iff the token is still usable: not expired and not yet consumed. */
    val isUsable: Boolean
        get() = consumedAt == null && Instant.now().isBefore(expiresAt)
}
