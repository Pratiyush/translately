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
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the i18next JSON exporter at
 * `/api/v1/organizations/{orgSlug}/projects/{projectSlug}/exports/json`
 * (T302). Seeds a project via the HTTP surface, runs the importer to
 * populate translations, then exports them in both shapes.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class ExportResourceIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var jwtIssuer: JwtIssuer

    private fun seed(): Seeded {
        val email = "export-it-${System.nanoTime()}@example.com"
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
                            Scope.EXPORTS_READ,
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
        // Seed three translations via the importer so the exporter has rows to emit.
        val payload = """{"nav.signIn":"Sign in","nav.signOut":"Sign out","footer.copy":"(c) 2026"}"""
        given()
            .header("Authorization", "Bearer $token")
            .contentType(ContentType.JSON)
            .body(
                """{"languageTag":"en","namespaceSlug":"default","mode":"OVERWRITE","body":${quote(payload)}}""",
            ).`when`()
            .post("/api/v1/organizations/$orgSlug/projects/$projectSlug/imports/json")
            .then()
            .statusCode(200)
        return Seeded(token, orgSlug, projectSlug)
    }

    @Test
    fun `exports flat JSON for a language with every translation`() {
        val seeded = seed()
        val body =
            given()
                .header("Authorization", "Bearer ${seeded.token}")
                .queryParam("languageTag", "en")
                .queryParam("shape", "FLAT")
                .`when`()
                .get(seeded.exportPath())
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .header("Content-Disposition", containsString(".json"))
                .header("X-Translately-Key-Count", equalTo("3"))
                .extract()
                .asString()
        assert(body.contains("\"nav.signIn\"")) { "flat export missing nav.signIn: $body" }
        assert(body.contains("\"Sign in\"")) { "flat export missing value: $body" }
    }

    @Test
    fun `exports nested JSON walking dotted keys into the tree`() {
        val seeded = seed()
        val body =
            given()
                .header("Authorization", "Bearer ${seeded.token}")
                .queryParam("languageTag", "en")
                .queryParam("shape", "NESTED")
                .`when`()
                .get(seeded.exportPath())
                .then()
                .statusCode(200)
                .extract()
                .asString()
        assert(body.contains("\"nav\"")) { "nested export missing nav: $body" }
        assert(body.contains("\"signIn\"")) { "nested export missing nested signIn: $body" }
    }

    @Test
    fun `bad shape yields 400 with VALIDATION_FAILED`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .queryParam("languageTag", "en")
            .queryParam("shape", "BOGUS")
            .`when`()
            .get(seeded.exportPath())
            .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_FAILED"))
    }

    @Test
    fun `missing languageTag yields 400`() {
        val seeded = seed()
        given()
            .header("Authorization", "Bearer ${seeded.token}")
            .`when`()
            .get(seeded.exportPath())
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
        fun exportPath(): String = "/api/v1/organizations/$orgSlug/projects/$projectSlug/exports/json"
    }
}
