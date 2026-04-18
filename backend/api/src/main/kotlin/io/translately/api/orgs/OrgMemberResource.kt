package io.translately.api.orgs

import io.quarkus.security.Authenticated
import io.translately.service.orgs.OrgException
import io.translately.service.orgs.OrgMemberService
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
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
 * Organization member management (T119 predecessor).
 *
 * Invite-by-email is explicitly out of scope for v0.1.0 — it needs the
 * email-verification token flow plus the "pending membership" lifecycle
 * and lands alongside SSO / SAML in Phase 7. The resource today supports:
 *
 *   GET    /members               — list (any member)
 *   PATCH  /members/{userId}      — change role (caller must be OWNER/ADMIN)
 *   DELETE /members/{userId}      — remove (caller must be OWNER/ADMIN)
 *
 * The last-OWNER guard in the service returns `LAST_OWNER` (409) so the
 * UI can tell the admin to promote a co-owner before removing themselves.
 */
@Path("/api/v1/organizations/{orgSlug}/members")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "members", description = "Organization membership")
@Authenticated
class OrgMemberResource {
    @Inject
    lateinit var service: OrgMemberService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "List members of the organization.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Caller is not a member of this org."),
    )
    fun list(
        @PathParam("orgSlug") orgSlug: String,
    ): Response =
        runFlow {
            val items = service.list(callerIdFrom(jwt), orgSlug).map(::toBody)
            Response.ok(ListResponse(items)).build()
        }

    @PATCH
    @Path("/{userId}")
    @Operation(summary = "Change a member's role.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Role updated."),
        APIResponse(responseCode = "400", description = "Invalid role."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Member not found."),
        APIResponse(responseCode = "409", description = "Would leave the org with no OWNER."),
    )
    fun updateRole(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("userId") userId: String,
        body: UpdateBody?,
    ): Response =
        runFlow {
            // Role-string parsing + VALIDATION_FAILED on unknown values lives
            // in the service so the api layer doesn't need to depend on
            // :backend:data's OrganizationRole enum.
            val summary = service.updateRole(callerIdFrom(jwt), orgSlug, userId, body?.role.orEmpty())
            Response.ok(toBody(summary)).build()
        }

    @DELETE
    @Path("/{userId}")
    @Operation(summary = "Remove a member from the organization.")
    @APIResponses(
        APIResponse(responseCode = "204", description = "Removed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Member not found."),
        APIResponse(responseCode = "409", description = "Would leave the org with no OWNER."),
    )
    fun remove(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("userId") userId: String,
    ): Response =
        runFlow {
            service.remove(callerIdFrom(jwt), orgSlug, userId)
            Response.noContent().build()
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

    data class UpdateBody(
        val role: String?,
    )

    data class MemberBody(
        val userId: String,
        val email: String,
        val fullName: String,
        val role: String,
        val invitedAt: String,
        val joinedAt: String?,
    )

    data class ListResponse(
        val data: List<MemberBody>,
    )

    companion object {
        fun toBody(s: io.translately.service.orgs.MemberSummary): MemberBody =
            MemberBody(
                userId = s.userId,
                email = s.email,
                fullName = s.fullName,
                role = s.role,
                invitedAt = s.invitedAt.toString(),
                joinedAt = s.joinedAt?.toString(),
            )
    }
}
