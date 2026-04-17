package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class UserTest :
    DescribeSpec({

        describe("email normalization") {
            it("lowercases the email on set") {
                val u = User().apply { email = "Pratiyush@Example.COM" }
                u.email shouldBe "pratiyush@example.com"
            }

            it("trims leading/trailing whitespace") {
                val u = User().apply { email = "  user@example.com  " }
                u.email shouldBe "user@example.com"
            }
        }

        describe("verification status") {
            it("is not verified when emailVerifiedAt is null") {
                User().isVerified shouldBe false
            }

            it("is verified once emailVerifiedAt is set") {
                val u = User().apply { emailVerifiedAt = Instant.now() }
                u.isVerified shouldBe true
            }
        }

        describe("local password") {
            it("hasLocalPassword=false when passwordHash is null") {
                User().hasLocalPassword shouldBe false
            }

            it("hasLocalPassword=false when passwordHash is blank") {
                User().apply { passwordHash = "   " }.hasLocalPassword shouldBe false
            }

            it("hasLocalPassword=true when passwordHash is present") {
                val hash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$abc\$def"
                val user = User().apply { passwordHash = hash }
                user.hasLocalPassword shouldBe true
            }
        }

        describe("default values") {
            it("defaults locale to 'en' and timezone to 'UTC'") {
                val u = User()
                u.locale shouldBe "en"
                u.timezone shouldBe "UTC"
            }

            it("generates a non-blank external ULID on construction") {
                User().externalId shouldNotBe ""
                User().externalId.length shouldBe 26
            }

            it("assigns different external IDs to each new instance") {
                User().externalId shouldNotBe User().externalId
            }
        }
    })
