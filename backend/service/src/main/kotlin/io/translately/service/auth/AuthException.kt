package io.translately.service.auth

/**
 * Domain-layer failure modes for the auth flow. Mapped at the `:backend:api`
 * boundary to the uniform error-body contract from `api-conventions.md`.
 *
 * Each code matches the machine-readable token exposed over the wire. Never
 * rename a code — downstream clients encode them — add a new subclass and
 * deprecate the old one.
 */
sealed class AuthException(
    val code: String,
    message: String,
) : RuntimeException(message) {
    /** Email already registered for another user. */
    class EmailTaken(
        email: String,
    ) : AuthException("EMAIL_TAKEN", "Email '$email' is already in use.")

    /** Password / token failed validation at the service boundary. */
    class ValidationFailed(
        val fields: List<FieldError>,
    ) : AuthException("VALIDATION_FAILED", "One or more fields failed validation.") {
        data class FieldError(
            val path: String,
            val code: String,
        )
    }

    /** Login attempted for a user who hasn't verified their email yet. */
    class EmailNotVerified : AuthException("EMAIL_NOT_VERIFIED", "Email address has not been verified.")

    /** Login attempted with an unknown email or wrong password. */
    class InvalidCredentials : AuthException("INVALID_CREDENTIALS", "Email or password is incorrect.")

    /** Token string doesn't match any known token for the given flow. */
    class TokenInvalid : AuthException("TOKEN_INVALID", "Token is invalid or expired.")

    /** Token was already consumed (single-use semantics) or superseded. */
    class TokenConsumed : AuthException("TOKEN_CONSUMED", "Token has already been used.")

    /** Token was valid at one point but has passed its expiry. */
    class TokenExpired : AuthException("TOKEN_EXPIRED", "Token has expired.")

    /** Refresh-token jti re-presented after consumption — treat as theft. */
    class RefreshTokenReused : AuthException("REFRESH_TOKEN_REUSED", "Refresh token has already been used.")
}
