package io.translately.api.projects

import io.quarkus.security.Authenticated
import io.translately.api.orgs.OrgErrorMapper
import io.translately.service.orgs.OrgException
import io.translately.service.projects.CreateProjectRequest
import io.translately.service.projects.ProjectService
import io.translately.service.projects.UpdateProjectRequest
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
 * Project CRUD surface (T119 predecessor). Mounted under the org
 * membership path so tenant scope is implicit in the URL; the service
 * enforces membership via [`OrgService.requireMembership`] on every
 * operation.
 */
@Path("/api/v1/organizations/{orgSlug}/projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "projects", description = "Project lifecycle")
@Authenticated
class ProjectResource {
    @Inject
    lateinit var service: ProjectService

    @Inject
    lateinit var jwt: Instance<JsonWebToken>

    @GET
    @Operation(summary = "List every project in the organization.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Listing returned."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Caller is not a member."),
    )
    fun list(
        @PathParam("orgSlug") orgSlug: String,
    ): Response =
        runFlow {
            val items = service.list(callerId(), orgSlug).map(::toBody)
            Response.ok(ListResponse(items)).build()
        }

    @POST
    @Operation(summary = "Create a project inside the organization.")
    @APIResponses(
        APIResponse(responseCode = "201", description = "Project created."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Caller is not a member."),
        APIResponse(responseCode = "409", description = "Slug already in use."),
    )
    fun create(
        @PathParam("orgSlug") orgSlug: String,
        body: CreateBody?,
    ): Response =
        runFlow {
            val req =
                CreateProjectRequest(
                    name = body?.name.orEmpty(),
                    slug = body?.slug,
                    description = body?.description,
                    baseLanguageTag = body?.baseLanguageTag,
                )
            val summary = service.create(callerId(), orgSlug, req)
            Response.status(Response.Status.CREATED).entity(toBody(summary)).build()
        }

    @GET
    @Path("/{projectSlug}")
    @Operation(summary = "Fetch one project.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Found."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun getOne(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
    ): Response =
        runFlow {
            val summary = service.get(callerId(), orgSlug, projectSlug)
            Response.ok(toBody(summary)).build()
        }

    @PATCH
    @Path("/{projectSlug}")
    @Operation(summary = "Update name or description.")
    @APIResponses(
        APIResponse(responseCode = "200", description = "Updated."),
        APIResponse(responseCode = "400", description = "Validation failed."),
        APIResponse(responseCode = "401", description = "Not authenticated."),
        APIResponse(responseCode = "404", description = "Not found or caller is not a member."),
    )
    fun update(
        @PathParam("orgSlug") orgSlug: String,
        @PathParam("projectSlug") projectSlug: String,
        body: UpdateBody?,
    ): Response =
        runFlow {
            val req =
                UpdateProjectRequest(
                    name = body?.name,
                    description = body?.description,
                )
            val summary = service.update(callerId(), orgSlug, projectSlug, req)
            Response.ok(toBody(summary)).build()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun callerId(): String {
        val token = if (jwt.isUnsatisfied) null else runCatching { jwt.get() }.getOrNull()
        return token?.subject?.takeIf { it.isNotBlank() } ?: throw OrgException.NotMember()
    }

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
        val baseLanguageTag: String?,
    )

    data class UpdateBody(
        val name: String?,
        val description: String?,
    )

    data class ProjectBody(
        val id: String,
        val slug: String,
        val name: String,
        val description: String?,
        val baseLanguageTag: String,
        val createdAt: String,
    )

    data class ListResponse(
        val data: List<ProjectBody>,
    )

    companion object {
        fun toBody(s: io.translately.service.projects.ProjectSummary): ProjectBody =
            ProjectBody(
                id = s.id,
                slug = s.slug,
                name = s.name,
                description = s.description,
                baseLanguageTag = s.baseLanguageTag,
                createdAt = s.createdAt.toString(),
            )
    }
}
