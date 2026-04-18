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
 * Refresh-token ledger row.
 *
 * A refresh token is a JWT with a minimal claim set; this row is the server
 * side of its single-use semantics. The JWT carries a `jti` claim that we
 * also write here. On `POST /auth/refresh`:
 *
 *  1. Verify JWT signature + `typ=refresh` + not expired.
 *  2. Look up the row by `jti`. Missing → 401. Already consumed → 401.
 *  3. Set `consumedAt = now`, mint a fresh pair, write the new row.
 *
 * Treating the JWT as the authoritative signed artifact lets us avoid
 * storing the token body at all; the `jti` is the stable handle. The column
 * is `VARCHAR(64)` because [io.translately.security.password.TokenGenerator]
 * emits 43 base64url chars by default for a 32-byte jti.
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = [UniqueConstraint(name = "uk_refresh_tokens_jti", columnNames = ["jti"])],
    indexes = [Index(name = "idx_refresh_tokens_user", columnList = "user_id")],
)
class RefreshToken : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_refresh_tokens_user"))
    lateinit var user: User

    @Column(name = "jti", nullable = false, length = 64)
    var jti: String = ""

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH

    /** True iff the token is still usable: not expired and not yet consumed. */
    val isUsable: Boolean
        get() = consumedAt == null && Instant.now().isBefore(expiresAt)
}
