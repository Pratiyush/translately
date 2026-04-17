package io.translately.app.tenant

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.translately.api.tenant.TenantRequestFilter
import io.translately.security.tenant.TenantContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * End-to-end test for [TenantRequestFilter] via a small probe resource that
 * echoes back the current [TenantContext.current] value. Hits it through
 * tenant-less, ULID-shaped, and slug-shaped paths.
 */
@QuarkusTest
class TenantRequestFilterIT {
    // ---- path extraction (no HTTP) ---------------------------------------

    @Test
    fun `extractTenant returns the ULID segment from a canonical API path`() {
        TenantRequestFilter.extractTenant(
            "/api/v1/organizations/01HT7F8KXN0GZJYQP3M5CRSBNW/projects/foo",
        ) shouldBeEqual "01HT7F8KXN0GZJYQP3M5CRSBNW"
    }

    @Test
    fun `extractTenant returns a kebab-case slug from the canonical API path`() {
        TenantRequestFilter.extractTenant(
            "/api/v1/organizations/acme-corp/projects/foo",
        ) shouldBeEqual "acme-corp"
    }

    @Test
    fun `extractTenant tolerates the leading slash being absent`() {
        TenantRequestFilter.extractTenant(
            "api/v1/organizations/acme/projects",
        ) shouldBeEqual "acme"
    }

    @Test
    fun `extractTenant returns null when the path has no organizations segment`() {
        TenantRequestFilter.extractTenant("/api/v1/keys/abc") shouldBeEqual null
        TenantRequestFilter.extractTenant("/q/health") shouldBeEqual null
        TenantRequestFilter.extractTenant("/") shouldBeEqual null
    }

    @Test
    fun `extractTenant rejects identifiers with invalid characters`() {
        TenantRequestFilter.extractTenant("/api/v1/organizations/ACME/projects") shouldBeEqual null
        TenantRequestFilter.extractTenant("/api/v1/organizations/has spaces/projects") shouldBeEqual null
        TenantRequestFilter.extractTenant("/api/v1/organizations/-leading-dash/projects") shouldBeEqual null
    }

    // ---- end-to-end through the filter -----------------------------------

    @Test
    fun `unprotected path leaves the tenant unbound`() {
        given()
            .`when`()
            .get("/test/tenant/current")
            .then()
            .statusCode(200)
            .body(equalTo(""))
    }

    @Test
    fun `organization path populates the tenant context`() {
        given()
            .`when`()
            .get("/api/v1/organizations/acme/projects/foo/probe")
            .then()
            .statusCode(200)
            .body(equalTo("acme"))
    }

    @Test
    fun `ULID-shaped organization path populates the tenant context`() {
        given()
            .`when`()
            .get("/api/v1/organizations/01HT7F8KXN0GZJYQP3M5CRSBNW/projects/foo/probe")
            .then()
            .statusCode(200)
            .body(equalTo("01HT7F8KXN0GZJYQP3M5CRSBNW"))
    }

    // ---- probe resources -------------------------------------------------

    @Path("/test/tenant")
    @ApplicationScoped
    class TenantProbeNoPath {
        @Inject
        lateinit var ctx: TenantContext

        @GET
        @Path("/current")
        @Produces(MediaType.TEXT_PLAIN)
        fun current(): String = ctx.current().orEmpty()
    }

    @Path("/api/v1/organizations/{orgId}/projects/{projectId}")
    @ApplicationScoped
    class TenantProbeWithPath {
        @Inject
        lateinit var ctx: TenantContext

        @GET
        @Path("/probe")
        @Produces(MediaType.TEXT_PLAIN)
        @Suppress("UnusedParameter")
        fun probe(
            @PathParam("orgId") orgId: String,
            @PathParam("projectId") projectId: String,
        ): String = ctx.current().orEmpty()
    }
}

private infix fun <T> T.shouldBeEqual(expected: T) {
    org.junit.jupiter.api.Assertions
        .assertEquals(expected, this)
}
