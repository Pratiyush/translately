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
 * Project-scoped API key.
 *
 * Tokens are shown in full **exactly once** at creation time. We store only:
 *  - `prefix`: first 12 chars so the UI can list "translately_abc123…" safely;
 *  - `secretHash`: Argon2id of the full token, verified on each request by
 *    `security/ApiKeyAuthenticator` (T110).
 *
 * Scopes are a space-separated list of domain-level scope tokens (e.g.
 * `projects.read keys.write`). Parsed into the permission enum at request time
 * (T108).
 */
@Entity
@Table(
    name = "api_keys",
    uniqueConstraints = [UniqueConstraint(name = "uk_api_keys_prefix", columnNames = ["prefix"])],
    indexes = [Index(name = "idx_api_keys_project", columnList = "project_id")],
)
class ApiKey : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = ForeignKey(name = "fk_api_keys_project"))
    lateinit var project: Project

    @Column(name = "prefix", nullable = false, length = 16)
    var prefix: String = ""

    @Column(name = "secret_hash", nullable = false, length = 256)
    var secretHash: String = ""

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    /** Space-separated permission scopes (see T108). */
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
