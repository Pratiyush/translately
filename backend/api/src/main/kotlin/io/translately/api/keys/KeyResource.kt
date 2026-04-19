package io.translately.api.keys

import io.quarkus.security.Authenticated
import io.translately.api.orgs.OrgErrorMapper
import io.translately.api.orgs.callerIdFrom
import io.translately.service.keys.CreateKeyRequest
import io.translately.service.keys.KeyDetails
import io.translately.service.keys.KeyService
import io.translately.service.keys.KeySummary
import io.translately.service.keys.ListKeysFilter
import io.translately.service.keys.TranslationSummary
import io.translately.service.keys.TranslationTarget
import io.translately.service.keys.UpdateKeyRequest
import io.translately.service.keys.UpdateTranslationRequest
import io.translately.service.orgs.OrgException
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * Translation-key CRUD surface (T208). Mounted under the project scope
 * so tenant + project checks are implicit in the URL; the service
 * enforces membership on every call.
 */
@Path("/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "keys", description = "Translation-key lifecycle")
@Authenticated
class KeyResource {
    @Inject
    lateinit var service: KeyService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "List keys in the project.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Caller is not a member."),
    )
    fun list(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @QueryParam("namespace") namespace: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
        @QueryParam("offset") @DefaultValue("0") offset: Int,
    ): Response =
        runFlow {
            val items =
                service
                    .list(
                        callerIdFrom(jwt),
                        orgSlug,
                        projectSlug,
                        ListKeysFilter(namespaceSlug = namespace, limit = limit, offset = offset),
                    ).map(::toBody)
            Response.ok(ListResponse(items)).build()
        }

    @POST
    @Operation(summary = "Create a key inside a namespace.")
    @APIResponses(
        APIResponse(responseCode = "201", description = "Key created."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Project / namespace not found or caller is not a member."),
        APIResponse(responseCode = "409", description = "Key name already exists in the namespace."),
    )
    fun create(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        body: CreateBody?,
    ): Response =
        runFlow {
            val req =
                CreateKeyRequest(
                    keyName = body?.keyName.orEmpty(),
                    namespaceSlug = body?.namespaceSlug,
                    description = body?.description,
                )
            val summary = service.create(callerIdFrom(jwt), orgSlug, projectSlug, req)
            Response.status(Response.Status.CREATED).entity(toBody(summary)).build()
        }

    @GET
    @Path("/{keyId}")
    @Operation(summary = "Fetch one key with its translations.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Found."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun getOne(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @PathParam("keyId") keyId: String,
    ): Response =
        runFlow {
            val details = service.get(callerIdFrom(jwt), orgSlug, projectSlug, keyId)
            Response.ok(toDetailsBody(details)).build()
        }

    @PATCH
    @Path("/{keyId}")
    @Operation(summary = "Rename, reclassify, or re-state a key.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Updated."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun update(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @PathParam("keyId") keyId: String,
        body: UpdateBody?,
    ): Response =
        runFlow {
            val req =
                UpdateKeyRequest(
                    keyName = body?.keyName,
                    namespaceSlug = body?.namespaceSlug,
                    description = body?.description,
                    stateName = body?.state,
                )
            val summary = service.update(callerIdFrom(jwt), orgSlug, projectSlug, keyId, req)
            Response.ok(toBody(summary)).build()
        }

    @DELETE
    @Path("/{keyId}")
    @Operation(summary = "Soft-delete a key. Idempotent.")
    @APIResponses(
        APIResponse(responseCode = "204", description = "Deleted."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun delete(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @PathParam("keyId") keyId: String,
    ): Response =
        runFlow {
            service.softDelete(callerIdFrom(jwt), orgSlug, projectSlug, keyId)
            Response.noContent().build()
        }

    @PUT
    @Path("/{keyId}/translations/{languageTag}")
    @Operation(summary = "Upsert one translation cell for a key.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Upserted."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun upsertTranslation(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @PathParam("keyId") keyId: String,
        @PathParam("languageTag") languageTag: String,
        body: TranslationBody?,
    ): Response =
        runFlow {
            val target =
                TranslationTarget(
                    orgSlugOrId = orgSlug,
                    projectSlugOrId = projectSlug,
                    keyIdOrName = keyId,
                    languageTag = languageTag,
                )
            val req =
                UpdateTranslationRequest(
                    value = body?.value.orEmpty(),
                    stateName = body?.state,
                )
            val summary = service.upsertTranslation(callerIdFrom(jwt), target, req)
            Response.ok(toTranslationBody(summary)).build()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun runFlow(block: () -> Response): Response =
        try {
            block()
        } catch (ex: OrgException) {
            OrgErrorMapper.toResponse(ex)
        }

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    data class CreateBody(
        val keyName: String?,
        val namespaceSlug: String?,
        val description: String?,
    )

    data class UpdateBody(
        val keyName: String?,
        val namespaceSlug: String?,
        val description: String?,
        val state: String?,
    )

    data class TranslationBody(
        val value: String?,
        val state: String?,
    )

    data class KeyBody(
        val id: String,
        val keyName: String,
        val namespaceSlug: String,
        val description: String?,
        val state: String,
        val createdAt: String,
        val updatedAt: String,
    )

    data class TranslationCellBody(
        val id: String,
        val languageTag: String,
        val value: String,
        val state: String,
        val updatedAt: String,
    )

    data class KeyDetailsBody(
        val key: KeyBody,
        val translations: List<TranslationCellBody>,
    )

    data class ListResponse(
        val data: List<KeyBody>,
    )

    companion object {
        fun toBody(s: KeySummary): KeyBody =
            KeyBody(
                id = s.id,
                keyName = s.keyName,
                namespaceSlug = s.namespaceSlug,
                description = s.description,
                state = s.stateName,
                createdAt = s.createdAt.toString(),
                updatedAt = s.updatedAt.toString(),
            )

        fun toTranslationBody(s: TranslationSummary): TranslationCellBody =
            TranslationCellBody(
                id = s.id,
                languageTag = s.languageTag,
                value = s.value,
                state = s.stateName,
                updatedAt = s.updatedAt.toString(),
            )

        fun toDetailsBody(d: KeyDetails): KeyDetailsBody =
            KeyDetailsBody(
                key = toBody(d.key),
                translations = d.translations.map(::toTranslationBody),
            )
    }
}
