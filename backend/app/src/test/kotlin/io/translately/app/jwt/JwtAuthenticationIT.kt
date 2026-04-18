package io.translately.app.jwt

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
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * End-to-end: sign a JWT with [JwtIssuer], present it as a bearer token,
 * hit a `@RequiresScope`-protected probe endpoint, assert the full chain
 * (smallrye-jwt validate → `JwtSecurityScopesFilter` populates
 * `SecurityScopes` → `ScopeAuthorizationFilter` enforces) works.
 */
@QuarkusTest
class JwtAuthenticationIT {
    @Inject
    lateinit var issuer: JwtIssuer

    // Note: the full "valid JWT → 200 on a protected endpoint" happy path
    // requires wiring `@Authenticated` + a real auth mechanism onto real
    // resources. That integration lands in T103 (signup/login endpoints)
    // together with @QuarkusTestResource fixtures that seed test users.
    // For T104, we prove the issuer produces well-formed tokens (JwtIssuerIT,
    // 9 payload assertions) and that the filter correctly REJECTS bad tokens
    // (no token → 401; wrong scope → 403; refresh token → 403). The union
    // of those two proofs covers the invariants we care about now.

    @Test
    fun `missing bearer token returns 401 on a protected endpoint`() {
        // @Authenticated on the resource means Quarkus security responds 401
        // (no credentials) before our ScopeAuthorizationFilter runs. That's
        // the correct semantic — missing auth is not a scope problem.
        given()
            .`when`()
            .get("/test/jwt/projects")
            .then()
            .statusCode(401)
    }

    @Test
    fun `token without the required scope is rejected`() {
        val jwt =
            issuer
                .issue(
                    userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                    email = "alice@example.com",
                    scopes = setOf(Scope.KEYS_READ),
                ).accessToken
        given()
            .header("Authorization", "Bearer $jwt")
            .`when`()
            .get("/test/jwt/projects")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
    }

    @Test
    fun `refresh token cannot be used as a bearer access token`() {
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                email = "alice@example.com",
                scopes = setOf(Scope.PROJECTS_READ),
            )
        given()
            .header("Authorization", "Bearer ${tokens.refreshToken}")
            .`when`()
            .get("/test/jwt/projects")
            .then()
            .statusCode(403)
    }

    @Test
    fun `an unprotected endpoint is reachable even without a bearer token`() {
        given()
            .`when`()
            .get("/test/jwt/open")
            .then()
            .statusCode(200)
            .body(equalTo("open"))
    }

    // ---- probe resources ----------------------------------------------------
    //
    // These are test-only JAX-RS resources. OpenAPI scanning is scoped to
    // `io.translately.api` in `application.yml`, so they never contribute
    // to the committed `docs/api/openapi.json`.

    @Path("/test/jwt")
    @ApplicationScoped
    @Suppress("FunctionOnlyReturningConstant")
    class JwtTestResource {
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
        fun projects(): String = "ok-projects.read"
    }
}
