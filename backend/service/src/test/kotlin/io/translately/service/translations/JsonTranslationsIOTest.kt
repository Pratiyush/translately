package io.translately.service.translations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * `JsonTranslationsIO` is the i18next JSON boundary used by both the
 * importer (T301) and the exporter (T302). The tests focus on the shape
 * rules: flat vs nested auto-detect, leaf coercion, error paths.
 */
class JsonTranslationsIOTest :
    DescribeSpec({

        val io = JsonTranslationsIO()

        describe("read — flat input") {
            it("returns every dotted top-level key verbatim") {
                val entries =
                    io.read(
                        """
                        {
                          "nav.signIn": "Sign in",
                          "nav.signOut": "Sign out"
                        }
                        """.trimIndent(),
                    )
                entries.shouldContainExactly(
                    JsonTranslationsIO.Entry("nav.signIn", "Sign in"),
                    JsonTranslationsIO.Entry("nav.signOut", "Sign out"),
                )
            }
        }

        describe("read — nested input") {
            it("flattens dotted paths from a nested object tree") {
                val entries =
                    io.read(
                        """
                        {
                          "nav": { "signIn": "Sign in", "signOut": "Sign out" },
                          "profile": { "title": "Profile" }
                        }
                        """.trimIndent(),
                    )
                entries shouldBe
                    listOf(
                        JsonTranslationsIO.Entry("nav.signIn", "Sign in"),
                        JsonTranslationsIO.Entry("nav.signOut", "Sign out"),
                        JsonTranslationsIO.Entry("profile.title", "Profile"),
                    )
            }

            it("handles deep nesting (three levels)") {
                val entries = io.read("""{"a":{"b":{"c":"deep"}}}""")
                entries shouldBe listOf(JsonTranslationsIO.Entry("a.b.c", "deep"))
            }
        }

        describe("read — leaf coercion") {
            it("coerces numbers and booleans to their string form") {
                val entries = io.read("""{"count":42,"active":true,"pi":3.14}""")
                entries shouldBe
                    listOf(
                        JsonTranslationsIO.Entry("count", "42"),
                        JsonTranslationsIO.Entry("active", "true"),
                        JsonTranslationsIO.Entry("pi", "3.14"),
                    )
            }

            it("treats null as an empty string (EMPTY per TranslationState)") {
                val entries = io.read("""{"placeholder":null}""")
                entries shouldBe listOf(JsonTranslationsIO.Entry("placeholder", ""))
            }
        }

        describe("read — error paths") {
            it("rejects an array at the top level") {
                val ex = shouldThrow<JsonShapeException> { io.read("""["not","an","object"]""") }
                ex.error.path shouldBe "$"
                ex.error.code shouldBe "NOT_AN_OBJECT"
            }

            it("rejects arrays nested inside the tree with their jq-style path") {
                val ex =
                    shouldThrow<JsonShapeException> {
                        io.read("""{"nav":{"items":["a","b"]}}""")
                    }
                ex.error.path shouldBe "nav.items"
                ex.error.code shouldBe "UNSUPPORTED_TYPE"
            }

            it("rejects invalid JSON with INVALID_JSON and includes the parser message") {
                val ex = shouldThrow<JsonShapeException> { io.read("""{"unterminated""") }
                ex.error.code shouldBe "INVALID_JSON"
                ex.error.path shouldBe "$"
                ex.error.message shouldContain "Unexpected"
            }
        }

        describe("write") {
            val entries =
                listOf(
                    JsonTranslationsIO.Entry("nav.signIn", "Sign in"),
                    JsonTranslationsIO.Entry("nav.signOut", "Sign out"),
                    JsonTranslationsIO.Entry("profile.title", "Profile"),
                )

            it("emits flat JSON preserving insertion order") {
                val out = io.write(entries, JsonTranslationsIO.Shape.FLAT)
                out shouldContain "\"nav.signIn\" : \"Sign in\""
                out shouldContain "\"profile.title\" : \"Profile\""
            }

            it("emits nested JSON walking dotted paths into the tree") {
                val out = io.write(entries, JsonTranslationsIO.Shape.NESTED)
                out shouldContain "\"nav\""
                out shouldContain "\"signIn\" : \"Sign in\""
                out shouldContain "\"profile\""
            }

            it("round-trips flat → parse → equal entries") {
                val flat = io.write(entries, JsonTranslationsIO.Shape.FLAT)
                io.read(flat) shouldBe entries
            }

            it("round-trips nested → parse → equal entries") {
                val nested = io.write(entries, JsonTranslationsIO.Shape.NESTED)
                io.read(nested) shouldBe entries
            }
        }
    })
