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
 * End-to-end tests for key CRUD + translation upsert at
 * `/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys` (T208).
 * Seeds a user, org, project, and a namespace via the HTTP surface so
 * the whole flow is exercised.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class KeyResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seed(): Seeded {
        val email = "keys-it-${System.nanoTime()}@example.com"
        val userId = helpers.seedVerifiedUser(email)
        val token =
            jwtIssuer
                .issue(
                    userExternalId = userId,
                    email = email,
                    scopes =
                        setOf(
                            Scope.ORG_READ,
                            Scope.PROJECTS_READ,
                            Scope.PROJECTS_WRITE,
                            Scope.KEYS_READ,
                            Scope.KEYS_WRITE,
                            Scope.TRANSLATIONS_READ,
                            Scope.TRANSLATIONS_WRITE,
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
        val nsSlug = "ns-${System.nanoTime().toString().takeLast(6)}"
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Default","slug":"$nsSlug"}""")
            .`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/namespaces")
            .then()
            .statusCode(201)
        return Seeded(token, orgSlug, projectSlug, nsSlug)
    }

    @Test
    fun `happy path — create, get, list, rename key`() {
        val seeded = seed()
        val keyName = "settings.save.button"

        val created =
            given()
                .header("Authorization", "Bearer ${seeded.token}")
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "keyName": "$keyName",
                      "namespaceSlug": "${seeded.namespaceSlug}",
                      "description": "Save-button label"
                    }
                    """.trimIndent(),
                ).`when`()
                .post(seeded.keysPath())
                .then()
                .statusCode(201)
                .body("keyName", equalTo(keyName))
                .body("namespaceSlug", equalTo(seeded.namespaceSlug))
                .body("state", equalTo("NEW"))
                .extract()
        val id = created.jsonPath().getString("id")

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .`when`()
            .get("${seeded.keysPath()}/$id")
            .then()
            .statusCode(200)
            .body("key.id", equalTo(id))
            .body("key.keyName", equalTo(keyName))
            .body("translations", hasSize<Any>(0))

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .`when`()
            .get(seeded.keysPath())
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].keyName", equalTo(keyName))

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body("""{"keyName":"settings.save.button.v2"}""")
            .`when`()
            .patch("${seeded.keysPath()}/$id")
            .then()
            .statusCode(200)
            .body("keyName", equalTo("settings.save.button.v2"))
    }

    @Test
    fun `create rejects empty keyName with 400 VALIDATION_FAILED`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body("""{"keyName":"","namespaceSlug":"${seeded.namespaceSlug}"}""")
            .`when`()
            .post(seeded.keysPath())
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    @Test
    fun `get rejects unknown key with 404`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .`when`()
            .get("${seeded.keysPath()}/does-not-exist")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }

    @Test
    fun `non-member cannot list keys and sees NOT_FOUND`() {
        val seeded = seed()
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
            .get(seeded.keysPath())
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }

    @Test
    fun `upsert translation flips state to DRAFT then overwrites`() {
        val seeded = seed()
        val created =
            given()
                .header("Authorization", "Bearer ${seeded.token}")
                .contentType(ContentType.JSON)
                .body("""{"keyName":"hello","namespaceSlug":"${seeded.namespaceSlug}"}""")
                .`when`()
                .post(seeded.keysPath())
                .then()
                .statusCode(201)
                .extract()
        val id = created.jsonPath().getString("id")

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body("""{"value":"Hello world"}""")
            .`when`()
            .put("${seeded.keysPath()}/$id/translations/en")
            .then()
            .statusCode(200)
            .body("languageTag", equalTo("en"))
            .body("value", equalTo("Hello world"))
            .body("state", equalTo("DRAFT"))

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body("""{"value":"Hello, world!","state":"TRANSLATED"}""")
            .`when`()
            .put("${seeded.keysPath()}/$id/translations/en")
            .then()
            .statusCode(200)
            .body("value", equalTo("Hello, world!"))
            .body("state", equalTo("TRANSLATED"))

        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .`when`()
            .get("${seeded.keysPath()}/$id")
            .then()
            .statusCode(200)
            .body("translations", hasSize<Any>(1))
            .body("translations[0].languageTag", equalTo("en"))
            .body("translations[0].value", equalTo("Hello, world!"))
    }

    private data class Seeded(
        val token: String,
        val orgSlug: String,
        val projectSlug: String,
        val namespaceSlug: String,
    ) {
        fun keysPath(): String = "/api/v1/organizations/$orgSlug/projects/$projectSlug/keys"
    }
}
