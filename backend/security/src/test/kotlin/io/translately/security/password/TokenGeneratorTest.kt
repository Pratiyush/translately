package io.translately.security.password

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith

class TokenGeneratorTest :
    DescribeSpec({

        describe("generate()") {
            it("default call produces 43 URL-safe characters (32 random bytes unpadded)") {
                val token = TokenGenerator.generate()
                token.length shouldBe 43
            }

            it("uses only the URL-safe base64 alphabet (no +, /, =)") {
                val token = TokenGenerator.generate()
                token shouldMatch Regex("^[A-Za-z0-9_-]+$")
            }

            it("10 000 tokens are all distinct") {
                val tokens = (1..10_000).map { TokenGenerator.generate() }.toSet()
                tokens shouldHaveSize 10_000
            }

            it("16-byte token is 22 chars long") {
                TokenGenerator.generate(bytes = 16).length shouldBe 22
            }

            it("128-byte token is 171 chars long") {
                TokenGenerator.generate(bytes = 128).length shouldBe 171
            }

            it("rejects below-minimum entropy") {
                shouldThrow<IllegalArgumentException> { TokenGenerator.generate(bytes = 15) }
                shouldThrow<IllegalArgumentException> { TokenGenerator.generate(bytes = 0) }
                shouldThrow<IllegalArgumentException> { TokenGenerator.generate(bytes = -1) }
            }

            it("rejects above-maximum entropy") {
                shouldThrow<IllegalArgumentException> { TokenGenerator.generate(bytes = 129) }
                shouldThrow<IllegalArgumentException> { TokenGenerator.generate(bytes = 1024) }
            }
        }

        describe("generatePrefixed()") {
            it("starts with the requested prefix") {
                val token = TokenGenerator.generatePrefixed("tr_apikey_")
                token shouldStartWith "tr_apikey_"
            }

            it("contains exactly one prefix + 43-char token body (default)") {
                val prefix = "tr_pat_"
                val token = TokenGenerator.generatePrefixed(prefix)
                token.length shouldBe prefix.length + 43
            }

            it("accepts any prefix (prefix is not cryptographic)") {
                TokenGenerator.generatePrefixed("") shouldMatch Regex("^[A-Za-z0-9_-]+$")
                TokenGenerator.generatePrefixed("🔑_") shouldStartWith "🔑_"
            }
        }
    })
