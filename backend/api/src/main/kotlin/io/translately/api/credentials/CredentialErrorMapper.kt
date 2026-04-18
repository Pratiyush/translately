package io.translately.api.credentials

import io.translately.service.credentials.CredentialException
import jakarta.ws.rs.core.Response

/**
 * Single source of truth for mapping [CredentialException] to the uniform
 * error envelope defined in `.kiro/steering/api-conventions.md`:
 *
 * ```json
 * {
 *   "error": {
 *     "code":    "…",
 *     "message": "…",
 *     "details": { … optional … }
 *   }
 * }
 * ```
 *
 * Kept as a top-level object rather than a JAX-RS `ExceptionMapper` so the
 * resources can catch and render in-band (preserving the `try { } catch`
 * shape the auth resource uses) without a global mapper affecting
 * unrelated endpoints.
 */
object CredentialErrorMapper {
    fun toResponse(ex: CredentialException): Response {
        val (status, details) =
            when (ex) {
                is CredentialException.ValidationFailed ->
                    Response.Status.BAD_REQUEST to
                        mapOf(
                            "fields" to
                                ex.fields.map { mapOf("path" to it.path, "code" to it.code) },
                        )
                is CredentialException.ScopeEscalation ->
                    Response.Status.FORBIDDEN to
                        mapOf(
                            "requested" to ex.requested.map { it.token }.sorted(),
                            "held" to ex.held.map { it.token }.sorted(),
                            "missing" to ex.missing.map { it.token }.sorted(),
                        )
                is CredentialException.NotFound -> Response.Status.NOT_FOUND to null
                is CredentialException.UnknownScope ->
                    Response.Status.BAD_REQUEST to mapOf("token" to ex.token)
            }

        val body =
            mapOf(
                "error" to
                    buildMap {
                        put("code", ex.code)
                        put("message", ex.message.orEmpty())
                        if (details != null) put("details", details)
                    },
            )
        return Response.status(status).entity(body).build()
    }
}
