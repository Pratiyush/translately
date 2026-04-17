package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ProjectTest :
    DescribeSpec({

        describe("slug normalization") {
            it("lowercases and trims") {
                val p = Project().apply { slug = "  Mobile-App-Strings  " }
                p.slug shouldBe "mobile-app-strings"
            }
        }

        describe("hasAi flag") {
            it("is false by default") {
                Project().hasAi shouldBe false
            }

            it("is false when provider is set but key is null") {
                val p = Project().apply { aiProvider = AiProvider.ANTHROPIC }
                p.hasAi shouldBe false
            }

            it("is false when key is set but provider is null") {
                val p = Project().apply { aiApiKeyEncrypted = byteArrayOf(1, 2, 3) }
                p.hasAi shouldBe false
            }

            it("is true when both provider and key are set") {
                val p =
                    Project().apply {
                        aiProvider = AiProvider.ANTHROPIC
                        aiApiKeyEncrypted = byteArrayOf(1, 2, 3)
                    }
                p.hasAi shouldBe true
            }
        }

        describe("defaults") {
            it("baseLanguageTag defaults to 'en'") {
                Project().baseLanguageTag shouldBe "en"
            }

            it("all AI fields default to null") {
                val p = Project()
                p.aiProvider shouldBe null
                p.aiModel shouldBe null
                p.aiBaseUrl shouldBe null
                p.aiApiKeyEncrypted shouldBe null
                p.aiBudgetCapUsdMonthly shouldBe null
            }
        }

        describe("budget cap accepts 2-decimal BigDecimal") {
            it("stores and retrieves the value exactly") {
                val p = Project().apply { aiBudgetCapUsdMonthly = BigDecimal("49.99") }
                p.aiBudgetCapUsdMonthly shouldBe BigDecimal("49.99")
            }
        }
    })
