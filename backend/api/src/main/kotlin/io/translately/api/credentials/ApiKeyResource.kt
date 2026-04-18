package io.translately.api.credentials

import io.quarkus.security.Authenticated
import io.translately.security.RequiresScope
import io.translately.security.Scope
import io.translately.security.SecurityScopes
import io.translately.service.credentials.ApiKeyService
import io.translately.service.credentials.CredentialException
import io.translately.service.credentials.MintApiKeyRequest
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

/**
 * Project-scoped API-key issuance / listing / revocation (T110).
 *
 * Authenticator filter that accepts `Authorization: ApiKey …` on protected
 * endpoints lands separately — this resource is only the management surface.
 *
 * Mounted at `/api/v1/projects/{projectId}/api-keys` so the project external
 * ID is on the path. Each endpoint declares the minimum scope set it needs
 * via `@RequiresScope`; the service layer additionally enforces scope
 * intersection at mint time so a caller can't upgrade their own scopes.
 */
@Path("/api/v1/projects/{projectId}/api-keys")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "api-keys", description = "Project-scoped API key lifecycle")
@Authenticated
class ApiKeyResource {
    @Inject
    lateinit var service: ApiKeyService

    @Inject
    lateinit var securityScopes: SecurityScopes

    // ------------------------------------------------------------------
    // mint
    // ------------------------------------------------------------------

    @POST
    @RequiresScope(Scope.API_KEYS_WRITE)
    @Operation(
        summary = "Mint a new API key. The full secret is returned exactly once.",
        description =
            "Scopes requested on the new key must be a subset of the caller's current scope set; " +
                "otherwise 403 SCOPE_ESCALATION. The returned `secret` is the full token to " +
                "present as `Authorization: ApiKey <secret>` on future requests.",
    )
    @APIResponses(
        APIResponse(responseCode = "201", description = "API key minted; secret in body."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "403", description = "Scope escalation attempt."),
        APIResponse(responseCode = "404", description = "Project not found."),
    )
    fun mint(
        @PathParam("projectId") projectId: String,
        body: MintBody?,
    ): Response =
        runFlow {
            val req =
                MintApiKeyRequest(
                    name = body?.name.orEmpty(),
                    scopes = body?.scopes.orEmpty(),
                    expiresAt = body?.expiresAt,
                )
            val minted = service.mint(projectId, securityScopes.granted, req)
            Response
                .status(Response.Status.CREATED)
                .entity(MintedResponse.from(minted))
                .build()
        }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @GET
    @RequiresScope(Scope.API_KEYS_READ)
    @Operation(summary = "List API keys for the project (no secrets).")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "404", description = "Project not found."),
    )
    fun list(
        @PathParam("projectId") projectId: String,
    ): Response =
        runFlow {
            val items = service.list(projectId).map(SummaryResponse::from)
            Response.ok(ListResponse(items)).build()
        }

    // ------------------------------------------------------------------
    // revoke
    // ------------------------------------------------------------------

    @DELETE
    @Path("/{apiKeyId}")
    @RequiresScope(Scope.API_KEYS_WRITE)
    @Operation(summary = "Revoke an API key. Idempotent.")
    @APIResponses(
        APIResponse(responseCode = "204", description = "Key revoked."),
        APIResponse(responseCode = "404", description = "Project or API key not found."),
    )
    fun revoke(
        @PathParam("projectId") projectId: String,
        @PathParam("apiKeyId") apiKeyId: String,
    ): Response =
        runFlow {
            service.revoke(projectId, apiKeyId)
            Response.noContent().build()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun runFlow(block: () -> Response): Response =
        try {
            block()
        } catch (ex: CredentialException) {
            CredentialErrorMapper.toResponse(ex)
        }

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    data class MintBody(
        val name: String?,
        val scopes: List<String>?,
        val expiresAt: Instant?,
    )

    data class MintedResponse(
        val id: String,
        val prefix: String,
        val secret: String,
        val name: String,
        val scopes: List<String>,
        val expiresAt: Instant?,
        val createdAt: Instant,
    ) {
        companion object {
            fun from(m: io.translately.service.credentials.ApiKeyMinted): MintedResponse =
                MintedResponse(
                    id = m.id,
                    prefix = m.prefix,
                    secret = m.secret,
                    name = m.name,
                    scopes = m.scopes.map { it.token }.sorted(),
                    expiresAt = m.expiresAt,
                    createdAt = m.createdAt,
                )
        }
    }

    data class SummaryResponse(
        val id: String,
        val prefix: String,
        val name: String,
        val scopes: List<String>,
        val expiresAt: Instant?,
        val lastUsedAt: Instant?,
        val revokedAt: Instant?,
        val createdAt: Instant,
    ) {
        companion object {
            fun from(s: io.translately.service.credentials.ApiKeySummary): SummaryResponse =
                SummaryResponse(
                    id = s.id,
                    prefix = s.prefix,
                    name = s.name,
                    scopes = s.scopes.map { it.token }.sorted(),
                    expiresAt = s.expiresAt,
                    lastUsedAt = s.lastUsedAt,
                    revokedAt = s.revokedAt,
                    createdAt = s.createdAt,
                )
        }
    }

    data class ListResponse(
        val data: List<SummaryResponse>,
    )
}
