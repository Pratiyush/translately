package io.translately.app.auth

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test

/**
 * HTTP-level @QuarkusTest covering every [io.translately.api.auth.AuthResource]
 * endpoint, including the uniform `{error:{code,message,details?}}` body
 * for each failure mode.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class AuthResourceIT {
    @Inject
    lateinit var testHelpers: AuthTestHelpers

    private val mailpitUrl: String
        get() =
            System.getProperty("translately.test.mailpit-url")
                ?: error("translately.test.mailpit-url was not set by PostgresAndMailpitResource")

    private fun uniqueEmail(): String = "res-${System.nanoTime()}@example.com"

    private fun extractToken(
        mailText: String,
        paramName: String,
    ): String {
        val rx = Regex("""[?&]$paramName=([A-Za-z0-9_\-]+)""")
        val match =
            rx.find(mailText) ?: error("No $paramName parameter found in mail body: ${mailText.take(500)}")
        return match.groupValues[1]
    }

    // ------------------------------------------------------------------
    // /signup
    // ------------------------------------------------------------------

    @Test
    fun `signup returns 201 with externalId`() {
        val email = uniqueEmail()
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
            .body("userExternalId", notNullValue())
    }

    @Test
    fun `signup rejects missing email with VALIDATION_FAILED`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
            .body("error.details.fields[0].path", equalTo("email"))
    }

    @Test
    fun `signup rejects a short password with VALIDATION_FAILED`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"${uniqueEmail()}","password":"short","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    @Test
    fun `duplicate signup returns 409 EMAIL_TAKEN`() {
        val email = uniqueEmail()
        val body = """{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}"""
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("EMAIL_TAKEN"))
    }

    // ------------------------------------------------------------------
    // /verify-email + /login
    // ------------------------------------------------------------------

    @Test
    fun `verify-email returns 200 on a fresh token then login succeeds`() {
        val email = uniqueEmail()
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)

        val body = MailpitClient(mailpitUrl).waitForMessage(email)
        val token = extractToken(body, "token")

        given()
            .contentType(ContentType.JSON)
            .body("""{"token":"$token"}""")
            .`when`()
            .post("/api/v1/auth/verify-email")
            .then()
            .statusCode(200)
            .body("status", equalTo("verified"))

        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
    }

    @Test
    fun `login access token opens a scope-protected endpoint end-to-end (issue #151)`() {
        // Full round-trip: signup → verify → login → hit a @RequiresScope
        // endpoint with the real access JWT. Proves `JwtSecurityScopesFilter`
        // reads the `scope` claim correctly and grants it to SecurityScopes.
        // Uses the probe resource declared in JwtAuthenticationIT
        // (`/test/jwt/projects`, requires Scope.PROJECTS_READ) — login issues
        // `ORG_READ + PROJECTS_READ` by default, so this must return 200.
        val email = uniqueEmail()
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
        testHelpers.markVerified(email)

        val accessToken =
            given()
                .contentType(ContentType.JSON)
                .body("""{"email":"$email","password":"correcthorsestaple!"}""")
                .`when`()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("accessToken")

        given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/test/jwt/projects")
            .then()
            .statusCode(200)
            .body(equalTo("ok-projects.read"))
    }

    @Test
    fun `verify-email returns 400 TOKEN_INVALID for garbage`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"token":"garbage-that-never-issued"}""")
            .`when`()
            .post("/api/v1/auth/verify-email")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("TOKEN_INVALID"))
    }

    @Test
    fun `login rejects an unverified account with 403 EMAIL_NOT_VERIFIED`() {
        val email = uniqueEmail()
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)

        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("EMAIL_NOT_VERIFIED"))
    }

    @Test
    fun `login rejects bad credentials with 401 INVALID_CREDENTIALS`() {
        val email = uniqueEmail()
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"correcthorsestaple!","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
        testHelpers.markVerified(email)

        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"wrong-password-entirely"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("INVALID_CREDENTIALS"))
    }

    // ------------------------------------------------------------------
    // /refresh
    // ------------------------------------------------------------------

    @Test
    fun `refresh rotates tokens and replays return REFRESH_TOKEN_REUSED`() {
        val email = uniqueEmail()
        val pass = "correcthorsestaple!"
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"$pass","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
        testHelpers.markVerified(email)

        val first =
            given()
                .contentType(ContentType.JSON)
                .body("""{"email":"$email","password":"$pass"}""")
                .`when`()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
        val refreshToken = first.jsonPath().getString("refreshToken")

        val rotated =
            given()
                .contentType(ContentType.JSON)
                .body("""{"refreshToken":"$refreshToken"}""")
                .`when`()
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .extract()
        val newRefresh = rotated.jsonPath().getString("refreshToken")
        assert(newRefresh != refreshToken) { "refresh token should rotate" }

        given()
            .contentType(ContentType.JSON)
            .body("""{"refreshToken":"$refreshToken"}""")
            .`when`()
            .post("/api/v1/auth/refresh")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("REFRESH_TOKEN_REUSED"))
    }

    @Test
    fun `refresh with malformed JWT returns 400 TOKEN_INVALID`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"refreshToken":"not.a.jwt"}""")
            .`when`()
            .post("/api/v1/auth/refresh")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("TOKEN_INVALID"))
    }

    // ------------------------------------------------------------------
    // /forgot-password + /reset-password
    // ------------------------------------------------------------------

    @Test
    fun `forgot-password always returns 202`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"${uniqueEmail()}"}""")
            .`when`()
            .post("/api/v1/auth/forgot-password")
            .then()
            .statusCode(202)
            .body("status", equalTo("queued"))
    }

    @Test
    fun `reset-password with bad token returns 400 TOKEN_INVALID`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"token":"not-a-real-token","newPassword":"correcthorsestaple!"}""")
            .`when`()
            .post("/api/v1/auth/reset-password")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("TOKEN_INVALID"))
    }

    @Test
    fun `reset-password end-to-end allows login with the new password`() {
        val email = uniqueEmail()
        val oldPass = "correcthorsestaple!"
        val newPass = "rotated-passphrase!2026"
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"$oldPass","fullName":"Jamie"}""")
            .`when`()
            .post("/api/v1/auth/signup")
            .then()
            .statusCode(201)
        // Clear the verification mail so the next one we pull is the reset
        MailpitClient(mailpitUrl).waitForMessage(email)

        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email"}""")
            .`when`()
            .post("/api/v1/auth/forgot-password")
            .then()
            .statusCode(202)
        val body = MailpitClient(mailpitUrl).waitForMessage(email)
        val resetToken = extractToken(body, "token")

        given()
            .contentType(ContentType.JSON)
            .body("""{"token":"$resetToken","newPassword":"$newPass"}""")
            .`when`()
            .post("/api/v1/auth/reset-password")
            .then()
            .statusCode(200)
            .body("status", equalTo("updated"))

        testHelpers.markVerified(email)
        given()
            .contentType(ContentType.JSON)
            .body("""{"email":"$email","password":"$newPass"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
    }
}
