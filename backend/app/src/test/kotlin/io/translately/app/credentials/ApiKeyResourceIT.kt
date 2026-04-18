package io.translately.app.credentials

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.security.Scope
import io.translately.security.jwt.JwtIssuer
import jakarta.inject.Inject
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test

/**
 * End-to-end `@QuarkusTest` for the API-key HTTP surface (T110).
 *
 * Each test seeds a fresh org + project via [CredentialsTestHelpers] and
 * authenticates with a short-lived access JWT minted directly by
 * [JwtIssuer]. The elevated `api-keys.*` scopes the resource requires are
 * unioned via the `X-Test-Scopes` header (see `TestScopeHeaderFilter`) —
 * real per-org membership-to-scope mapping lands later in the phase.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class ApiKeyResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seedToken(email: String = "apikey-it-${System.nanoTime()}@example.com") =
        jwtIssuer.issue(
            userExternalId = helpers.seedVerifiedUser(email),
            email = email,
            scopes = setOf(Scope.ORG_READ, Scope.PROJECTS_READ),
        ).accessToken

    private fun elevatedHeader(write: Boolean): String =
        (if (write) Scope.API_KEYS_WRITE else Scope.API_KEYS_READ).token

    @Test
    fun `mint → list → revoke happy path`() {
        val token = seedToken()
        val projectId = helpers.seedOrgAndProject("apik-${System.nanoTime()}", "p-${System.nanoTime()}")

        // mint
        val mintResp =
            given()
                .header("Authorization", "Bearer $token")
                .header("X-Test-Scopes", "${elevatedHeader(true)} ${elevatedHeader(false)} keys.read keys.write")
                .contentType(ContentType.JSON)
                .body(
                    """{
                       "name": "CI publisher",
                       "scopes": ["keys.read", "keys.write"]
                     }""".trimIndent(),
                )
                .`when`()
                .post("/api/v1/projects/$projectId/api-keys")
                .then()
                .statusCode(201)
                .body("prefix", startsWith("tr_ak_"))
                .body("secret", startsWith("tr_ak_"))
                .body("scopes", hasItem("keys.write"))
                .body("scopes", hasItem("keys.read"))
                .extract()
        val keyId = mintResp.jsonPath().getString("id")

        // list
        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(false))
            .`when`()
            .get("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].id", equalTo(keyId))
            .body("data[0].revokedAt", equalTo(null))

        // revoke
        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(true))
            .`when`()
            .delete("/api/v1/projects/$projectId/api-keys/$keyId")
            .then()
            .statusCode(204)

        // revoke again — idempotent
        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(true))
            .`when`()
            .delete("/api/v1/projects/$projectId/api-keys/$keyId")
            .then()
            .statusCode(204)

        // list shows revoked_at set
        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(false))
            .`when`()
            .get("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(200)
            .body("data[0].revokedAt", anyOf(org.hamcrest.Matchers.notNullValue()))
    }

    @Test
    fun `mint rejects scope escalation with 403 SCOPE_ESCALATION`() {
        val token = seedToken()
        val projectId = helpers.seedOrgAndProject("esc-${System.nanoTime()}", "p-${System.nanoTime()}")

        // caller holds api-keys.write but asks for AUDIT_READ which isn't in their set
        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(true))
            .contentType(ContentType.JSON)
            .body(
                """{
                   "name": "naughty",
                   "scopes": ["audit.read"]
                 }""".trimIndent(),
            )
            .`when`()
            .post("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("SCOPE_ESCALATION"))
            .body("error.details.missing", hasItem("audit.read"))
    }

    @Test
    fun `mint rejects unknown scope with 400 UNKNOWN_SCOPE`() {
        val token = seedToken()
        val projectId = helpers.seedOrgAndProject("unk-${System.nanoTime()}", "p-${System.nanoTime()}")

        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(true))
            .contentType(ContentType.JSON)
            .body(
                """{
                   "name": "x",
                   "scopes": ["not-a-real-scope"]
                 }""".trimIndent(),
            )
            .`when`()
            .post("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("UNKNOWN_SCOPE"))
            .body("error.details.token", equalTo("not-a-real-scope"))
    }

    @Test
    fun `mint rejects an empty name with 400 VALIDATION_FAILED`() {
        val token = seedToken()
        val projectId = helpers.seedOrgAndProject("val-${System.nanoTime()}", "p-${System.nanoTime()}")

        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", elevatedHeader(true))
            .contentType(ContentType.JSON)
            .body(
                """{
                   "name": "",
                   "scopes": ["keys.read"]
                 }""".trimIndent(),
            )
            .`when`()
            .post("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
            .body("error.details.fields.path", hasItem("body.name"))
    }

    @Test
    fun `mint on unknown project returns 404 NOT_FOUND`() {
        val token = seedToken()

        given()
            .header("Authorization", "Bearer $token")
            .header("X-Test-Scopes", "${elevatedHeader(true)} keys.read")
            .contentType(ContentType.JSON)
            .body(
                """{
                   "name": "orphan",
                   "scopes": ["keys.read"]
                 }""".trimIndent(),
            )
            .`when`()
            .post("/api/v1/projects/00000000000000000000000000/api-keys")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }

    @Test
    fun `request without authentication returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"x","scopes":["keys.read"]}""")
            .`when`()
            .post("/api/v1/projects/anything/api-keys")
            .then()
            .statusCode(401)
    }

    @Test
    fun `list without api-keys_read returns 403 INSUFFICIENT_SCOPE`() {
        val token = seedToken()
        val projectId = helpers.seedOrgAndProject("fb-${System.nanoTime()}", "p-${System.nanoTime()}")

        given()
            .header("Authorization", "Bearer $token")
            // no X-Test-Scopes — JWT only grants org.read + projects.read
            .`when`()
            .get("/api/v1/projects/$projectId/api-keys")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
    }
}
