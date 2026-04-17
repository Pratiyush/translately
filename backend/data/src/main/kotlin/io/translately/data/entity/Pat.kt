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
 * Personal Access Token — user-scoped, cross-project, like a long-lived JWT
 * that the user can revoke. Used by the CLI and by any integration that acts
 * "as the user" without going through OAuth.
 *
 * Same storage model as [ApiKey]: show full token at creation once, keep only
 * prefix + Argon2id hash. Authorization scope is carried with the token; the
 * intersection of (user's org memberships) and (token scopes) wins.
 */
@Entity
@Table(
    name = "personal_access_tokens",
    uniqueConstraints = [UniqueConstraint(name = "uk_pats_prefix", columnNames = ["prefix"])],
    indexes = [Index(name = "idx_pats_user", columnList = "user_id")],
)
class Pat : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_pats_user"))
    lateinit var user: User

    @Column(name = "prefix", nullable = false, length = 16)
    var prefix: String = ""

    @Column(name = "secret_hash", nullable = false, length = 256)
    var secretHash: String = ""

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    @Column(name = "scopes", nullable = false, length = 512)
    var scopes: String = ""

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    val isActive: Boolean
        get() {
            if (revokedAt != null) return false
            val exp = expiresAt ?: return true
            return Instant.now().isBefore(exp)
        }
}
