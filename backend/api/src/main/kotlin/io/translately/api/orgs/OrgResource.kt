package io.translately.api.orgs

import io.quarkus.security.Authenticated
import io.translately.service.orgs.CreateOrgRequest
import io.translately.service.orgs.OrgException
import io.translately.service.orgs.OrgService
import io.translately.service.orgs.UpdateOrgRequest
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
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
 * Organization CRUD surface (T118 predecessor).
 *
 * `@Authenticated` requires a valid JWT; org-level scopes are enforced at
 * the service layer once we have per-org JWT scope loading. For v0.1.0
 * every authenticated caller can list their own orgs and create new ones;
 * updates check membership via the service.
 */
@Path("/api/v1/organizations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "organizations", description = "Organization lifecycle")
@Authenticated
class OrgResource {
    @Inject
    lateinit var service: OrgService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "List organizations the caller is a member of.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
    )
    fun list(): Response =
        runFlow {
            val items = service.listForCaller(callerIdFrom(jwt)).map(::toBody)
            Response.ok(ListResponse(items)).build()
        }

    @POST
    @Operation(summary = "Create a new organization; caller becomes OWNER.")
    @APIResponses(
        APIResponse(responseCode = "201", description = "Organization created."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "409", description = "Slug already in use."),
    )
    fun create(body: CreateBody?): Response =
        runFlow {
            val req =
                CreateOrgRequest(
                    name = body?.name.orEmpty(),
                    slug = body?.slug,
                )
            val summary = service.create(callerIdFrom(jwt), req)
            Response.status(Response.Status.CREATED).entity(toBody(summary)).build()
        }

    @GET
    @Path("/{orgSlug}")
    @Operation(summary = "Fetch one organization the caller belongs to.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Found."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun getOne(
        @PathParam("orgSlug") orgSlug: String,
    ): Response =
        runFlow {
            val summary = service.getForCaller(callerIdFrom(jwt), orgSlug)
            Response.ok(toBody(summary)).build()
        }

    @PATCH
    @Path("/{orgSlug}")
    @Operation(summary = "Rename an organization (name only for v0.1.0).")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Updated."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun update(
        @PathParam("orgSlug") orgSlug: String,
        body: UpdateBody?,
    ): Response =
        runFlow {
            val req = UpdateOrgRequest(name = body?.name.orEmpty())
            val summary = service.updateName(callerIdFrom(jwt), orgSlug, req)
            Response.ok(toBody(summary)).build()
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
        val name: String?,
        val slug: String?,
    )

    data class UpdateBody(
        val name: String?,
    )

    data class OrgBody(
        val id: String,
        val slug: String,
        val name: String,
        val callerRole: String,
        val createdAt: String,
    )

    data class ListResponse(
        val data: List<OrgBody>,
    )

    companion object {
        fun toBody(s: io.translately.service.orgs.OrgSummary): OrgBody =
            OrgBody(
                id = s.id,
                slug = s.slug,
                name = s.name,
                callerRole = s.callerRole,
                createdAt = s.createdAt.toString(),
            )
    }
}
