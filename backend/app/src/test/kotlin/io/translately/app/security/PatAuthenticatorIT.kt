package io.translately.app.security

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.app.credentials.CredentialsTestHelpers
import io.translately.data.entity.OrganizationRole
import io.translately.security.RequiresScope
import io.translately.security.Scope
import io.translately.service.credentials.MintPatRequest
import io.translately.service.credentials.PatService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * End-to-end for [io.translately.api.security.PatAuthenticator].
 *
 * PAT scopes are intersected with the owning user's effective scopes at
 * request time. The heavy test here covers that reduction: mint a PAT
 * with `API_KEYS_WRITE` (an ADMIN-only scope), attach the user as a
 * mere MEMBER, and assert the probe refuses the admin endpoint while
 * accepting a MEMBER-level one.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class PatAuthenticatorIT {
    @Inject
    lateinit var helpers: CredentialsTestHelpers

    @Inject
    lateinit var patService: PatService

    /** Mint a PAT and stamp the caller as having every scope the PAT asks for. */
    private fun mintPat(
        userExternalId: String,
        scopes: Set<Scope>,
    ): String {
        val minted =
            patService.mint(
                userExternalId = userExternalId,
                callerScopes = Scope.entries.toSet(),
                body =
                    MintPatRequest(
                        name = "test pat",
                        scopes = scopes.map { it.token },
                    ),
            )
        return minted.secret
    }

    @Test
    fun `valid PAT grants its scopes to a protected probe`() {
        val email = "pat-auth-${System.nanoTime()}@example.com"
        val seeded = helpers.seedOrgProjectAndOwner("pat-auth-${System.nanoTime()}", "p", email)
        val token = mintPat(seeded.userExternalId, setOf(Scope.PROJECTS_READ, Scope.KEYS_READ))

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(200)
            .body(equalTo("ok"))
    }

    @Test
    fun `revoked PAT returns 401 CREDENTIAL_REVOKED`() {
        val email = "pat-rev-${System.nanoTime()}@example.com"
        val seeded = helpers.seedOrgProjectAndOwner("pat-rev-${System.nanoTime()}", "p", email)
        val token = mintPat(seeded.userExternalId, setOf(Scope.PROJECTS_READ))
        val prefix = token.substringBefore(".")
        helpers.revokePat(prefix)

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("CREDENTIAL_REVOKED"))
    }

    @Test
    fun `expired PAT returns 401 CREDENTIAL_EXPIRED`() {
        val email = "pat-exp-${System.nanoTime()}@example.com"
        val seeded = helpers.seedOrgProjectAndOwner("pat-exp-${System.nanoTime()}", "p", email)
        val token = mintPat(seeded.userExternalId, setOf(Scope.PROJECTS_READ))
        val prefix = token.substringBefore(".")
        helpers.expirePat(prefix)

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("CREDENTIAL_EXPIRED"))
    }

    @Test
    fun `wrong secret on a valid prefix returns 401 UNAUTHENTICATED`() {
        val email = "pat-bad-${System.nanoTime()}@example.com"
        val seeded = helpers.seedOrgProjectAndOwner("pat-bad-${System.nanoTime()}", "p", email)
        val token = mintPat(seeded.userExternalId, setOf(Scope.PROJECTS_READ))
        val prefix = token.substringBefore(".")
        val forged = "$prefix.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        given()
            .header("Authorization", "Bearer $forged")
            .`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHENTICATED"))
    }

    @Test
    fun `unknown PAT prefix returns 401 UNAUTHENTICATED`() {
        given()
            .header(
                "Authorization",
                "Bearer tr_pat_unknown1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            ).`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHENTICATED"))
    }

    @Test
    fun `cross-org scope intersection — MEMBER cannot exercise an ADMIN-granted PAT scope`() {
        // Seed a user, make them MEMBER of an org (not OWNER, not ADMIN).
        val email = "pat-mem-${System.nanoTime()}@example.com"
        val userId = helpers.seedVerifiedUser(email)
        helpers.seedOrgWithMember("pat-member-${System.nanoTime()}", userId, OrganizationRole.MEMBER)

        // Mint a PAT that nominally carries API_KEYS_WRITE — an ADMIN scope.
        // `mintPat` passes a maximal callerScopes so the mint itself succeeds;
        // the authenticator must still intersect down at request time.
        val token = mintPat(userId, setOf(Scope.API_KEYS_WRITE, Scope.KEYS_READ))

        // api-keys.write is NOT in MEMBER's effective scope set → 403.
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/api-keys-write")
            .then()
            .statusCode(403)
            .body("error.code", equalTo("INSUFFICIENT_SCOPE"))

        // keys.read IS in MEMBER's effective scope set → 200.
        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/keys-read")
            .then()
            .statusCode(200)
    }

    @Test
    fun `PAT for a user with no memberships yields empty effective scopes`() {
        val email = "pat-orphan-${System.nanoTime()}@example.com"
        val userId = helpers.seedVerifiedUser(email)
        // no org memberships at all
        val token = mintPat(userId, setOf(Scope.PROJECTS_READ, Scope.KEYS_READ))

        given()
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/test/pat/projects-read")
            .then()
            .statusCode(403)
    }

    @Test
    fun `Bearer header without tr_pat_ prefix is left to the JWT authenticator`() {
        // Any `Bearer <x>` that doesn't start with `tr_pat_` is the JWT
        // filter's problem. smallrye-jwt will 401 an unparseable token
        // (no content-type body, hence the `asString()` extraction). The
        // critical proof: the response body must NOT contain any of
        // our PAT-specific error codes — that would mean the PAT
        // authenticator accidentally claimed a JWT-shaped request.
        val body =
            given()
                .header("Authorization", "Bearer some.random.value")
                .`when`()
                .get("/test/pat/projects-read")
                .then()
                .extract()
                .asString()
        assert(!body.contains("CREDENTIAL_REVOKED") && !body.contains("CREDENTIAL_EXPIRED")) {
            "PatAuthenticator leaked a credential-specific error code into the response: $body"
        }
    }

    // ---- probe resources -------------------------------------------------

    @Path("/test/pat")
    @ApplicationScoped
    @Suppress("FunctionOnlyReturningConstant")
    class PatProbeResource {
        @GET
        @Path("/projects-read")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.PROJECTS_READ)
        fun projectsRead(): String = "ok"

        @GET
        @Path("/keys-read")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.KEYS_READ)
        fun keysRead(): String = "ok"

        @GET
        @Path("/api-keys-write")
        @Produces(MediaType.TEXT_PLAIN)
        @RequiresScope(Scope.API_KEYS_WRITE)
        fun apiKeysWrite(): String = "ok"
    }
}
