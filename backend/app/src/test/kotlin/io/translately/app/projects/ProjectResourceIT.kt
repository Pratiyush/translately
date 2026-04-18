package io.translately.app.projects

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
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for project CRUD at
 * `/api/v1/organizations/{orgSlug}/projects`. Each test seeds a user,
 * creates an org (caller becomes OWNER), and exercises the resource.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class ProjectResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seedTokenAndOrg(): Pair<String, String> {
        val email = "proj-it-${System.nanoTime()}@example.com"
        val token =
            jwtIssuer
                .issue(
                    userExternalId = helpers.seedVerifiedUser(email),
                    email = email,
                    scopes = setOf(Scope.ORG_READ, Scope.PROJECTS_READ),
                ).accessToken
        val orgSlug = "o-${System.nanoTime().toString().takeLast(8)}"
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Org","slug":"$orgSlug"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(201)
        return token to orgSlug
    }

    @Test
    fun `create → get → list → rename`() {
        val (token, orgSlug) = seedTokenAndOrg()
        val projectSlug = "p-${System.nanoTime().toString().takeLast(8)}"

        val created =
            given()
                .header("Authorization", "Bearer $token")
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "name": "Marketing site",
                      "slug": "$projectSlug",
                      "description": "Website copy",
                      "baseLanguageTag": "en"
                    }
                    """.trimIndent(),
                ).`when`()
                .post("/api/v1/organizations/$orgSlug/projects")
                .then()
                .statusCode(201)
                .body("slug", equalTo(projectSlug))
                .body("name", equalTo("Marketing site"))
                .body("baseLanguageTag", equalTo("en"))
                .extract()
        val id = created.jsonPath().getString("id")

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations/$orgSlug/projects/$projectSlug")
            .then()
            .statusCode(200)
            .body("id", equalTo(id))

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations/$orgSlug/projects")
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].slug", equalTo(projectSlug))

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Marketing Site 2.0"}""")
            .`when`()
            .patch("/api/v1/organizations/$orgSlug/projects/$projectSlug")
            .then()
            .statusCode(200)
            .body("name", equalTo("Marketing Site 2.0"))
    }

    @Test
    fun `create rejects duplicate slug within same org with 409 PROJECT_SLUG_TAKEN`() {
        val (token, orgSlug) = seedTokenAndOrg()
        val projectSlug = "dup-${System.nanoTime().toString().takeLast(8)}"

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"first","slug":"$projectSlug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects")
            .then()
            .statusCode(201)

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"second","slug":"$projectSlug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("PROJECT_SLUG_TAKEN"))
    }

    @Test
    fun `non-member cannot list projects`() {
        val (_, orgSlug) = seedTokenAndOrg()
        val email = "outsider-${System.nanoTime()}@example.com"
        val outsider =
            jwtIssuer
                .issue(
                    userExternalId = helpers.seedVerifiedUser(email),
                    email = email,
                    scopes = setOf(Scope.PROJECTS_READ),
                ).accessToken
        given()
            .header("Authorization", "Bearer $outsider")
            .`when`()
            .get("/api/v1/organizations/$orgSlug/projects")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }
}
