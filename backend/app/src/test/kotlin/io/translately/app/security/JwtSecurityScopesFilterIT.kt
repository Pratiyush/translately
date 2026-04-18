package io.translately.app.security

import io.quarkus.security.Authenticated
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.translately.security.RequiresScope
import io.translately.security.Scope
import io.translately.security.jwt.JwtIssuer
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #151.
 *
 * Exercises [io.translately.api.security.JwtSecurityScopesFilter] end-to-end
 * by minting a real access JWT via [JwtIssuer] and presenting it against a
 * probe endpoint guarded by `@RequiresScope`. The fix replaces
 * `getClaim<Any?>(...)?.toString()` with a typed `getClaim<String>(...)`
 * read — `JsonString.toString()` returned a quoted JSON literal (e.g.
 * `"access"` including the quote characters), breaking the `typ` check and
 * the `scope` parser. Before the fix these tests were intermittent: any
 * token reaching the filter as a `JsonString` produced a 403 with empty
 * `SecurityScopes.granted` rather than the expected 200.
 *
 * Tests live under `io.translately.app.*` so OpenAPI scanning (restricted
 * to `io.translately.api`) never catalogues the probe resource into the
 * committed `docs/api/openapi.json`.
 */
@QuarkusTest
class JwtSecurityScopesFilterIT {
    @Inject
    lateinit var issuer: JwtIssuer

    @Test
    fun `valid JWT with the required scope reaches a protected endpoint (200)`() {
        val jwt =
            issuer
                .issue(
                    userExternalId = "01HT7F8KXN0GZJYQP3M5JWTSCP1",
                    email = "scopes-filter@example.com",
                    scopes = setOf(Scope.PROJECTS_READ),
                ).accessToken

        given()
            .header("Authorization", "Bearer $jwt")
            .`when`()
            .get("/test/scopes-filter/projects")
            .then()
            .statusCode(200)
            .body(equalTo("projects.read"))
    }

    @Test
    fun `valid JWT without the required scope yields 403 INSUFFICIENT_SCOPE`() {
        val jwt =
            issuer
                .issue(
                    userExternalId = "01HT7F8KXN0GZJYQP3M5JWTSCP2",
                    email = "scopes-filter@example.com",
                    scopes = setOf(Scope.ORG_READ),
                ).accessToken

        given()
            .header("Authorization", "Bearer $jwt")
            .`when`()
            .get("/test/scopes-filter/projects")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
            .body("error.details.missing", contains("projects.read"))
    }

    @Test
    fun `JWT carrying multiple scopes grants every one to SecurityScopes`() {
        // Token carries projects.read AND keys.read — both probes must pass.
        val jwt =
            issuer
                .issue(
                    userExternalId = "01HT7F8KXN0GZJYQP3M5JWTSCP3",
                    email = "scopes-filter@example.com",
                    scopes = setOf(Scope.PROJECTS_READ, Scope.KEYS_READ),
                ).accessToken

        given()
            .header("Authorization", "Bearer $jwt")
            .`when`()
            .get("/test/scopes-filter/projects")
            .then()
            .statusCode(200)
        given()
            .header("Authorization", "Bearer $jwt")
            .`when`()
            .get("/test/scopes-filter/keys")
            .then()
            .statusCode(200)
    }

    @Test
    fun `refresh token presented as a bearer credential is refused (403)`() {
        // Refresh tokens carry typ=refresh; the filter short-circuits and
        // grants nothing, so the downstream scope check 403s.
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5JWTSCP4",
                email = "scopes-filter@example.com",
                scopes = setOf(Scope.PROJECTS_READ),
            )
        given()
            .header("Authorization", "Bearer ${tokens.refreshToken}")
            .`when`()
            .get("/test/scopes-filter/projects")
            .then()
            .statusCode(403)
    }

    // ---- probe resource -----------------------------------------------------
    //
    // Test-only JAX-RS resource in `io.translately.app.*`. OpenAPI scanning
    // is restricted to `io.translately.api` via `mp.openapi.scan.packages`,
    // so this probe never contributes to the committed schema.

    @Path("/test/scopes-filter")
    @ApplicationScoped
    @Suppress("FunctionOnlyReturningConstant")
    class ScopesFilterProbe {
        @GET
        @Path("/open")
        @Produces(MediaType.TEXT_PLAIN)
        @PermitAll
        fun open(): String = "open"

        @GET
        @Path("/projects")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        @RequiresScope(Scope.PROJECTS_READ)
        fun projects(): String = "projects.read"

        @GET
        @Path("/keys")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        @RequiresScope(Scope.KEYS_READ)
        fun keys(): String = "keys.read"
    }
}
