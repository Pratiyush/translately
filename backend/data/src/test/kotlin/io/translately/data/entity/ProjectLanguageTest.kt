package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ProjectLanguageTest :
    DescribeSpec({

        describe("direction default") {
            it("is LTR out of the box") {
                ProjectLanguage().direction shouldBe LanguageDirection.LTR
            }
        }

        describe("field defaults") {
            it("languageTag and name default to empty strings (must be set before persist)") {
                val pl = ProjectLanguage()
                pl.languageTag shouldBe ""
                pl.name shouldBe ""
            }
        }
    })
