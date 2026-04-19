package io.translately.api.imports

import io.quarkus.security.Authenticated
import io.translately.api.orgs.OrgErrorMapper
import io.translately.api.orgs.callerIdFrom
import io.translately.service.orgs.OrgException
import io.translately.service.translations.JsonTranslationsIO
import io.translately.service.translations.TranslationExportService
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
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
 * i18next JSON export surface (T302). Returns one language's
 * translations as either a flat (`{"nav.signIn":"Sign in"}`) or nested
 * (`{"nav":{"signIn":"Sign in"}}`) JSON object. The response body is
 * the JSON itself — a suggested filename is surfaced via
 * `Content-Disposition` so downloaded files land with a sane default.
 */
@Path("/api/v1/organizations/{orgSlug}/projects/{projectSlug}/exports/json")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "exports", description = "JSON translation export")
@Authenticated
class ExportResource {
    @Inject
    lateinit var service: TranslationExportService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "Export i18next JSON translations for one language tag.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Export generated. Body is the JSON file."),
        APIResponse(
            responseCode = "400",
            description = "Validation failed (missing language tag, bad shape, bad state).",
        ),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Project not found or caller is not a member."),
    )
    @Suppress("LongParameterList") // JAX-RS @QueryParam wiring requires one param per query arg
    fun exportJson(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        @QueryParam("languageTag") languageTag: String?,
        @QueryParam("namespaceSlug") namespaceSlug: String?,
        @QueryParam("tags") tags: String?,
        @QueryParam("minState") minState: String?,
        @QueryParam("shape") @DefaultValue("FLAT") shape: String,
    ): Response =
        runFlow {
            val query =
                ExportQuery(
                    languageTag = languageTag,
                    namespaceSlug = namespaceSlug,
                    tags = tags,
                    minState = minState,
                    shape = shape,
                )
            runExport(orgSlug, projectSlug, query)
        }

    private data class ExportQuery(
        val languageTag: String?,
        val namespaceSlug: String?,
        val tags: String?,
        val minState: String?,
        val shape: String,
    )

    private fun runExport(
        orgSlug: String,
        projectSlug: String,
        q: ExportQuery,
    ): Response {
        val tag =
            q.languageTag?.trim().orEmpty().ifEmpty {
                throw OrgException.ValidationFailed(
                    listOf(OrgException.ValidationFailed.FieldError("languageTag", "REQUIRED")),
                )
            }
        val parsedShape =
            parseShape(q.shape)
                ?: throw OrgException.ValidationFailed(
                    listOf(OrgException.ValidationFailed.FieldError("shape", "INVALID")),
                )
        val tagList =
            q.tags
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
        val req =
            TranslationExportService.ExportJsonRequest(
                languageTag = tag,
                namespaceSlug = q.namespaceSlug,
                tags = tagList,
                minStateName = q.minState,
                shape = parsedShape,
            )
        val target =
            TranslationExportService.ExportTarget(
                orgSlugOrId = orgSlug,
                projectSlugOrId = projectSlug,
            )
        val result = service.exportJson(callerIdFrom(jwt), target, req)
        val filename = "$projectSlug-$tag-${parsedShape.name.lowercase()}.json"
        return Response
            .ok(result.body)
            .type(MediaType.APPLICATION_JSON)
            .header("Content-Disposition", """attachment; filename="$filename"""")
            .header("X-Translately-Key-Count", result.keyCount.toString())
            .build()
    }

    private fun runFlow(block: () -> Response): Response =
        try {
            block()
        } catch (ex: OrgException) {
            OrgErrorMapper.toResponse(ex)
        }

    private fun parseShape(raw: String): JsonTranslationsIO.Shape? =
        when (raw.trim().uppercase()) {
            "FLAT" -> JsonTranslationsIO.Shape.FLAT
            "NESTED" -> JsonTranslationsIO.Shape.NESTED
            else -> null
        }
}
