package io.translately.api.security

import io.translately.security.Scope
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * Converts [InsufficientScopeException] into a uniform 403 response body
 * matching our API error contract (see `.kiro/steering/api-conventions.md`).
 *
 * Response shape:
 * ```json
 * {
 *   "error": {
 *     "code": "INSUFFICIENT_SCOPE",
 *     "message": "Missing required scope(s): keys.write",
 *     "details": {
 *       "required": ["keys.read", "keys.write"],
 *       "missing":  ["keys.write"]
 *     }
 *   }
 * }
 * ```
 */
@Provider
class InsufficientScopeExceptionMapper : ExceptionMapper<InsufficientScopeException> {
    override fun toResponse(exception: InsufficientScopeException): Response {
        val required = exception.required.map(Scope::token).sorted()
        val missing = exception.missing.map(Scope::token).sorted()
        val body =
            mapOf(
                "error" to
                    mapOf(
                        "code" to "INSUFFICIENT_SCOPE",
                        "message" to "Missing required scope(s): ${missing.joinToString(", ")}",
                        "details" to
                            mapOf(
                                "required" to required,
                                "missing" to missing,
                            ),
                    ),
            )
        return Response
            .status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build()
    }
}
