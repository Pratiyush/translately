package io.translately.app.orgs

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.app.credentials.CredentialsTestHelpers
import io.translately.security.Scope
import io.translately.security.jwt.JwtIssuer
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the organization CRUD surface
 * (`/api/v1/organizations`). Each test seeds a fresh user via the
 * existing credentials test helpers and mints a real access JWT so
 * @Authenticated passes.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class OrgResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seedToken(email: String = "orgs-it-${System.nanoTime()}@example.com"): String =
        jwtIssuer
            .issue(
                userExternalId = helpers.seedVerifiedUser(email),
                email = email,
                scopes = setOf(Scope.ORG_READ),
            ).accessToken

    @Test
    fun `create → get → list → rename`() {
        val token = seedToken()
        val slug = "acme-${System.nanoTime().toString().takeLast(8)}"

        val created =
            given()
                .header("Authorization", "Bearer $token")
                .contentType(ContentType.JSON)
                .body("""{"name":"Acme Corp","slug":"$slug"}""")
                .`when`()
                .post("/api/v1/organizations")
                .then()
                .statusCode(201)
                .body("slug", equalTo(slug))
                .body("name", equalTo("Acme Corp"))
                .body("callerRole", equalTo("OWNER"))
                .extract()
        val orgId = created.jsonPath().getString("id")

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations/$slug")
            .then()
            .statusCode(200)
            .body("id", equalTo(orgId))
            .body("callerRole", equalTo("OWNER"))

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations")
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].slug", equalTo(slug))

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Acme International"}""")
            .`when`()
            .patch("/api/v1/organizations/$slug")
            .then()
            .statusCode(200)
            .body("name", equalTo("Acme International"))
    }

    @Test
    fun `create rejects duplicate slug with 409 ORG_SLUG_TAKEN`() {
        val token = seedToken()
        val slug = "dup-${System.nanoTime().toString().takeLast(8)}"
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"first","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(201)

        val token2 = seedToken()
        given()
            .header("Authorization", "Bearer $token2")
            .contentType(ContentType.JSON)
            .body("""{"name":"second","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("ORG_SLUG_TAKEN"))
    }

    @Test
    fun `create rejects empty name with 400 VALIDATION_FAILED`() {
        val token = seedToken()
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"","slug":"whatever"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
            .body("error.details.fields.path", hasItem("body.name"))
    }

    @Test
    fun `get on an unknown org returns 404 NOT_FOUND`() {
        val token = seedToken()
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations/never-exists-anywhere")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }

    @Test
    fun `get as a non-member returns 404 NOT_FOUND (never leaks existence)`() {
        val ownerToken = seedToken()
        val slug = "priv-${System.nanoTime().toString().takeLast(8)}"
        given()
            .header("Authorization", "Bearer $ownerToken")
            .contentType(ContentType.JSON)
            .body("""{"name":"Private Co","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(201)

        val outsiderToken = seedToken()
        given()
            .header("Authorization", "Bearer $outsiderToken")
            .`when`()
            .get("/api/v1/organizations/$slug")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }

    @Test
    fun `unauthenticated request returns 401`() {
        given()
            .`when`()
            .get("/api/v1/organizations")
            .then()
            .statusCode(401)
    }
}
