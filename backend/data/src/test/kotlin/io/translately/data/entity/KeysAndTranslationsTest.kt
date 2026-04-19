package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class KeysAndTranslationsTest :
    DescribeSpec({

        describe("Namespace slug normalization") {
            it("lowercases and trims") {
                Namespace().apply { slug = "  WEB-Strings  " }.slug shouldBe "web-strings"
            }
        }

        describe("Tag slug normalization") {
            it("lowercases and trims") {
                Tag().apply { slug = "  Priority-HIGH  " }.slug shouldBe "priority-high"
            }

            it("color is null by default") {
                Tag().color shouldBe null
            }
        }

        describe("Key defaults + soft-delete") {
            it("state defaults to NEW") {
                Key().state shouldBe KeyState.NEW
            }

            it("soft_deleted_at is null by default") {
                Key().isSoftDeleted shouldBe false
                Key().softDeletedAt shouldBe null
            }

            it("isSoftDeleted flips true when softDeletedAt is set") {
                val k = Key().apply { softDeletedAt = Instant.now() }
                k.isSoftDeleted shouldBe true
            }

            it("tags + meta collections start empty") {
                val k = Key()
                k.tags.isEmpty() shouldBe true
                k.meta.isEmpty() shouldBe true
            }
        }

        describe("Translation defaults") {
            it("state defaults to EMPTY") {
                Translation().state shouldBe TranslationState.EMPTY
            }

            it("value defaults to empty string") {
                Translation().value shouldBe ""
            }

            it("author is null by default (imports have no author)") {
                Translation().author shouldBe null
            }
        }

        describe("Activity defaults") {
            it("actionType defaults to CREATED") {
                Activity().actionType shouldBe ActivityType.CREATED
            }

            it("diffJson is null — Phase 7 audit log will fill it") {
                Activity().diffJson shouldBe null
            }
        }

        describe("KeyState enum covers the documented lifecycle") {
            it("has exactly four states matching the CHECK constraint") {
                KeyState.entries.map { it.name }.toSet() shouldBe
                    setOf("NEW", "TRANSLATING", "REVIEW", "DONE")
            }
        }

        describe("TranslationState enum covers the documented lifecycle") {
            it("has exactly five states matching the CHECK constraint") {
                TranslationState.entries.map { it.name }.toSet() shouldBe
                    setOf("EMPTY", "DRAFT", "TRANSLATED", "REVIEW", "APPROVED")
            }
        }

        describe("ActivityType enum covers the documented action set") {
            it("has exactly the seven action types matching the CHECK constraint") {
                ActivityType.entries.map { it.name }.toSet() shouldBe
                    setOf(
                        "CREATED",
                        "UPDATED",
                        "DELETED",
                        "STATE_CHANGED",
                        "TRANSLATED",
                        "COMMENTED",
                        "TAGGED",
                    )
            }
        }
    })
