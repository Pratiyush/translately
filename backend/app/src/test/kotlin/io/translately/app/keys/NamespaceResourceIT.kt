package io.translately.app.keys

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
 * End-to-end tests for namespace CRUD at
 * `/api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces` (T208).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class NamespaceResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seedTokenOrgAndProject(): Triple<String, String, String> {
        val email = "ns-it-${System.nanoTime()}@example.com"
        val token =
            jwtIssuer
                .issue(
                    userExternalId = helpers.seedVerifiedUser(email),
                    email = email,
                    scopes =
                        setOf(
                            Scope.ORG_READ,
                            Scope.PROJECTS_READ,
                            Scope.PROJECTS_WRITE,
                            Scope.KEYS_READ,
                            Scope.KEYS_WRITE,
                        ),
                ).accessToken
        val orgSlug = "o-${System.nanoTime().toString().takeLast(8)}"
        val projectSlug = "p-${System.nanoTime().toString().takeLast(8)}"
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Org","slug":"$orgSlug"}""")
            .`when`()
            .post("/api/v1/organizations")
            .then()
            .statusCode(201)
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"P","slug":"$projectSlug","baseLanguageTag":"en"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects")
            .then()
            .statusCode(201)
        return Triple(token, orgSlug, projectSlug)
    }

    @Test
    fun `create, list namespaces`() {
        val (token, orgSlug, projectSlug) = seedTokenOrgAndProject()
        val slug = "mobile-${System.nanoTime().toString().takeLast(6)}"

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Mobile","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(201)
            .body("slug", equalTo(slug))
            .body("name", equalTo("Mobile"))

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].slug", equalTo(slug))
    }

    @Test
    fun `create rejects empty name with 400 VALIDATION_FAILED`() {
        val (token, orgSlug, projectSlug) = seedTokenOrgAndProject()
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":""}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    @Test
    fun `create rejects duplicate slug with 409 NAMESPACE_SLUG_TAKEN`() {
        val (token, orgSlug, projectSlug) = seedTokenOrgAndProject()
        val slug = "dup-${System.nanoTime().toString().takeLast(6)}"

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"First","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(201)

        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Second","slug":"$slug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("NAMESPACE_SLUG_TAKEN"))
    }

    @Test
    fun `non-member cannot list namespaces`() {
        val (_, orgSlug, projectSlug) = seedTokenOrgAndProject()
        val email = "outsider-${System.nanoTime()}@example.com"
        val outsider =
            jwtIssuer
                .issue(
                    userExternalId = helpers.seedVerifiedUser(email),
                    email = email,
                    scopes = setOf(Scope.KEYS_READ),
                ).accessToken
        given()
            .header("Authorization", "Bearer $outsider")
            .`when`()
            .get("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }
}
