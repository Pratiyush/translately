package io.translately.api.keys

import io.quarkus.security.Authenticated
import io.translately.api.orgs.OrgErrorMapper
import io.translately.api.orgs.callerIdFrom
import io.translately.service.keys.CreateNamespaceRequest
import io.translately.service.keys.KeyService
import io.translately.service.keys.NamespaceSummary
import io.translately.service.orgs.OrgException
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
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

/**
 * Namespace CRUD for a project (T208). Namespaces group keys inside a
 * project; `(projectId, namespaceId, keyName)` is the uniqueness triple
 * for a [io.translately.data.entity.Key].
 *
 * Rename + delete land in a follow-up — keys-create flow only needs
 * list + create for the first ship.
 */
@Path("/api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "namespaces", description = "Key namespace lifecycle")
@Authenticated
class NamespaceResource {
    @Inject
    lateinit var service: KeyService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "List namespaces in the project.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Caller is not a member."),
    )
    fun list(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
    ): Response =
        runFlow {
            val items = service.listNamespaces(callerIdFrom(jwt), orgSlug, projectSlug).map(::toBody)
            Response.ok(ListResponse(items)).build()
        }

    @POST
    @Operation(summary = "Create a namespace in the project.")
    @APIResponses(
        APIResponse(responseCode = "201", description = "Namespace created."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Project not found or caller is not a member."),
        APIResponse(responseCode = "409", description = "Slug already in use in this project."),
    )
    fun create(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        body: CreateBody?,
    ): Response =
        runFlow {
            val req =
                CreateNamespaceRequest(
                    name = body?.name.orEmpty(),
                    slug = body?.slug,
                    description = body?.description,
                )
            val summary = service.createNamespace(callerIdFrom(jwt), orgSlug, projectSlug, req)
            Response.status(Response.Status.CREATED).entity(toBody(summary)).build()
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
        val description: String?,
    )

    data class NamespaceBody(
        val id: String,
        val slug: String,
        val name: String,
        val description: String?,
    )

    data class ListResponse(
        val data: List<NamespaceBody>,
    )

    companion object {
        fun toBody(s: NamespaceSummary): NamespaceBody =
            NamespaceBody(
                id = s.id,
                slug = s.slug,
                name = s.name,
                description = s.description,
            )
    }
}
