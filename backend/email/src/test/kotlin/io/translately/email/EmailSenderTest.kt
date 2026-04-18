package io.translately.email

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance

/**
 * Unit tests for [EmailSender] that exercise URL encoding, template data
 * wiring, and `Mail` construction without booting Quarkus.
 *
 * Qute is stubbed via MockK: we only care that the correct keys land on the
 * template and that the rendered body is forwarded to `Mailer.send`.
 */
class EmailSenderTest :
    DescribeSpec({

        fun newSender(
            mailer: Mailer,
            verifyTemplate: Template,
            resetTemplate: Template,
            baseUrl: String = "http://localhost:5173",
            from: String = "noreply@translately.local",
        ): EmailSender = EmailSender(mailer, verifyTemplate, resetTemplate, from, baseUrl)

        fun stubTemplate(rendered: String): Template {
            val tpl = mockk<Template>()
            val instance = mockk<TemplateInstance>()
            every { tpl.data(any<String>(), any()) } returns instance
            every { instance.data(any<String>(), any()) } returns instance
            every { instance.render() } returns rendered
            return tpl
        }

        describe("URL builders") {
            val mailer = mockk<Mailer>(relaxed = true)
            val sender =
                newSender(
                    mailer = mailer,
                    verifyTemplate = stubTemplate("v"),
                    resetTemplate = stubTemplate("r"),
                    baseUrl = "http://localhost:5173",
                )

            it("URL-encodes the raw token in verify links") {
                val url = sender.buildVerifyUrl("a+b=c/d")
                url shouldStartWith "http://localhost:5173/verify-email?token="
                url shouldContain "a%2Bb%3Dc%2Fd"
            }

            it("URL-encodes the raw token in reset links") {
                val url = sender.buildResetUrl("plain")
                url shouldBe "http://localhost:5173/reset-password?token=plain"
            }

            it("strips a trailing slash from the configured base URL") {
                val sender2 =
                    newSender(
                        mailer = mailer,
                        verifyTemplate = stubTemplate("v"),
                        resetTemplate = stubTemplate("r"),
                        baseUrl = "https://app.example.com/",
                    )
                sender2.buildVerifyUrl("abc") shouldBe "https://app.example.com/verify-email?token=abc"
            }
        }

        describe("sendVerification") {
            it("sends an HTML mail with the rendered verify template") {
                val mailer = mockk<Mailer>(relaxed = true)
                val captured = slot<Mail>()
                every { mailer.send(capture(captured)) } returns Unit
                val sender =
                    newSender(
                        mailer = mailer,
                        verifyTemplate = stubTemplate("<html>verify-body</html>"),
                        resetTemplate = stubTemplate("<html>ignored</html>"),
                    )

                sender.sendVerification("user@example.com", "Jamie", "raw-token")

                verify(exactly = 1) { mailer.send(any<Mail>()) }
                captured.captured.to shouldBe listOf("user@example.com")
                captured.captured.subject shouldBe EmailSender.SUBJECT_VERIFY
                captured.captured.html shouldBe "<html>verify-body</html>"
                captured.captured.from shouldBe "noreply@translately.local"
                captured.captured.text!! shouldContain "raw-token"
                captured.captured.text!! shouldContain "Hi Jamie"
            }
        }

        describe("sendPasswordReset") {
            it("sends an HTML mail with the rendered reset template") {
                val mailer = mockk<Mailer>(relaxed = true)
                val captured = slot<Mail>()
                every { mailer.send(capture(captured)) } returns Unit
                val sender =
                    newSender(
                        mailer = mailer,
                        verifyTemplate = stubTemplate("<html>ignored</html>"),
                        resetTemplate = stubTemplate("<html>reset-body</html>"),
                    )

                sender.sendPasswordReset("user@example.com", "Jamie", "reset-raw")

                captured.captured.to shouldBe listOf("user@example.com")
                captured.captured.subject shouldBe EmailSender.SUBJECT_RESET
                captured.captured.html shouldBe "<html>reset-body</html>"
                captured.captured.text!! shouldContain "reset-raw"
                captured.captured.text!! shouldContain "Someone"
            }
        }
    })
