package io.translately.service.auth

/**
 * Minimal validation at the service boundary. The resource layer repeats
 * these checks for fast 400 responses without spinning up a transaction; we
 * re-run them here so nobody can smuggle malformed input in by calling the
 * service directly.
 *
 * We deliberately keep the rules boring:
 *
 *  * emails: contain `@` + a `.` somewhere in the domain, non-empty local
 *    and host parts, ≤254 chars. Full RFC 5322 is needlessly strict for the
 *    sign-up path; we validate deliverability via the verification round-trip.
 *  * passwords: ≥12 chars, ≤128 chars. We intentionally do not enforce
 *    "must contain a digit / symbol" patterns — NIST SP 800-63B says length
 *    + breach-list checks are the right primitives, and length alone is
 *    sufficient for the MIT ship now.
 *  * full name: non-blank, ≤128 chars.
 */
object AuthValidator {
    private const val MAX_EMAIL_LENGTH = 254
    private const val MIN_PASSWORD_LENGTH = 12
    private const val MAX_PASSWORD_LENGTH = 128
    private const val MAX_NAME_LENGTH = 128

    private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Collect every rule violation for a signup body; throw if any. */
    fun validateSignup(
        email: String?,
        password: String?,
        fullName: String?,
    ) {
        val errors = mutableListOf<AuthException.ValidationFailed.FieldError>()
        errors.addAll(emailErrors(email, path = "email"))
        errors.addAll(passwordErrors(password, path = "password"))
        errors.addAll(nameErrors(fullName, path = "fullName"))
        if (errors.isNotEmpty()) throw AuthException.ValidationFailed(errors)
    }

    /** Login: only email + password are inspected; errors are collected. */
    fun validateLogin(
        email: String?,
        password: String?,
    ) {
        val errors = mutableListOf<AuthException.ValidationFailed.FieldError>()
        errors.addAll(emailErrors(email, path = "email"))
        // For login we check presence / length only. Real credential check
        // happens later so we don't leak "account exists" via a 400 vs 401.
        if (password.isNullOrEmpty()) {
            errors.add(AuthException.ValidationFailed.FieldError("password", "REQUIRED"))
        }
        if (errors.isNotEmpty()) throw AuthException.ValidationFailed(errors)
    }

    /** Forgot-password: email must at least be syntactically valid. */
    fun validateEmail(email: String?) {
        val errors = emailErrors(email, path = "email")
        if (errors.isNotEmpty()) throw AuthException.ValidationFailed(errors)
    }

    /** Reset: new password must meet the sign-up rules and token must be present. */
    fun validateReset(
        token: String?,
        newPassword: String?,
    ) {
        val errors = mutableListOf<AuthException.ValidationFailed.FieldError>()
        if (token.isNullOrBlank()) {
            errors.add(AuthException.ValidationFailed.FieldError("token", "REQUIRED"))
        }
        errors.addAll(passwordErrors(newPassword, path = "newPassword"))
        if (errors.isNotEmpty()) throw AuthException.ValidationFailed(errors)
    }

    /** Verify / refresh: only token presence is validated here. */
    fun requireToken(
        token: String?,
        path: String,
    ) {
        if (token.isNullOrBlank()) {
            throw AuthException.ValidationFailed(
                listOf(AuthException.ValidationFailed.FieldError(path, "REQUIRED")),
            )
        }
    }

    private fun emailErrors(
        email: String?,
        path: String,
    ): List<AuthException.ValidationFailed.FieldError> {
        val out = mutableListOf<AuthException.ValidationFailed.FieldError>()
        when {
            email.isNullOrBlank() -> out.add(AuthException.ValidationFailed.FieldError(path, "REQUIRED"))
            email.length > MAX_EMAIL_LENGTH -> out.add(AuthException.ValidationFailed.FieldError(path, "TOO_LONG"))
            !EMAIL_RE.matches(email.trim()) ->
                out.add(AuthException.ValidationFailed.FieldError(path, "INVALID_EMAIL"))
        }
        return out
    }

    private fun passwordErrors(
        password: String?,
        path: String,
    ): List<AuthException.ValidationFailed.FieldError> {
        val out = mutableListOf<AuthException.ValidationFailed.FieldError>()
        when {
            password.isNullOrEmpty() ->
                out.add(AuthException.ValidationFailed.FieldError(path, "REQUIRED"))
            password.length < MIN_PASSWORD_LENGTH ->
                out.add(AuthException.ValidationFailed.FieldError(path, "TOO_SHORT"))
            password.length > MAX_PASSWORD_LENGTH ->
                out.add(AuthException.ValidationFailed.FieldError(path, "TOO_LONG"))
        }
        return out
    }

    private fun nameErrors(
        fullName: String?,
        path: String,
    ): List<AuthException.ValidationFailed.FieldError> {
        val out = mutableListOf<AuthException.ValidationFailed.FieldError>()
        when {
            fullName.isNullOrBlank() ->
                out.add(AuthException.ValidationFailed.FieldError(path, "REQUIRED"))
            fullName.length > MAX_NAME_LENGTH ->
                out.add(AuthException.ValidationFailed.FieldError(path, "TOO_LONG"))
        }
        return out
    }
}
