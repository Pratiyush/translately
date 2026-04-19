package io.translately.app.imports

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
 * End-to-end tests for the i18next JSON importer at
 * `/api/v1/organizations/{orgSlug}/projects/{projectSlug}/imports/json`
 * (T301). Seeds an org + project + translator user via the HTTP surface
 * so the full authn → authz → service pipeline is exercised.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class ImportResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seed(): Seeded {
        val email = "import-it-${System.nanoTime()}@example.com"
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
                            Scope.IMPORTS_WRITE,
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
        return Seeded(token, orgSlug, projectSlug)
    }

    @Test
    fun `flat JSON imports every key, counts created rows`() {
        val seeded = seed()
        val payload = """{"nav.signIn":"Sign in","nav.signOut":"Sign out","footer.copy":"(c) 2026"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "languageTag": "en",
                  "namespaceSlug": "default",
                  "mode": "OVERWRITE",
                  "body": ${quote(payload)}
                }
                """.trimIndent(),
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("created", equalTo(3))
            .body("updated", equalTo(0))
            .body("skipped", equalTo(0))
            .body("failed", equalTo(0))
            .body("errors", hasSize<Any>(0))
    }

    @Test
    fun `nested JSON flattens paths with a dot separator`() {
        val seeded = seed()
        val payload = """{"nav":{"signIn":"Sign in","signOut":"Sign out"}}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "languageTag": "en",
                  "namespaceSlug": "default",
                  "mode": "OVERWRITE",
                  "body": ${quote(payload)}
                }
                """.trimIndent(),
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("total", equalTo(2))
            .body("created", equalTo(2))
    }

    @Test
    fun `conflict mode KEEP preserves existing translations`() {
        val seeded = seed()
        val initial = """{"login.title":"Sign in"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE","body":${quote(initial)}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("created", equalTo(1))

        val second = """{"login.title":"Log in"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"KEEP","body":${quote(second)}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("created", equalTo(0))
            .body("updated", equalTo(0))
            .body("skipped", equalTo(1))
    }

    @Test
    fun `conflict mode OVERWRITE updates every existing row`() {
        val seeded = seed()
        val initial = """{"login.title":"Sign in"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE","body":${quote(initial)}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)

        val second = """{"login.title":"Log in"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE","body":${quote(second)}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("created", equalTo(0))
            .body("updated", equalTo(1))
            .body("skipped", equalTo(0))
    }

    @Test
    fun `invalid ICU lands in errors without rolling back the clean rows`() {
        val seeded = seed()
        // Unclosed '{' in "broken" trips the IcuValidator; "good" passes.
        val payload = """{"good":"Hello, {name}!","broken":"Hello, {name"}"""
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE","body":${quote(payload)}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(200)
            .body("total", equalTo(2))
            .body("created", equalTo(1))
            .body("failed", equalTo(1))
            .body("errors[0].keyName", equalTo("broken"))
            .body("errors[0].code", equalTo("INVALID_ICU_TEMPLATE"))
    }

    @Test
    fun `malformed JSON yields a 400 with VALIDATION_FAILED`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE",""" +
                    """"body":${quote("""{"unterminated""")}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    @Test
    fun `unknown mode yields a 400`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"NONSENSE","body":${quote("""{"x":"y"}""")}}""",
            ).`when`()
            .post(seeded.importPath())
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    /** Escape a string so it can be embedded as a JSON string value. */
    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private data class Seeded(
        val token: String,
        val orgSlug: String,
        val projectSlug: String,
    ) {
        fun importPath(): String = "/api/v1/organizations/$orgSlug/projects/$projectSlug/imports/json"
    }
}
