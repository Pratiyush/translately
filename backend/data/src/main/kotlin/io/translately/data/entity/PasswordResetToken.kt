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
 * Single-use password-reset token.
 *
 * Generated on `POST /auth/forgot-password` if the email matches a real
 * user (and silently skipped otherwise, so the API does not leak
 * enumeration). The raw token body is emailed to the user exactly once and
 * only an Argon2id hash is kept here. Reset is atomic: consume the token
 * and update `users.password_hash` in one transaction.
 */
@Entity
@Table(
    name = "password_reset_tokens",
    uniqueConstraints = [UniqueConstraint(name = "uk_password_reset_tokens_token_hash", columnNames = ["token_hash"])],
    indexes = [Index(name = "idx_password_reset_tokens_user", columnList = "user_id")],
)
class PasswordResetToken : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_password_reset_tokens_user"))
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
