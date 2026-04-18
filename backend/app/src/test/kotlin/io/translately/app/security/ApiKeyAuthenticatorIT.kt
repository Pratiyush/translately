package io.translately.app.security

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.app.credentials.CredentialsTestHelpers
import io.translately.security.RequiresScope
import io.translately.security.Scope
import io.translately.service.credentials.ApiKeyService
import io.translately.service.credentials.MintApiKeyRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * End-to-end: mint a real API key through [ApiKeyService], present it as
 * `Authorization: ApiKey <prefix>.<secret>` against a probe endpoint that
 * only requires a scope (no `@Authenticated`), and assert the full chain
 * (ApiKeyAuthenticator → SecurityScopes populate → ScopeAuthorizationFilter
 * enforce) works.
 *
 * The probe resource lives in this file so OpenAPI scanning (scoped to
 * `io.translately.api` in `application.yml`) never picks it up.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class ApiKeyAuthenticatorIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var apiKeyService: ApiKeyService

    private fun mintKey(
        projectId: String,
        scopes: Set<Scope> = setOf(Scope.PROJECTS_READ, Scope.KEYS_READ),
    ): String {
        val minted =
            apiKeyService.mint(
                projectExternalId = projectId,
                callerScopes = setOf(Scope.PROJECTS_READ, Scope.KEYS_READ, Scope.KEYS_WRITE),
                body =
                    MintApiKeyRequest(
                        name = "test key",
                        scopes = scopes.map { it.token },
                    ),
            )
        return minted.secret
    }

    @Test
    fun `valid ApiKey grants its scopes to a protected probe`() {
        val projectId = helpers.seedOrgAndProject("apik-auth-${System.nanoTime()}", "p-${System.nanoTime()}")
        val token = mintKey(projectId)

        given()
            .header("Authorization", "ApiKey $token")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(200)
            .body(equalTo("ok"))
    }

    @Test
    fun `a key lacking the required scope is denied with 403`() {
        val projectId = helpers.seedOrgAndProject("apik-noscope-${System.nanoTime()}", "p-${System.nanoTime()}")
        // key only has projects.read, probe requires keys.write
        val token = mintKey(projectId, setOf(Scope.PROJECTS_READ))

        given()
            .header("Authorization", "ApiKey $token")
            .`when`()
            .get("/test/apikey/keys-write")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
    }

    @Test
    fun `revoked api key returns 401 CREDENTIAL_REVOKED`() {
        val projectId = helpers.seedOrgAndProject("apik-rev-${System.nanoTime()}", "p-${System.nanoTime()}")
        val token = mintKey(projectId)
        val prefix = token.substringBefore(".")
        helpers.revokeApiKey(prefix)

        given()
            .header("Authorization", "ApiKey $token")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("CREDENTIAL_REVOKED"))
    }

    @Test
    fun `expired api key returns 401 CREDENTIAL_EXPIRED`() {
        val projectId = helpers.seedOrgAndProject("apik-exp-${System.nanoTime()}", "p-${System.nanoTime()}")
        val token = mintKey(projectId)
        val prefix = token.substringBefore(".")
        helpers.expireApiKey(prefix)

        given()
            .header("Authorization", "ApiKey $token")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("CREDENTIAL_EXPIRED"))
    }

    @Test
    fun `wrong secret on a valid prefix returns 401 UNAUTHENTICATED`() {
        val projectId = helpers.seedOrgAndProject("apik-bad-${System.nanoTime()}", "p-${System.nanoTime()}")
        val token = mintKey(projectId)
        val prefix = token.substringBefore(".")
        // Replace the 43-char secret with a same-shape-but-wrong string.
        val forged = "$prefix.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        given()
            .header("Authorization", "ApiKey $forged")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHENTICATED"))
    }

    @Test
    fun `unknown prefix returns 401 UNAUTHENTICATED`() {
        given()
            .header("Authorization", "ApiKey tr_ak_unknown1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHENTICATED"))
    }

    @Test
    fun `malformed token missing separator returns 401 UNAUTHENTICATED`() {
        given()
            .header("Authorization", "ApiKey tr_ak_noseparator")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHENTICATED"))
    }

    @Test
    fun `missing Authorization header falls through to 403 (no JWT, no ApiKey)`() {
        // No header at all: ApiKeyAuthenticator doesn't abort, neither does
        // JwtSecurityScopesFilter, so we land at ScopeAuthorizationFilter
        // with an empty scope set and get a 403 — not a 401. That's the
        // contract: scope-protected endpoints answer 403 for missing
        // scopes regardless of auth status, unless the resource also carries
        // `@Authenticated` (these probes don't).
        given()
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
    }

    @Test
    fun `Basic auth scheme is ignored by the ApiKey authenticator`() {
        // Proves ApiKeyAuthenticator bails out on any non-`ApiKey` scheme
        // — here the header uses `Basic`, which nobody owns, so no filter
        // grants anything and we land at the scope filter with an empty
        // set → 403 INSUFFICIENT_SCOPE. Critical side of the proof: the
        // error code must be `INSUFFICIENT_SCOPE`, not one of this
        // filter's `UNAUTHENTICATED` / `CREDENTIAL_*` codes — that would
        // mean we mistakenly claimed a credential shape we don't own.
        given()
            .header("Authorization", "Basic dXNlcjpwYXNz")
            .`when`()
            .get("/test/apikey/projects-read")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))
    }

    // ---- probe resources -------------------------------------------------

    @Path("/test/apikey")
    @ApplicationScoped
    @Suppress("FunctionOnlyReturningConstant")
    class ApiKeyProbeResource {
        @GET
        @Path("/projects-read")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.PROJECTS_READ)
        fun projectsRead(): String = "ok"

        @GET
        @Path("/keys-write")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.KEYS_WRITE)
        fun keysWrite(): String = "ok"
    }
}
