package io.translately.app.security

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.translately.security.RequiresScope
import io.translately.security.Scope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * Boots Quarkus with a small inline resource that declares `@RequiresScope`
 * at both method and class level, then exercises the
 * [io.translately.api.security.ScopeAuthorizationFilter] end-to-end.
 *
 * Scopes are granted via `X-Test-Scopes` (space-separated tokens) handled by
 * [TestScopeHeaderFilter] — a stand-in for the real JWT / API-key
 * authenticator landing in Phase 1 (T104 / T110).
 */
@QuarkusTest
class ScopeAuthorizationIT {
    // ---- happy path ------------------------------------------------------

    @Test
    fun `unprotected endpoint passes with no scopes granted`() {
        given()
            .`when`()
            .get("/test/scopes/open")
            .then()
            .statusCode(200)
            .body(equalTo("open"))
    }

    @Test
    fun `method-level protected endpoint allows a caller with the required scope`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.read")
            .`when`()
            .get("/test/scopes/keys-read")
            .then()
            .statusCode(200)
            .body(equalTo("keys.read"))
    }

    @Test
    fun `method-level protected endpoint allows over-granted callers`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.read keys.write projects.read")
            .`when`()
            .get("/test/scopes/keys-read")
            .then()
            .statusCode(200)
    }

    @Test
    fun `method requiring multiple scopes passes when all are granted`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.read keys.write")
            .`when`()
            .get("/test/scopes/keys-write")
            .then()
            .statusCode(200)
            .body(equalTo("keys.read+keys.write"))
    }

    @Test
    fun `class-level annotation protects every method on the class`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "audit.read")
            .`when`()
            .get("/test/scopes/audit/a")
            .then()
            .statusCode(200)
            .body(equalTo("a"))
        given()
            .header(TestScopeHeaderFilter.HEADER, "audit.read")
            .`when`()
            .get("/test/scopes/audit/b")
            .then()
            .statusCode(200)
            .body(equalTo("b"))
    }

    @Test
    fun `method-level annotation overrides class-level (wider scope on method wins)`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.write")
            .`when`()
            .get("/test/scopes/audit/override")
            .then()
            .statusCode(200)
            .body(equalTo("override"))
    }

    // ---- denial path -----------------------------------------------------

    @Test
    fun `method-level protected endpoint rejects caller with no scopes`() {
        given()
            .`when`()
            .get("/test/scopes/keys-read")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
            .body("error.details.required", contains("keys.read"))
            .body("error.details.missing", contains("keys.read"))
    }

    @Test
    fun `endpoint requiring multiple scopes reports the missing ones only`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.read")
            .`when`()
            .get("/test/scopes/keys-write")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
            .body("error.details.required", containsInAnyOrder("keys.read", "keys.write"))
            .body("error.details.missing", contains("keys.write"))
    }

    @Test
    fun `class-level rejection fires when the class-wide scope is absent`() {
        given()
            .`when`()
            .get("/test/scopes/audit/a")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
            .body("error.details.required", contains("audit.read"))
    }

    @Test
    fun `unknown tokens in the header are silently dropped`() {
        given()
            .header(TestScopeHeaderFilter.HEADER, "keys.read bogus.token")
            .`when`()
            .get("/test/scopes/keys-read")
            .then()
            .statusCode(200)
    }

    // ---- test resources --------------------------------------------------

    @Path("/test/scopes")
    @ApplicationScoped
    @Suppress("FunctionOnlyReturningConstant")
    class ScopeTestResource {
        @GET
        @Path("/open")
        @Produces(MediaType.TEXT_PLAIN)
        fun open(): String = "open"

        @GET
        @Path("/keys-read")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.KEYS_READ)
        fun keysRead(): String = "keys.read"

        @GET
        @Path("/keys-write")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.KEYS_READ, Scope.KEYS_WRITE)
        fun keysWrite(): String = "keys.read+keys.write"
    }

    @Path("/test/scopes/audit")
    @ApplicationScoped
    @RequiresScope(Scope.AUDIT_READ)
    @Suppress("FunctionOnlyReturningConstant")
    class ClassProtectedResource {
        @GET
        @Path("/a")
        @Produces(MediaType.TEXT_PLAIN)
        fun a(): String = "a"

        @GET
        @Path("/b")
        @Produces(MediaType.TEXT_PLAIN)
        fun b(): String = "b"

        @GET
        @Path("/override")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.KEYS_WRITE)
        fun overrideMethod(): String = "override"
    }
}
