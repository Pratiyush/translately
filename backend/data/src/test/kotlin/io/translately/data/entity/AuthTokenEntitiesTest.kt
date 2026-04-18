package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

/**
 * Plain JVM tests for the auth-token entity trio — no Hibernate, no DB.
 * We only need to exercise [BaseEntity] default wiring and the `isUsable`
 * derived property that gates single-use semantics.
 */
class AuthTokenEntitiesTest :
    DescribeSpec({

        describe("RefreshToken") {
            it("starts with a fresh ULID external id and EPOCH expiry") {
                val token = RefreshToken()
                token.externalId.length shouldBe 26
                token.expiresAt shouldBe Instant.EPOCH
                token.consumedAt shouldBe null
            }

            it("is not usable when expiresAt is in the past") {
                val token =
                    RefreshToken().apply {
                        jti = "jti-old"
                        expiresAt = Instant.now().minusSeconds(60)
                    }
                token.isUsable shouldBe false
            }

            it("is usable when expiresAt is in the future and not consumed") {
                val token =
                    RefreshToken().apply {
                        jti = "jti-new"
                        expiresAt = Instant.now().plusSeconds(3600)
                    }
                token.isUsable shouldBe true
            }

            it("is not usable once consumedAt is set, even before expiry") {
                val token =
                    RefreshToken().apply {
                        jti = "jti-consumed"
                        expiresAt = Instant.now().plusSeconds(3600)
                        consumedAt = Instant.now().minusSeconds(1)
                    }
                token.isUsable shouldBe false
            }

            it("assigns different external ids per instance") {
                RefreshToken().externalId shouldNotBe RefreshToken().externalId
            }
        }

        describe("EmailVerificationToken") {
            it("is not usable past expiry") {
                val token =
                    EmailVerificationToken().apply {
                        tokenHash = "h1"
                        expiresAt = Instant.now().minusSeconds(10)
                    }
                token.isUsable shouldBe false
            }

            it("is not usable once consumed") {
                val token =
                    EmailVerificationToken().apply {
                        tokenHash = "h2"
                        expiresAt = Instant.now().plusSeconds(1_000)
                        consumedAt = Instant.now()
                    }
                token.isUsable shouldBe false
            }

            it("is usable when fresh and not consumed") {
                val token =
                    EmailVerificationToken().apply {
                        tokenHash = "h3"
                        expiresAt = Instant.now().plusSeconds(1_000)
                    }
                token.isUsable shouldBe true
            }
        }

        describe("PasswordResetToken") {
            it("mirrors the verify-token lifecycle") {
                val expired =
                    PasswordResetToken().apply {
                        tokenHash = "p1"
                        expiresAt = Instant.now().minusSeconds(60)
                    }
                val consumed =
                    PasswordResetToken().apply {
                        tokenHash = "p2"
                        expiresAt = Instant.now().plusSeconds(60)
                        consumedAt = Instant.now()
                    }
                val live =
                    PasswordResetToken().apply {
                        tokenHash = "p3"
                        expiresAt = Instant.now().plusSeconds(60)
                    }
                expired.isUsable shouldBe false
                consumed.isUsable shouldBe false
                live.isUsable shouldBe true
            }
        }
    })
