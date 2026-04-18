package io.translately.service.credentials

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.translately.security.Scope
import java.time.Instant

class CredentialValidatorTest :
    DescribeSpec({

        val now = Instant.parse("2026-04-18T12:00:00Z")

        describe("validateMint — happy path") {
            it("resolves well-formed scope tokens to a Scope set") {
                val resolved =
                    CredentialValidator.validateMint(
                        name = "CI publisher",
                        scopeTokens = listOf("keys.read", "keys.write"),
                        expiresAt = null,
                        now = now,
                    )
                resolved shouldContainExactlyInAnyOrder setOf(Scope.KEYS_READ, Scope.KEYS_WRITE)
            }

            it("accepts a future expiry") {
                val resolved =
                    CredentialValidator.validateMint(
                        name = "CI publisher",
                        scopeTokens = listOf("keys.read"),
                        expiresAt = now.plusSeconds(60),
                        now = now,
                    )
                resolved shouldBe setOf(Scope.KEYS_READ)
            }

            it("trims whitespace around the name") {
                // Validation passes; the service will also trim before persist.
                CredentialValidator.validateMint(
                    name = "  CI publisher  ",
                    scopeTokens = listOf("keys.read"),
                    expiresAt = null,
                    now = now,
                )
            }
        }

        describe("validateMint — validation failures") {
            it("rejects an empty name with REQUIRED") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "",
                            scopeTokens = listOf("keys.read"),
                            expiresAt = null,
                            now = now,
                        )
                    }
                ex.fields.map { it.path } shouldContain "body.name"
                ex.fields.first { it.path == "body.name" }.code shouldBe "REQUIRED"
            }

            it("rejects a whitespace-only name with REQUIRED") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "   ",
                            scopeTokens = listOf("keys.read"),
                            expiresAt = null,
                            now = now,
                        )
                    }
                ex.fields.any { it.path == "body.name" && it.code == "REQUIRED" } shouldBe true
            }

            it("rejects a name over 128 chars with TOO_LONG") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "a".repeat(129),
                            scopeTokens = listOf("keys.read"),
                            expiresAt = null,
                            now = now,
                        )
                    }
                ex.fields.any { it.path == "body.name" && it.code == "TOO_LONG" } shouldBe true
            }

            it("rejects an empty scope list with REQUIRED") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "x",
                            scopeTokens = emptyList(),
                            expiresAt = null,
                            now = now,
                        )
                    }
                ex.fields.any { it.path == "body.scopes" && it.code == "REQUIRED" } shouldBe true
            }

            it("rejects an expiry at or before now with MUST_BE_FUTURE") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "x",
                            scopeTokens = listOf("keys.read"),
                            expiresAt = now,
                            now = now,
                        )
                    }
                ex.fields.any { it.path == "body.expiresAt" && it.code == "MUST_BE_FUTURE" } shouldBe true
            }

            it("collects multiple field errors in one exception") {
                val ex =
                    shouldThrow<CredentialException.ValidationFailed> {
                        CredentialValidator.validateMint(
                            name = "",
                            scopeTokens = emptyList(),
                            expiresAt = now.minusSeconds(1),
                            now = now,
                        )
                    }
                ex.fields.map { it.path }.toSet() shouldBe
                    setOf("body.name", "body.scopes", "body.expiresAt")
            }
        }

        describe("validateMint — unknown scope") {
            it("throws UnknownScope with the offending token echoed back") {
                val ex =
                    shouldThrow<CredentialException.UnknownScope> {
                        CredentialValidator.validateMint(
                            name = "x",
                            scopeTokens = listOf("keys.read", "not-a-real-scope"),
                            expiresAt = null,
                            now = now,
                        )
                    }
                ex.token shouldBe "not-a-real-scope"
            }

            it("does not throw ValidationFailed when only the scope token is wrong") {
                // Order matters: structural validation first, unknown-scope second.
                // A wholly-valid request with one unknown token should surface
                // UnknownScope, not VALIDATION_FAILED.
                val ex =
                    runCatching {
                        CredentialValidator.validateMint(
                            name = "x",
                            scopeTokens = listOf("unknown.scope"),
                            expiresAt = null,
                            now = now,
                        )
                    }.exceptionOrNull()
                (ex is CredentialException.UnknownScope) shouldBe true
            }
        }
    })
