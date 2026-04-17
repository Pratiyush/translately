package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Authenticated human identity.
 *
 * - `email` is normalized to lowercase on write so unique-constraint lookups
 *   are consistent regardless of what the user types.
 * - `passwordHash` is `null` for users that only authenticate via OIDC / LDAP.
 *   The local auth flow enforces a non-null hash separately in Phase 1 signup.
 * - `emailVerifiedAt` doubles as the "can log in" flag — null means pending verify.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_email", columnNames = ["email"])],
)
class User : BaseEntity() {
    @Column(name = "email", nullable = false, length = 254)
    var email: String = ""
        set(value) {
            field = value.trim().lowercase()
        }

    @Column(name = "email_verified_at")
    var emailVerifiedAt: Instant? = null

    @Column(name = "password_hash", length = 256)
    var passwordHash: String? = null

    @Column(name = "full_name", nullable = false, length = 128)
    var fullName: String = ""

    @Column(name = "locale", nullable = false, length = 32)
    var locale: String = "en"

    @Column(name = "timezone", nullable = false, length = 64)
    var timezone: String = "UTC"

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    /** True once the user has verified their email and is allowed to sign in. */
    val isVerified: Boolean get() = emailVerifiedAt != null

    /** True if local (email + password) authentication is configured. */
    val hasLocalPassword: Boolean get() = !passwordHash.isNullOrBlank()
}
