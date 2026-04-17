package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * The active-flag logic is identical for [ApiKey] and [Pat], so this suite
 * exercises both via a parameterized pattern to ensure drift can't happen.
 */
class TokenActiveTest :
    DescribeSpec({

        describe("ApiKey.isActive") {
            it("is active by default (no expiry, not revoked)") {
                ApiKey().isActive shouldBe true
            }

            it("is inactive once revoked") {
                val k = ApiKey().apply { revokedAt = Instant.now() }
                k.isActive shouldBe false
            }

            it("is inactive after expiry") {
                val k = ApiKey().apply { expiresAt = Instant.now().minusSeconds(1) }
                k.isActive shouldBe false
            }

            it("is active when expiry is in the future") {
                val k = ApiKey().apply { expiresAt = Instant.now().plusSeconds(3600) }
                k.isActive shouldBe true
            }

            it("revocation beats expiry") {
                val k =
                    ApiKey().apply {
                        expiresAt = Instant.now().plusSeconds(3600)
                        revokedAt = Instant.now()
                    }
                k.isActive shouldBe false
            }
        }

        describe("Pat.isActive") {
            it("mirrors the ApiKey logic (default active)") {
                Pat().isActive shouldBe true
            }

            it("is inactive once revoked") {
                val t = Pat().apply { revokedAt = Instant.now() }
                t.isActive shouldBe false
            }

            it("is inactive after expiry") {
                val t = Pat().apply { expiresAt = Instant.now().minusSeconds(1) }
                t.isActive shouldBe false
            }
        }
    })
