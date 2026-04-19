package io.translately.api.imports

import io.quarkus.security.Authenticated
import io.translately.api.orgs.OrgErrorMapper
import io.translately.api.orgs.callerIdFrom
import io.translately.service.orgs.OrgException
import io.translately.service.translations.TranslationImportService
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * i18next JSON import surface (T301). One synchronous endpoint that
 * runs the full parse → validate → conflict-resolve → persist pipeline
 * inside a single transaction. Partial failures roll back the whole
 * call so the UI's "preview then commit" flow (T304) never observes a
 * half-applied import.
 *
 * Async Quartz + SSE progress streaming is tracked as T303 (moved to
 * Phase 4). MVP-scope for v0.3.0 is sync only.
 */
@Path("/api/v1/organizations/{orgSlug}/projects/{projectSlug}/imports/json")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "imports", description = "JSON translation import")
@Authenticated
class ImportResource {
    @Inject
    lateinit var service: TranslationImportService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @POST
    @Operation(summary = "Import i18next flat/nested JSON translations into a project for one language.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Import finished. Summary in body."),
        APIResponse(
            responseCode = "400",
            description = "Validation failed — bad JSON, unsupported type, missing language tag.",
        ),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Project / language not found or caller is not a member."),
    )
    fun importJson(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        body: ImportBody?,
    ): Response =
        runFlow {
            val mode =
                parseMode(body?.mode)
                    ?: throw OrgException.ValidationFailed(
                        listOf(OrgException.ValidationFailed.FieldError(path = "mode", code = "INVALID")),
                    )
            val payload = body?.body
            if (payload.isNullOrBlank()) {
                throw OrgException.ValidationFailed(
                    listOf(OrgException.ValidationFailed.FieldError(path = "body", code = "REQUIRED")),
                )
            }
            val request =
                TranslationImportService.ImportJsonRequest(
                    languageTag = body.languageTag.orEmpty(),
                    namespaceSlug = body.namespaceSlug,
                    mode = mode,
                    body = payload,
                )
            val target =
                TranslationImportService.ImportTarget(
                    orgSlugOrId = orgSlug,
                    projectSlugOrId = projectSlug,
                )
            val result = service.importJson(callerIdFrom(jwt), target, request)
            Response.ok(toResponseBody(result)).build()
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

    private fun parseMode(raw: String?): TranslationImportService.ConflictMode? {
        val value = raw?.trim()?.uppercase().orEmpty()
        return when (value) {
            "KEEP" -> TranslationImportService.ConflictMode.KEEP
            "OVERWRITE" -> TranslationImportService.ConflictMode.OVERWRITE
            "MERGE" -> TranslationImportService.ConflictMode.MERGE
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    data class ImportBody(
        val languageTag: String?,
        val namespaceSlug: String?,
        /** `KEEP` / `OVERWRITE` / `MERGE` — case-insensitive. */
        val mode: String?,
        /**
         * The i18next JSON payload as a JSON string. The webapp sends
         * the raw file contents here; we parse + shape-detect inside
         * the service layer.
         */
        val body: String?,
    )

    data class ImportErrorBody(
        val keyName: String,
        val code: String,
        val message: String,
    )

    data class ImportResultBody(
        val total: Int,
        val created: Int,
        val updated: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<ImportErrorBody>,
    )

    companion object {
        fun toResponseBody(r: TranslationImportService.ImportResult): ImportResultBody =
            ImportResultBody(
                total = r.total,
                created = r.created,
                updated = r.updated,
                skipped = r.skipped,
                failed = r.failed,
                errors = r.errors.map { ImportErrorBody(it.keyName, it.code, it.message) },
            )
    }
}
