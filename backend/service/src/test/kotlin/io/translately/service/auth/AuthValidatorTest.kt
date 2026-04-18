package io.translately.service.auth

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.junit.jupiter.api.assertDoesNotThrow

class AuthValidatorTest :
    DescribeSpec({

        describe("validateSignup") {
            it("accepts a reasonable email + 12-char password + name") {
                assertDoesNotThrow {
                    AuthValidator.validateSignup("alice@example.com", "correcthorsestaple!", "Alice")
                }
            }

            it("collects REQUIRED for every missing field") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup(null, null, null)
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.map { it.path } shouldContain "email"
                ex.fields.map { it.path } shouldContain "password"
                ex.fields.map { it.path } shouldContain "fullName"
                ex.fields.filter { it.path == "email" }[0].code shouldBe "REQUIRED"
            }

            it("rejects an email without an @ sign") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("no-at-sign", "correcthorsestaple!", "Alice")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "email" }.code shouldBe "INVALID_EMAIL"
            }

            it("rejects an email without a domain TLD") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("alice@localhost", "correcthorsestaple!", "Alice")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "email" }.code shouldBe "INVALID_EMAIL"
            }

            it("rejects an email over 254 chars") {
                val local = "x".repeat(250)
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("$local@example.com", "correcthorsestaple!", "Alice")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "email" }.code shouldBe "TOO_LONG"
            }

            it("rejects a password shorter than 12 characters") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("a@b.c", "short", "Alice")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "password" }.code shouldBe "TOO_SHORT"
            }

            it("rejects a password longer than 128 characters") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("a@b.c", "x".repeat(129), "Alice")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "password" }.code shouldBe "TOO_LONG"
            }

            it("rejects a blank full name") {
                val ex =
                    runCatching {
                        AuthValidator.validateSignup("a@b.c", "correcthorsestaple!", "   ")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "fullName" }.code shouldBe "REQUIRED"
            }
        }

        describe("validateLogin") {
            it("is happy with email + non-empty password") {
                assertDoesNotThrow {
                    AuthValidator.validateLogin("a@b.c", "anything-really")
                }
            }

            it("REQUIRED when password is empty") {
                val ex =
                    runCatching {
                        AuthValidator.validateLogin("a@b.c", "")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first { it.path == "password" }.code shouldBe "REQUIRED"
            }
        }

        describe("validateEmail + validateReset") {
            it("validateEmail rejects blank") {
                val ex =
                    runCatching {
                        AuthValidator.validateEmail("")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first().code shouldBe "REQUIRED"
            }

            it("validateReset demands token + password rules") {
                val ex =
                    runCatching {
                        AuthValidator.validateReset(null, "short")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.map { it.path } shouldContain "token"
                ex.fields.map { it.path } shouldContain "newPassword"
            }
        }

        describe("requireToken") {
            it("throws when token is null") {
                val ex =
                    runCatching {
                        AuthValidator.requireToken(null, "refreshToken")
                    }.exceptionOrNull() as AuthException.ValidationFailed
                ex.fields.first().path shouldBe "refreshToken"
                ex.fields.first().code shouldBe "REQUIRED"
            }

            it("is a no-op when token is non-blank") {
                assertDoesNotThrow {
                    AuthValidator.requireToken("anything", "token")
                }
            }
        }

        describe("AuthException metadata") {
            it("every subclass carries a stable code token") {
                AuthException.EmailTaken("x").code shouldBe "EMAIL_TAKEN"
                AuthException.ValidationFailed(emptyList()).code shouldBe "VALIDATION_FAILED"
                AuthException.EmailNotVerified().code shouldBe "EMAIL_NOT_VERIFIED"
                AuthException.InvalidCredentials().code shouldBe "INVALID_CREDENTIALS"
                AuthException.TokenInvalid().code shouldBe "TOKEN_INVALID"
                AuthException.TokenConsumed().code shouldBe "TOKEN_CONSUMED"
                AuthException.TokenExpired().code shouldBe "TOKEN_EXPIRED"
                AuthException.RefreshTokenReused().code shouldBe "REFRESH_TOKEN_REUSED"
            }

            it("EmailTaken message mentions the email supplied") {
                AuthException.EmailTaken("bob@example.com") shouldHaveMessage
                    "Email 'bob@example.com' is already in use."
            }

            it("ValidationFailed.fields is the supplied list") {
                val ex =
                    AuthException.ValidationFailed(
                        listOf(AuthException.ValidationFailed.FieldError("a", "B")),
                    )
                ex.fields shouldNotBe emptyList<Any>()
                ex.fields.first().path shouldBe "a"
            }
        }
    })
