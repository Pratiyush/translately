package io.translately.email

import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Sends transactional email for the auth flow.
 *
 * Uses Quarkus Mailer (blocking API) against whatever SMTP the environment
 * supplies. In dev the default compose stack points at Mailpit
 * (`smtp://mailpit:1025`), which captures messages for manual inspection
 * without ever reaching a real inbox. In prod, the operator configures a
 * real SMTP via `quarkus.mailer.*` env vars.
 *
 * Templates live at `backend/email/src/main/resources/templates/email/` and
 * are rendered through Qute. Keep the HTML minimal and inline-styled — the
 * goal is to work in every mail client, not to look beautiful. A plain-text
 * alternative is built from the HTML so clients without HTML rendering
 * still see a usable link.
 */
@ApplicationScoped
open class EmailSender(
    private val mailer: Mailer,
    @Location("email/verify.html")
    private val verifyTemplate: Template,
    @Location("email/reset.html")
    private val resetTemplate: Template,
    @ConfigProperty(name = "translately.mail.from", defaultValue = "noreply@translately.local")
    private val fromAddress: String,
    @ConfigProperty(name = "translately.mail.base-url", defaultValue = "http://localhost:5173")
    private val baseUrl: String,
) {
    private val log = Logger.getLogger(EmailSender::class.java)

    /** Build the webapp URL the user clicks to verify their email. */
    open fun buildVerifyUrl(rawToken: String): String = buildLink("/verify-email", rawToken)

    /** Build the webapp URL the user clicks to reset their password. */
    open fun buildResetUrl(rawToken: String): String = buildLink("/reset-password", rawToken)

    private fun buildLink(
        path: String,
        rawToken: String,
    ): String = "${baseUrl.trimEnd('/')}$path?token=${encode(rawToken)}"

    /** Send the "please verify your email" message. */
    open fun sendVerification(
        toEmail: String,
        fullName: String,
        rawToken: String,
    ) {
        val link = buildVerifyUrl(rawToken)
        val html = verifyTemplate.data("fullName", fullName).data("link", link).render()
        val mail =
            Mail
                .withHtml(toEmail, SUBJECT_VERIFY, html)
                .setText(plainTextVerify(fullName, link))
                .setFrom(fromAddress)
        mailer.send(mail)
        log.infov("sent email-verification message to {0}", toEmail)
    }

    /** Send the "reset your password" message. */
    open fun sendPasswordReset(
        toEmail: String,
        fullName: String,
        rawToken: String,
    ) {
        val link = buildResetUrl(rawToken)
        val html = resetTemplate.data("fullName", fullName).data("link", link).render()
        val mail =
            Mail
                .withHtml(toEmail, SUBJECT_RESET, html)
                .setText(plainTextReset(fullName, link))
                .setFrom(fromAddress)
        mailer.send(mail)
        log.infov("sent password-reset message to {0}", toEmail)
    }

    private fun encode(raw: String): String = URLEncoder.encode(raw, StandardCharsets.UTF_8)

    private fun plainTextVerify(
        fullName: String,
        link: String,
    ): String =
        """
        Hi $fullName,

        Thanks for signing up for Translately. Click the link below to
        verify your email and finish creating your account.

        $link

        If you didn't sign up, you can safely ignore this message.
        """.trimIndent()

    private fun plainTextReset(
        fullName: String,
        link: String,
    ): String =
        """
        Hi $fullName,

        Someone (hopefully you) asked to reset your Translately password.
        Click the link below to choose a new one.

        $link

        If you didn't request a reset, you can safely ignore this message.
        """.trimIndent()

    companion object {
        const val SUBJECT_VERIFY = "Verify your Translately email"
        const val SUBJECT_RESET = "Reset your Translately password"
    }
}
