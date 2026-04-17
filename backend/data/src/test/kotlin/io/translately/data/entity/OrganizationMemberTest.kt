package io.translately.data.entity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OrganizationMemberTest :
    DescribeSpec({

        describe("default role") {
            it("is MEMBER") {
                OrganizationMember().role shouldBe OrganizationRole.MEMBER
            }
        }

        describe("pending state") {
            it("is pending while joinedAt is null") {
                OrganizationMember().isPending shouldBe true
            }

            it("stops being pending once joinedAt is set") {
                val m = OrganizationMember().apply { joinedAt = Instant.now() }
                m.isPending shouldBe false
            }
        }
    })
