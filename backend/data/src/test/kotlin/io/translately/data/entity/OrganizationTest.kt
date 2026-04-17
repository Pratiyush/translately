package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OrganizationTest :
    DescribeSpec({

        describe("slug normalization") {
            it("lowercases on set") {
                val org = Organization().apply { slug = "Acme-Corp" }
                org.slug shouldBe "acme-corp"
            }

            it("trims whitespace") {
                val org = Organization().apply { slug = "  acme-corp  " }
                org.slug shouldBe "acme-corp"
            }
        }

        describe("defaults") {
            it("has a 26-char external ULID out of the box") {
                Organization().externalId.length shouldBe 26
            }
        }
    })
