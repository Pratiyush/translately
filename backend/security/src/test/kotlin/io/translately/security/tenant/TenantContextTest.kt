package io.translately.security.tenant

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TenantContextTest :
    DescribeSpec({

        describe("default state") {
            it("starts unbound") {
                val ctx = TenantContext()
                ctx.current() shouldBe null
                ctx.isBound() shouldBe false
            }
        }

        describe("set") {
            it("binds a non-blank identifier") {
                val ctx = TenantContext()
                ctx.set("acme")
                ctx.current() shouldBe "acme"
                ctx.isBound() shouldBe true
            }

            it("accepts a ULID-shaped identifier") {
                val ctx = TenantContext()
                ctx.set("01HT7F8KXN0GZJYQP3M5CRSBNW")
                ctx.current() shouldBe "01HT7F8KXN0GZJYQP3M5CRSBNW"
            }

            it("treats null as clearing the context") {
                val ctx = TenantContext().apply { set("acme") }
                ctx.set(null)
                ctx.current() shouldBe null
                ctx.isBound() shouldBe false
            }

            it("treats blank as clearing the context (defense in depth)") {
                val ctx = TenantContext().apply { set("acme") }
                ctx.set("   ")
                ctx.current() shouldBe null
                ctx.isBound() shouldBe false
            }

            it("replaces an existing identifier rather than stacking") {
                val ctx = TenantContext().apply { set("old") }
                ctx.set("new")
                ctx.current() shouldBe "new"
            }
        }
    })
