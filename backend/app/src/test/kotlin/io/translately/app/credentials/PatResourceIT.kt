package io.translately.app.credentials

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.security.Scope
import io.translately.security.jwt.JwtIssuer
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the PAT surface (T110).
 *
 * PATs require only `@Authenticated`; every user manages their own
 * credentials. The scope-intersection rule still applies at mint time:
 * requested PAT scopes must be a subset of the caller's effective scope
 * set, which after the issue #151 fix is driven purely by the access
 * JWT's `scope` claim. Tests mint a JWT with `keys.read` in its scope
 * set and exercise the full `JwtSecurityScopesFilter` → `SecurityScopes`
 * → `PatService` path — the `X-Test-Scopes` workaround previously needed
 * here (see issue #151) is no longer required.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class PatResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    /**
     * JWT subject is the acting user's ULID — stable identity for /users/me.
     *
     * The scope set deliberately carries `keys.read` so the happy-path mint
     * below can request that scope without tripping `SCOPE_ESCALATION`.
     * Org-admin scopes (e.g. `api-keys.write`) are left out so the escalation
     * test can still cover the rejection path with a real JWT.
     */
    private fun seedToken(): Pair<String, String> {
        val email = "pat-it-${System.nanoTime()}@example.com"
        val uid = helpers.seedVerifiedUser(email)
        val token =
            jwtIssuer
                .issue(
                    userExternalId = uid,
                    email = email,
                    scopes = setOf(Scope.ORG_READ, Scope.PROJECTS_READ, Scope.KEYS_READ),
                ).accessToken
        return token to uid
    }

    @Test
    fun `mint → list → revoke happy path`() {
        val (token, _) = seedToken()

        // mint — caller's JWT carries keys.read; PAT requests keys.read.
        val mintResp =
            given()
                .header("Authorization", "Bearer $token")
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "name": "CLI personal",
                      "scopes": ["keys.read"]
                    }
                    """.trimIndent(),
                ).`when`()
                .post("/api/v1/users/me/pats")
                .then()
                .statusCode(201)
                .body("prefix", startsWith("tr_pat_"))
                .body("secret", startsWith("tr_pat_"))
                .body("scopes", hasItem("keys.read"))
                .extract()
        val patId = mintResp.jsonPath().getString("id")

        // list — no scope header required for PAT endpoints (only @Authenticated).
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/users/me/pats")
            .then()
            .statusCode(200)
            .body("data", hasSize<Any>(1))
            .body("data[0].id", equalTo(patId))

        // revoke + idempotent revoke
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .delete("/api/v1/users/me/pats/$patId")
            .then()
            .statusCode(204)
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .delete("/api/v1/users/me/pats/$patId")
            .then()
            .statusCode(204)

        // list now shows revokedAt
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/users/me/pats")
            .then()
            .statusCode(200)
            .body("data[0].revokedAt", notNullValue())
    }

    @Test
    fun `mint rejects scope escalation with 403 SCOPE_ESCALATION`() {
        val (token, _) = seedToken()
        given()
            .header("Authorization", "Bearer $token")
            // Caller's JWT carries keys.read only; asking for api-keys.write must 403.
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "name": "privileged",
                  "scopes": ["api-keys.write"]
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/users/me/pats")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("SCOPE_ESCALATION"))
            .body("error.details.missing", hasItem("api-keys.write"))
    }

    @Test
    fun `mint rejects unknown scope with 400 UNKNOWN_SCOPE`() {
        val (token, _) = seedToken()
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "name": "x",
                  "scopes": ["nope.nope"]
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/users/me/pats")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("UNKNOWN_SCOPE"))
            .body("error.details.token", equalTo("nope.nope"))
    }

    @Test
    fun `request without authentication returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"x","scopes":["keys.read"]}""")
            .`when`()
            .post("/api/v1/users/me/pats")
            .then()
            .statusCode(401)
    }

    @Test
    fun `revoke other user's PAT returns 404 NOT_FOUND`() {
        val (tokenA, _) = seedToken()
        val (tokenB, _) = seedToken()

        // user A mints a PAT
        val patId =
            given()
                .header("Authorization", "Bearer $tokenA")
                .contentType(ContentType.JSON)
                .body("""{"name":"A","scopes":["keys.read"]}""")
                .`when`()
                .post("/api/v1/users/me/pats")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id")

        // user B tries to revoke it → 404 (never reveals ownership)
        given()
            .header("Authorization", "Bearer $tokenB")
            .`when`()
            .delete("/api/v1/users/me/pats/$patId")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
    }
}
