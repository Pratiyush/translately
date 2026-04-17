package io.translately.security

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SecurityScopesTest :
    DescribeSpec({

        describe("default state") {
            it("starts with an empty scope set") {
                SecurityScopes().granted shouldBe emptySet()
            }

            it("hasAll(emptyList) is vacuously true on an empty bag") {
                SecurityScopes().hasAll(emptyList()) shouldBe true
            }

            it("hasAll(non-empty) is false on an empty bag") {
                SecurityScopes().hasAll(listOf(Scope.KEYS_READ)) shouldBe false
            }

            it("hasAny returns false with no grants") {
                SecurityScopes().hasAny(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE)) shouldBe false
            }
        }

        describe("grantAll") {
            it("replaces the scope set rather than merging") {
                val sut = SecurityScopes()
                sut.grantAll(listOf(Scope.KEYS_READ))
                sut.grantAll(listOf(Scope.PROJECTS_READ))
                sut.granted shouldBe setOf(Scope.PROJECTS_READ)
            }

            it("dedupes duplicates in the input collection") {
                val sut = SecurityScopes()
                sut.grantAll(listOf(Scope.KEYS_READ, Scope.KEYS_READ, Scope.KEYS_WRITE))
                sut.granted shouldBe setOf(Scope.KEYS_READ, Scope.KEYS_WRITE)
            }

            it("returns an immutable copy via `granted`") {
                val sut = SecurityScopes()
                sut.grantAll(listOf(Scope.KEYS_READ))
                val snapshot = sut.granted
                sut.grantAll(listOf(Scope.PROJECTS_READ))
                // The snapshot must not have mutated
                snapshot shouldBe setOf(Scope.KEYS_READ)
            }
        }

        describe("revokeAll") {
            it("clears every granted scope") {
                val sut = SecurityScopes().apply { grantAll(Scope.entries) }
                sut.revokeAll()
                sut.granted shouldBe emptySet()
            }

            it("is idempotent on an empty bag") {
                val sut = SecurityScopes()
                sut.revokeAll()
                sut.granted shouldBe emptySet()
            }
        }

        describe("hasAll") {
            it("returns true when every required scope is granted (subset)") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE, Scope.PROJECTS_READ))
                    }
                sut.hasAll(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE)) shouldBe true
            }

            it("returns false when any required scope is missing") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ))
                    }
                sut.hasAll(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE)) shouldBe false
            }

            it("returns true for an empty requirement (no auth needed)") {
                SecurityScopes().hasAll(emptyList()) shouldBe true
                SecurityScopes().apply { grantAll(listOf(Scope.KEYS_READ)) }.hasAll(emptyList()) shouldBe true
            }
        }

        describe("hasAny") {
            it("returns true on the first intersection") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ))
                    }
                sut.hasAny(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE)) shouldBe true
            }

            it("returns false when the sets are disjoint") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ))
                    }
                sut.hasAny(listOf(Scope.WEBHOOKS_WRITE, Scope.AUDIT_READ)) shouldBe false
            }
        }

        describe("missing") {
            it("returns only the scopes absent from the grant") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ))
                    }
                sut.missing(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE)) shouldBe setOf(Scope.KEYS_WRITE)
            }

            it("returns the full requirement on an empty bag") {
                val required = setOf(Scope.KEYS_READ, Scope.PROJECTS_READ)
                SecurityScopes().missing(required) shouldBe required
            }

            it("returns the empty set when everything is granted") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_READ, Scope.PROJECTS_READ))
                    }
                sut.missing(listOf(Scope.KEYS_READ, Scope.PROJECTS_READ)) shouldBe emptySet()
            }
        }

        describe("granted ordering invariant") {
            // The Set type doesn't promise an order but callers (e.g. the
            // exception mapper) sort by token before serializing — so as long
            // as we always go through the public API, the observable order
            // is stable for JSON responses. This test pins the pattern.
            it("exposes `granted` as a Set the caller sorts itself") {
                val sut =
                    SecurityScopes().apply {
                        grantAll(listOf(Scope.KEYS_WRITE, Scope.KEYS_READ))
                    }
                val sortedTokens = sut.granted.map { it.token }.sorted()
                sortedTokens shouldContainExactly listOf("keys.read", "keys.write")
            }
        }
    })
