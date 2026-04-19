package io.translately.service.translations

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

class IcuValidatorTest :
    DescribeSpec({

        val validator = IcuValidator()
        val en = Locale.ENGLISH

        describe("simple sources") {
            it("accepts a literal with a single named argument") {
                val result = validator.validate("Hello, {name}!", en)
                result.ok shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("accepts an empty string") {
                validator.validate("", en).ok shouldBe true
            }

            it("accepts a blank string") {
                validator.validate("   ", en).ok shouldBe true
            }

            it("accepts Unicode and emoji in plain literal") {
                validator.validate("こんにちは {name} 👋", en).ok shouldBe true
            }
        }

        describe("plural arguments") {
            it("accepts an English plural with one + other") {
                val src = "{count, plural, one {# apple} other {# apples}}"
                validator.validate(src, en).ok shouldBe true
            }

            it("accepts the full Russian keyword set") {
                val src =
                    "{n, plural, one {# яблоко} few {# яблока} many {# яблок} other {# яблока}}"
                val result = validator.validate(src, Locale.forLanguageTag("ru"))
                result.ok shouldBe true
            }

            it("accepts the Serbian keyword set") {
                val src = "{n, plural, one {X} few {Y} other {Z}}"
                val result = validator.validate(src, Locale.forLanguageTag("sr"))
                result.ok shouldBe true
            }

            it("rejects a plural missing the 'other' branch") {
                val src = "{count, plural, one {# apple}}"
                val result = validator.validate(src, en)
                result.ok shouldBe false
                result.errors shouldHaveSize 1
                result.errors.first().message shouldContain "Missing 'other' keyword"
            }

            it("rejects a selectordinal missing the 'other' branch") {
                val src = "{rank, selectordinal, one {#st}}"
                val result = validator.validate(src, en)
                result.ok shouldBe false
                result.errors.first().message shouldContain "Missing 'other' keyword"
            }
        }

        describe("select arguments") {
            it("accepts a select with other") {
                val src = "{gender, select, male {he} female {she} other {they}}"
                validator.validate(src, en).ok shouldBe true
            }

            it("rejects a select missing other") {
                val src = "{gender, select, male {he} female {she}}"
                val result = validator.validate(src, en)
                result.ok shouldBe false
                result.errors.first().message shouldContain "Missing 'other' keyword"
            }
        }

        describe("simple arg types") {
            it("accepts known ICU simple types") {
                validator.validate("Total: {amount, number}", en).ok shouldBe true
                validator.validate("On {d, date}", en).ok shouldBe true
            }

            it("rejects an unknown argument type") {
                val result = validator.validate("{x, bogusType}", en)
                result.ok shouldBe false
                result.errors shouldHaveSize 1
                result.errors.first().message shouldContain "Unknown argument type 'bogusType'"
            }
        }

        describe("malformed sources") {
            it("returns an ERROR with a line and column for a missing closing brace") {
                val result = validator.validate("Hello, {name!", en)
                result.ok shouldBe false
                result.errors shouldHaveSize 1
                val err = result.errors.first()
                err.severity shouldBe Severity.ERROR
                err.line shouldBe 1
                err.col shouldBeGreaterThan 0
            }

            it("reports line + column on a multi-line malformed source") {
                val result = validator.validate("line one\n{broken", en)
                result.ok shouldBe false
                val err = result.errors.first()
                err.line shouldBe 2
                err.col shouldBeGreaterThan 0
            }
        }

        describe("nested structures") {
            it("accepts a plural containing a nested select") {
                val src =
                    "{count, plural, one {{gender, select, male {a boy} female {a girl} other {a child}}} " +
                        "other {# kids}}"
                validator.validate(src, en).ok shouldBe true
            }

            it("reports the inner error inside a well-formed plural branch") {
                val src = "{count, plural, one {{gender, select, male {he}}} other {they}}"
                val result = validator.validate(src, en)
                result.ok shouldBe false
                result.errors.first().message shouldContain "Missing 'other' keyword"
            }
        }
    })
