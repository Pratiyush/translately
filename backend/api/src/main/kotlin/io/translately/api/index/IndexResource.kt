package io.translately.api.index

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * `GET /` — service metadata, discoverable without auth so operators can probe
 * deployments and humans can land somewhere friendly at the root. All real
 * resources live under `/api/v1/...`.
 */
@Path("/")
@Tag(name = "meta", description = "Service metadata")
class IndexResource {
    @ConfigProperty(name = "quarkus.application.name")
    lateinit var appName: String

    @ConfigProperty(name = "quarkus.application.version")
    lateinit var appVersion: String

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Service metadata", description = "Returns name, version, and well-known endpoint paths.")
    @APIResponse(responseCode = "200", description = "OK")
    fun index(): Index =
        Index(
            name = appName,
            version = appVersion,
            docs = "https://github.com/Pratiyush/translately",
            health = "/q/health",
            openapi = "/q/openapi",
            swagger = "/q/swagger-ui",
        )

    data class Index(
        val name: String,
        val version: String,
        val docs: String,
        val health: String,
        val openapi: String,
        val swagger: String,
    )
}
