package io.translately.app.auth

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.translately.service.auth.AuthException
import io.translately.service.auth.AuthService
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * End-to-end `@QuarkusTest` exercise of [AuthService] against real
 * Postgres + Mailpit (both booted via [PostgresAndMailpitResource]).
 *
 * Every test acts on a fresh user because each invocation generates a
 * unique email; the DB state is not reset between tests, which mirrors
 * how a user-in-production would see the service behave.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class AuthServiceIT {
    @Inject
    lateinit var service: AuthService

    @Inject
    lateinit var em: EntityManager

    @Inject
    lateinit var testHelpers: AuthTestHelpers

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private val mailpitUrl: String
        get() =
            System.getProperty("translately.test.mailpit-url")
                ?: error("translately.test.mailpit-url was not set by PostgresAndMailpitResource")

    private fun uniqueEmail(): String = "user-${System.nanoTime()}@example.com"

    private fun extractToken(
        mailText: String,
        paramName: String,
    ): String {
        val rx = Regex("""[?&]$paramName=([A-Za-z0-9_\-]+)""")
        val match =
            rx.find(mailText) ?: error("No $paramName parameter found in mail body: ${mailText.take(500)}")
        return match.groupValues[1]
    }

    // ------------------------------------------------------------------
    // signup + verify-email
    // ------------------------------------------------------------------

    @Test
    fun `signup then verify-email then login issues tokens`() {
        val email = uniqueEmail()
        val password = "correcthorsestaple!"
        val externalId = service.signup(email, password, "Jamie Example")
        assertNotNull(externalId)

        // pre-verify: login rejected with EMAIL_NOT_VERIFIED
        assertThrows(AuthException.EmailNotVerified::class.java) {
            service.login(email, password)
        }

        // fetch the verify link from the mail we just sent
        val body = MailpitClient(mailpitUrl).waitForMessage(email)
        val rawToken = extractToken(body, "token")
        service.verifyEmail(rawToken)

        val tokens = service.login(email, password)
        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)
        assertNotNull(tokens.refreshJti)
    }

    @Test
    fun `password reset round-trip updates the hash`() {
        val email = uniqueEmail()
        val oldPassword = "correcthorsestaple!"
        service.signup(email, oldPassword, "Jamie Reset")
        // Drop the verification email first, then ask for a reset so the
        // mail we read next is the reset one. `waitForMessage` returns the
        // most recent mail for that address.
        MailpitClient(mailpitUrl).waitForMessage(email)

        service.forgotPassword(email)
        val body = MailpitClient(mailpitUrl).waitForMessage(email)
        val rawToken = extractToken(body, "token")

        testHelpers.markVerified(email)
        val newPassword = "freshpassword!2026"
        service.resetPassword(rawToken, newPassword)

        // old password now rejected; new password works
        assertThrows(AuthException.InvalidCredentials::class.java) {
            service.login(email, oldPassword)
        }
        val tokens = service.login(email, newPassword)
        assertNotNull(tokens.accessToken)

        // replaying the same reset token → TokenConsumed
        assertThrows(AuthException.TokenConsumed::class.java) {
            service.resetPassword(rawToken, "another-passphrase!")
        }
    }

    @Test
    fun `duplicate email signup throws EmailTaken`() {
        val email = uniqueEmail()
        service.signup(email, "correcthorsestaple!", "Jamie")
        assertThrows(AuthException.EmailTaken::class.java) {
            service.signup(email, "correcthorsestaple!", "Other")
        }
    }

    @Test
    fun `invalid-email signup throws ValidationFailed`() {
        assertThrows(AuthException.ValidationFailed::class.java) {
            service.signup("not-an-email", "correcthorsestaple!", "Jamie")
        }
    }

    @Test
    fun `password shorter than 12 chars throws ValidationFailed`() {
        assertThrows(AuthException.ValidationFailed::class.java) {
            service.signup(uniqueEmail(), "short", "Jamie")
        }
    }

    @Test
    fun `login for unknown user returns InvalidCredentials (does not leak existence)`() {
        assertThrows(AuthException.InvalidCredentials::class.java) {
            service.login(uniqueEmail(), "correcthorsestaple!")
        }
    }

    @Test
    fun `login with wrong password returns InvalidCredentials`() {
        val email = uniqueEmail()
        service.signup(email, "correcthorsestaple!", "Jamie")
        // Force verification directly via SQL so we can isolate the
        // credentials-check path from the token flow (which is covered
        // end-to-end by AuthResourceIT).
        testHelpers.markVerified(email)
        assertThrows(AuthException.InvalidCredentials::class.java) {
            service.login(email, "something-else-entirely")
        }
    }

    @Test
    fun `verified user login returns distinct refresh jtis on repeat login`() {
        val email = uniqueEmail()
        val pass = "correcthorsestaple!"
        service.signup(email, pass, "Jamie")
        testHelpers.markVerified(email)
        val first = service.login(email, pass)
        val second = service.login(email, pass)
        assertNotEquals(first.refreshJti, second.refreshJti)
        assertNotEquals(first.refreshToken, second.refreshToken)
    }

    @Test
    fun `refresh rotates tokens and invalidates prior jti`() {
        val email = uniqueEmail()
        val pass = "correcthorsestaple!"
        service.signup(email, pass, "Jamie")
        testHelpers.markVerified(email)
        val first = service.login(email, pass)
        val rotated = service.refresh(first.refreshToken)
        assertNotEquals(first.refreshJti, rotated.refreshJti)
        // Re-using the original refresh JWT now that its jti is consumed →
        // REFRESH_TOKEN_REUSED.
        assertThrows(AuthException.RefreshTokenReused::class.java) {
            service.refresh(first.refreshToken)
        }
    }

    @Test
    fun `refresh with a malformed JWT returns TokenInvalid`() {
        assertThrows(AuthException.TokenInvalid::class.java) {
            service.refresh("not.a.valid.jwt")
        }
    }

    @Test
    fun `forgotPassword silently returns when the account is unknown`() {
        // No exception. Side effect (email send) is implicitly skipped.
        service.forgotPassword(uniqueEmail())
    }

    @Test
    fun `forgotPassword queues a reset when the account exists`() {
        val email = uniqueEmail()
        service.signup(email, "correcthorsestaple!", "Jamie")
        service.forgotPassword(email)
        // Row written → non-zero count.
        val count =
            em
                .createNativeQuery(
                    """
                    SELECT COUNT(*) FROM password_reset_tokens prt
                    JOIN users u ON u.id = prt.user_id
                    WHERE u.email = ?1
                    """.trimIndent(),
                ).setParameter(1, email)
                .singleResult as Number
        assertEquals(1, count.toInt())
    }
}
