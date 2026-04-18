package io.translately.api.auth

import io.translately.service.auth.AuthException
import io.translately.service.auth.AuthService
import jakarta.annotation.security.PermitAll
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

/**
 * Email+password auth endpoints (T103).
 *
 * Lives outside the `/api/v1/organizations/{orgId}` tenant scope — these
 * endpoints are called before the user has picked an org, so
 * `TenantRequestFilter` leaves the context unbound (by design).
 *
 * Error shape across every non-2xx response is the uniform envelope
 * `{ "error": { "code", "message", "details?" } }` specified in
 * `.kiro/steering/api-conventions.md`.
 */
@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "auth", description = "Email + password authentication flow")
@PermitAll
class AuthResource {
    @Inject
    lateinit var service: AuthService

    // ------------------------------------------------------------------
    // signup
    // ------------------------------------------------------------------

    @POST
    @Path("/signup")
    @Operation(summary = "Create a new account; sends a verification email.")
    @APIResponses(
        APIResponse(responseCode = "201", description = "Account created; verification email sent."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "409", description = "Email already registered."),
    )
    fun signup(body: SignupRequest?): Response {
        val safeBody = body ?: SignupRequest(null, null, null)
        return runFlow {
            val externalId =
                service.signup(
                    email = safeBody.email.orEmpty(),
                    password = safeBody.password.orEmpty(),
                    fullName = safeBody.fullName.orEmpty(),
                )
            Response.status(Response.Status.CREATED).entity(SignupResponse(externalId)).build()
        }
    }

    // ------------------------------------------------------------------
    // verify email
    // ------------------------------------------------------------------

    @POST
    @Path("/verify-email")
    @Operation(summary = "Consume a verification token and flip emailVerifiedAt.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Email verified."),
        APIResponse(responseCode = "400", description = "Token invalid."),
        APIResponse(responseCode = "401", description = "Token expired."),
        APIResponse(responseCode = "409", description = "Token already consumed."),
    )
    fun verifyEmail(body: TokenRequest?): Response =
        runFlow {
            service.verifyEmail(body?.token.orEmpty())
            Response.ok(SimpleStatus("verified")).build()
        }

    // ------------------------------------------------------------------
    // login + refresh
    // ------------------------------------------------------------------

    @POST
    @Path("/login")
    @Operation(summary = "Issue access + refresh tokens for a verified account.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Login successful."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Bad credentials."),
        APIResponse(responseCode = "403", description = "Email not verified."),
    )
    fun login(body: LoginRequest?): Response =
        runFlow {
            val tokens =
                service.login(
                    email = body?.email.orEmpty(),
                    password = body?.password.orEmpty(),
                )
            Response.ok(TokenPairResponse.from(tokens)).build()
        }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotate refresh tokens; single-use, replay returns 401.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "New tokens issued."),
        APIResponse(responseCode = "400", description = "Refresh token missing."),
        APIResponse(responseCode = "401", description = "Refresh token invalid or reused."),
    )
    fun refresh(body: RefreshRequest?): Response =
        runFlow {
            val tokens = service.refresh(body?.refreshToken.orEmpty())
            Response.ok(TokenPairResponse.from(tokens)).build()
        }

    // ------------------------------------------------------------------
    // forgot + reset password
    // ------------------------------------------------------------------

    @POST
    @Path("/forgot-password")
    @Operation(
        summary = "Request a password reset link. Always returns 202 — never leaks whether the email exists.",
    )
    @APIResponses(
        APIResponse(responseCode = "202", description = "Reset email queued if the account exists."),
        APIResponse(responseCode = "400", description = "Validation failed."),
    )
    fun forgotPassword(body: ForgotPasswordRequest?): Response =
        runFlow {
            service.forgotPassword(body?.email.orEmpty())
            Response.status(Response.Status.ACCEPTED).entity(SimpleStatus("queued")).build()
        }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Consume a password-reset token and update the user's password.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Password updated."),
        APIResponse(responseCode = "400", description = "Validation failed or token invalid."),
        APIResponse(responseCode = "401", description = "Token expired."),
        APIResponse(responseCode = "409", description = "Token already consumed."),
    )
    fun resetPassword(body: ResetPasswordRequest?): Response =
        runFlow {
            service.resetPassword(
                rawToken = body?.token.orEmpty(),
                newPassword = body?.newPassword.orEmpty(),
            )
            Response.ok(SimpleStatus("updated")).build()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun runFlow(block: () -> Response): Response =
        try {
            block()
        } catch (ex: AuthException) {
            errorResponse(ex)
        }

    private fun errorResponse(ex: AuthException): Response {
        val (status, details) =
            when (ex) {
                is AuthException.EmailTaken -> Response.Status.CONFLICT to null
                is AuthException.ValidationFailed ->
                    Response.Status.BAD_REQUEST to validationDetails(ex)
                is AuthException.EmailNotVerified -> Response.Status.FORBIDDEN to null
                is AuthException.InvalidCredentials -> Response.Status.UNAUTHORIZED to null
                is AuthException.TokenInvalid -> Response.Status.BAD_REQUEST to null
                is AuthException.TokenConsumed -> Response.Status.CONFLICT to null
                is AuthException.TokenExpired -> Response.Status.UNAUTHORIZED to null
                is AuthException.RefreshTokenReused -> Response.Status.UNAUTHORIZED to null
            }
        val body =
            ErrorBody(
                error = ErrorPayload(code = ex.code, message = ex.message.orEmpty(), details = details),
            )
        return Response.status(status).entity(body).build()
    }

    private fun validationDetails(ex: AuthException.ValidationFailed): Map<String, Any> =
        mapOf(
            "fields" to ex.fields.map { mapOf("path" to it.path, "code" to it.code) },
        )

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    data class SignupRequest(
        val email: String?,
        val password: String?,
        val fullName: String?,
    )

    data class SignupResponse(
        val userExternalId: String,
    )

    data class TokenRequest(
        val token: String?,
    )

    data class LoginRequest(
        val email: String?,
        val password: String?,
    )

    data class RefreshRequest(
        val refreshToken: String?,
    )

    data class ForgotPasswordRequest(
        val email: String?,
    )

    data class ResetPasswordRequest(
        val token: String?,
        val newPassword: String?,
    )

    data class SimpleStatus(
        val status: String,
    )

    data class TokenPairResponse(
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresAt: Instant,
        val refreshExpiresAt: Instant,
    ) {
        companion object {
            fun from(tokens: io.translately.security.jwt.JwtTokens): TokenPairResponse =
                TokenPairResponse(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    accessExpiresAt = tokens.accessExpiresAt,
                    refreshExpiresAt = tokens.refreshExpiresAt,
                )
        }
    }

    data class ErrorBody(
        val error: ErrorPayload,
    )

    data class ErrorPayload(
        val code: String,
        val message: String,
        val details: Map<String, Any>?,
    )
}
