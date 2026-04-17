package io.translately.security.password

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

/**
 * Exercises the production defaults (3 iterations, 64 MiB, 4 threads).
 * Each Argon2id hash takes ~30-60 ms, so the suite spends most of its
 * runtime in CPU — keep the assertion count sane.
 */
class PasswordHasherTest :
    DescribeSpec({

        val hasher = PasswordHasher()

        describe("hash output shape") {
            it("starts with \$argon2id\$ prefix") {
                hasher.hash("hunter2") shouldStartWith "\$argon2id\$"
            }

            it("embeds the requested parameters (m=65536,t=3,p=4)") {
                val h = hasher.hash("hunter2")
                h.contains("m=65536") shouldBe true
                h.contains("t=3") shouldBe true
                h.contains("p=4") shouldBe true
            }

            it("produces different hashes for the same password (fresh salt)") {
                val a = hasher.hash("hunter2")
                val b = hasher.hash("hunter2")
                a shouldNotBe b
            }
        }

        describe("verify") {
            val stored = hasher.hash("correct horse battery staple")

            it("returns true for the exact password") {
                hasher.verify("correct horse battery staple", stored) shouldBe true
            }

            it("returns false for a wrong password") {
                hasher.verify("wrong guess", stored) shouldBe false
            }

            it("returns false for a close-but-wrong password (case)") {
                hasher.verify("Correct Horse Battery Staple", stored) shouldBe false
            }

            it("returns false for null hash") {
                hasher.verify("anything", null) shouldBe false
            }

            it("returns false for blank hash") {
                hasher.verify("anything", "   ") shouldBe false
            }

            it("returns false for malformed hash without throwing") {
                hasher.verify("anything", "not-a-hash") shouldBe false
                hasher.verify("anything", "\$argon2id\$totally-broken") shouldBe false
            }
        }

        describe("input handling") {
            it("accepts and verifies an empty password") {
                val h = hasher.hash("")
                hasher.verify("", h) shouldBe true
                hasher.verify("x", h) shouldBe false
            }

            it("accepts and verifies a long (1 KiB) password") {
                val pw = "x".repeat(1024)
                val h = hasher.hash(pw)
                hasher.verify(pw, h) shouldBe true
                hasher.verify(pw.dropLast(1), h) shouldBe false
            }

            it("accepts and verifies Unicode including emoji") {
                val pw = "p@\$\$wörd 🔒 日本"
                val h = hasher.hash(pw)
                hasher.verify(pw, h) shouldBe true
            }
        }

        describe("multiple users (cross-contamination guard)") {
            it("two different passwords never hash to the same value") {
                val hashes = List(10) { hasher.hash("password-$it") }
                hashes shouldHaveSize hashes.toSet().size
            }

            it("one user's hash never verifies a different user's password") {
                val hashA = hasher.hash("alice-password")
                val hashB = hasher.hash("bob-password")
                hasher.verify("alice-password", hashB) shouldBe false
                hasher.verify("bob-password", hashA) shouldBe false
            }
        }
    })
