package io.translately.security

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class ScopeTest :
    DescribeSpec({

        describe("token naming convention") {
            it("every scope's token is dotted lowercase <domain>.<action>") {
                Scope.entries.forEach { scope ->
                    scope.token shouldMatch Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
                }
            }

            it("tokens are unique across the enum (no silent collisions)") {
                val tokens = Scope.entries.map { it.token }
                tokens shouldHaveSize tokens.distinct().size
            }

            it("enum has at least one read/write pair per major domain") {
                // This is a smoke check that nobody accidentally deletes a write
                // without realising a corresponding read is now unreachable.
                val withRead = Scope.entries.filter { it.token.endsWith(".read") }
                val withWrite = Scope.entries.filter { it.token.endsWith(".write") }
                (withRead.size + withWrite.size) shouldBe Scope.entries.size - 1
                // -1 accounts for AI_SUGGEST which is an action, not a read/write.
            }
        }

        describe("fromToken") {
            it("round-trips a known token back to the enum") {
                Scope.fromToken("keys.write") shouldBe Scope.KEYS_WRITE
                Scope.fromToken("org.read") shouldBe Scope.ORG_READ
            }

            it("returns null for an unknown token") {
                Scope.fromToken("nope.invalid") shouldBe null
                Scope.fromToken("") shouldBe null
            }
        }

        describe("serialize + parse") {
            it("round-trips a set of scopes through the space-separated wire format") {
                val input = setOf(Scope.KEYS_READ, Scope.KEYS_WRITE, Scope.PROJECTS_READ)
                val serialized = Scope.serialize(input)
                Scope.parse(serialized) shouldBe input
            }

            it("serialize emits stable space-separated tokens") {
                val out = Scope.serialize(listOf(Scope.KEYS_READ, Scope.KEYS_WRITE))
                out shouldBe "keys.read keys.write"
            }

            it("parse handles null, empty, and whitespace-only strings") {
                Scope.parse(null) shouldBe emptySet()
                Scope.parse("") shouldBe emptySet()
                Scope.parse("   ") shouldBe emptySet()
            }

            it("parse tolerates extra whitespace between tokens") {
                Scope.parse("  keys.read\t\tprojects.read   ") shouldBe
                    setOf(Scope.KEYS_READ, Scope.PROJECTS_READ)
            }

            it("parse silently drops unknown tokens (forward-compat with older clients)") {
                Scope.parse("keys.read bogus.token projects.read") shouldBe
                    setOf(Scope.KEYS_READ, Scope.PROJECTS_READ)
            }
        }

        describe("exhaustiveness guard") {
            it("includes every domain the v1.0 roadmap needs") {
                val expected =
                    listOf(
                        "org.read",
                        "org.write",
                        "members.read",
                        "members.write",
                        "api-keys.read",
                        "api-keys.write",
                        "audit.read",
                        "projects.read",
                        "projects.write",
                        "project-settings.write",
                        "keys.read",
                        "keys.write",
                        "translations.read",
                        "translations.write",
                        "imports.write",
                        "exports.read",
                        "ai.suggest",
                        "ai-config.write",
                        "tm.read",
                        "glossaries.read",
                        "glossaries.write",
                        "screenshots.read",
                        "screenshots.write",
                        "webhooks.read",
                        "webhooks.write",
                        "cdn.read",
                        "cdn.write",
                        "tasks.read",
                        "tasks.write",
                        "branches.read",
                        "branches.write",
                    )
                val actual = Scope.entries.map { it.token }
                actual shouldContainExactlyInAnyOrder expected
            }

            it("declaration order matches the token list above (stability)") {
                Scope.entries.map { it.token } shouldContainExactly
                    listOf(
                        "org.read",
                        "org.write",
                        "members.read",
                        "members.write",
                        "api-keys.read",
                        "api-keys.write",
                        "audit.read",
                        "projects.read",
                        "projects.write",
                        "project-settings.write",
                        "keys.read",
                        "keys.write",
                        "translations.read",
                        "translations.write",
                        "imports.write",
                        "exports.read",
                        "ai.suggest",
                        "ai-config.write",
                        "tm.read",
                        "glossaries.read",
                        "glossaries.write",
                        "screenshots.read",
                        "screenshots.write",
                        "webhooks.read",
                        "webhooks.write",
                        "cdn.read",
                        "cdn.write",
                        "tasks.read",
                        "tasks.write",
                        "branches.read",
                        "branches.write",
                    )
            }
        }
    })
