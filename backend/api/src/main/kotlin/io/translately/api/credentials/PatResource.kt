package io.translately.api.credentials

import io.quarkus.security.Authenticated
import io.translately.security.SecurityScopes
import io.translately.service.credentials.CredentialException
import io.translately.service.credentials.MintPatRequest
import io.translately.service.credentials.PatService
import jakarta.enterprise.inject.Instance
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
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

/**
 * Personal Access Token lifecycle (T110).
 *
 * Owned by the authenticated user — no scope is required beyond a valid
 * access JWT, because every user can manage their own credentials. The
 * user identity comes from the JWT's `sub` claim (the user's ULID).
 *
 * Lives outside the tenant scope — PATs span every project the user
 * belongs to.
 */
@Path("/api/v1/users/me/pats")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "pats", description = "Personal Access Token lifecycle")
@Authenticated
class PatResource {
    @Inject
    lateinit var service: PatService

    @Inject
    lateinit var securityScopes: SecurityScopes

    /**
     * Use [Instance] rather than direct injection so we never fail the
     * request with an unhelpful CDI resolution error when the JWT context
     * isn't fully populated yet (e.g. between the auth filter and the
     * resource method's entry). `@Authenticated` already ensured a JWT
     * was present; we read the subject lazily inside [callerId].
     */
    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    // ------------------------------------------------------------------
    // mint
    // ------------------------------------------------------------------

    @POST
    @Operation(
        summary = "Mint a new PAT. The full secret is returned exactly once.",
        description =
            "Scopes requested on the new PAT must be a subset of the caller's current JWT scope set.",
    )
    @APIResponses(
        APIResponse(responseCode = "201", description = "PAT minted; secret in body."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "403", description = "Scope escalation attempt."),
    )
    fun mint(body: MintBody?): Response =
        runFlow {
            val req =
                MintPatRequest(
                    name = body?.name.orEmpty(),
                    scopes = body?.scopes.orEmpty(),
                    expiresAt = body?.expiresAt,
                )
            val minted = service.mint(callerId(), securityScopes.granted, req)
            Response
                .status(Response.Status.CREATED)
                .entity(MintedResponse.from(minted))
                .build()
        }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @GET
    @Operation(summary = "List PATs owned by the caller (no secrets).")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
    )
    fun list(): Response =
        runFlow {
            val items = service.list(callerId()).map(SummaryResponse::from)
            Response.ok(ListResponse(items)).build()
        }

    // ------------------------------------------------------------------
    // revoke
    // ------------------------------------------------------------------

    @DELETE
    @Path("/{patId}")
    @Operation(summary = "Revoke a PAT owned by the caller. Idempotent.")
    @APIResponses(
        APIResponse(responseCode = "204", description = "PAT revoked."),
        APIResponse(responseCode = "404", description = "PAT not found."),
    )
    fun revoke(
        @PathParam("patId") patId: String,
    ): Response =
        runFlow {
            service.revoke(callerId(), patId)
            Response.noContent().build()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun callerId(): String {
        val token =
            if (jwt.isUnsatisfied) null else runCatching { jwt.get() }.getOrNull()
        return token?.subject?.takeIf { it.isNotBlank() }
            ?: throw CredentialException.NotFound("User")
    }

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
            fun from(m: io.translately.service.credentials.PatMinted): MintedResponse =
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
            fun from(s: io.translately.service.credentials.PatSummary): SummaryResponse =
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
