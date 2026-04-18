package io.translately.api.orgs

import io.translately.service.orgs.OrgException
import jakarta.ws.rs.core.Response

/**
 * Maps [OrgException] subclasses to the uniform `{error:{code,message,details?}}`
 * envelope from `.kiro/steering/api-conventions.md`. Called in-band by
 * the org / project / member resources' `runFlow` helpers.
 */
object OrgErrorMapper {
    fun toResponse(ex: OrgException): Response {
        val (status, details) =
            when (ex) {
                is OrgException.ValidationFailed ->
                    Response.Status.BAD_REQUEST to
                        mapOf(
                            "fields" to
                                ex.fields.map { mapOf("path" to it.path, "code" to it.code) },
                        )
                is OrgException.NotFound,
                is OrgException.NotMember,
                -> Response.Status.NOT_FOUND to null
                is OrgException.SlugTaken ->
                    Response.Status.CONFLICT to mapOf("slug" to ex.slug)
                is OrgException.InsufficientScope ->
                    Response.Status.FORBIDDEN to mapOf("required" to listOf(ex.required))
                is OrgException.LastOwner -> Response.Status.CONFLICT to null
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
