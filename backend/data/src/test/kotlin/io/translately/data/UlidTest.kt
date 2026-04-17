package io.translately.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.time.Instant

class UlidTest :
    DescribeSpec({

        describe("generate") {

            it("produces 26-character output") {
                Ulid.generate().length shouldBe 26
            }

            it("uses only Crockford base32 alphabet (no I, L, O, U)") {
                val ulid = Ulid.generate()
                ulid shouldMatch Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
            }

            it("is unique across a large batch") {
                val ulids = (1..10_000).map { Ulid.generate() }.toSet()
                ulids shouldHaveSize 10_000
            }

            it("is monotonic when generated across time (timestamp prefix sorts correctly)") {
                val first = Ulid.generate(Instant.ofEpochMilli(1_700_000_000_000L))
                val later = Ulid.generate(Instant.ofEpochMilli(1_700_000_001_000L))
                (later > first) shouldBe true
            }

            it("rejects timestamps outside the 48-bit range") {
                shouldThrow<IllegalArgumentException> {
                    Ulid.generate(Instant.ofEpochMilli(-1))
                }
                shouldThrow<IllegalArgumentException> {
                    // 2^48 milliseconds > Instant.MAX; use a value beyond ULID range
                    Ulid.generate(Instant.ofEpochMilli((1L shl 48)))
                }
            }

            it("generates different ULIDs even when called with identical timestamps") {
                val instant = Instant.ofEpochMilli(1_700_000_000_000L)
                val a = Ulid.generate(instant)
                val b = Ulid.generate(instant)
                a shouldNotBe b
                a.substring(0, 10) shouldBe b.substring(0, 10) // same time prefix
            }
        }

        describe("extractTimestamp") {

            it("round-trips a generated ULID back to its millisecond timestamp") {
                val expected = Instant.ofEpochMilli(1_700_000_000_000L)
                val ulid = Ulid.generate(expected)
                Ulid.extractTimestamp(ulid) shouldBe expected
            }

            it("returns null for wrong-length input") {
                Ulid.extractTimestamp("TOO_SHORT") shouldBe null
                Ulid.extractTimestamp("X".repeat(27)) shouldBe null
            }

            it("returns null for out-of-alphabet characters") {
                Ulid.extractTimestamp("!".repeat(26)) shouldBe null
            }
        }

        describe("isValid") {

            it("accepts a fresh ULID") {
                Ulid.isValid(Ulid.generate()) shouldBe true
            }

            it("rejects wrong length") {
                Ulid.isValid("01HT7F8XYZ") shouldBe false
                Ulid.isValid("") shouldBe false
            }

            it("rejects characters outside the Crockford alphabet") {
                Ulid.isValid("!".repeat(26)) shouldBe false
            }

            it("accepts lowercase input (case-insensitive)") {
                Ulid.isValid(Ulid.generate().lowercase()) shouldBe true
            }
        }
    })
