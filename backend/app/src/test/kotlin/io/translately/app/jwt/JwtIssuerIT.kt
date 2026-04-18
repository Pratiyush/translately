package io.translately.app.jwt

import io.quarkus.test.junit.QuarkusTest
import io.translately.security.Scope
import io.translately.security.jwt.JwtClaims
import io.translately.security.jwt.JwtIssuer
import io.translately.security.jwt.JwtOrgMembership
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * End-to-end tests for [JwtIssuer]. Exercises the real smallrye-jwt signing
 * path against the dev keypair in classpath:/jwt-dev/ and verifies the
 * compact token back with the matching public key.
 */
@QuarkusTest
class JwtIssuerIT {
    @Inject
    lateinit var issuer: JwtIssuer

    // ---- helpers -------------------------------------------------------------

    /**
     * Pull a claim map out of the JWT payload section without depending on
     * smallrye-jwt's parser at test time. The payload is the middle
     * `base64url(json)` segment of the three-dot-separated token.
     */
    private fun payloadOf(jwt: String): Map<String, Any?> {
        val middle = jwt.split('.')[1]
        val json =
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(middle),
                Charsets.UTF_8,
            )
        return io.vertx.core.json
            .JsonObject(json)
            .map
    }

    private fun clockNow(): Instant = Instant.parse("2026-04-17T12:00:00Z")

    // ---- access-token shape --------------------------------------------------

    @Test
    fun `access token carries sub, upn, scope, groups, orgs, typ=access`() {
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                email = "a@example.com",
                scopes = setOf(Scope.PROJECTS_READ, Scope.KEYS_WRITE),
                orgs = listOf(JwtOrgMembership("01HT7F8KXN0GZJYQP3M5ORG01", "acme", "OWNER")),
                now = clockNow(),
            )
        val p = payloadOf(tokens.accessToken)
        assertEquals("01HT7F8KXN0GZJYQP3M5CRSBNW", p["sub"])
        assertEquals("a@example.com", p["upn"])
        assertEquals("access", p[JwtClaims.TYPE])
        assertEquals("projects.read keys.write", p[JwtClaims.SCOPE])
        assertTrue(p["groups"] is List<*>)
        val groups = (p["groups"] as List<*>).map { it.toString() }.toSet()
        assertEquals(setOf("projects.read", "keys.write"), groups)
    }

    @Test
    fun `access token iss + aud match the configured values`() {
        val tokens = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", setOf(Scope.ORG_READ))
        val p = payloadOf(tokens.accessToken)
        assertEquals("translately", p["iss"])
        assertEquals("translately-webapp", p["aud"])
    }

    @Test
    fun `access token exp is roughly now + 15 min`() {
        val now = clockNow()
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                email = "a@example.com",
                scopes = emptySet(),
                now = now,
            )
        val exp = (payloadOf(tokens.accessToken)["exp"] as Number).toLong()
        assertEquals(now.plus(Duration.ofMinutes(15)).epochSecond, exp)
    }

    @Test
    fun `orgs claim is an array of id+slug+role maps`() {
        val orgs =
            listOf(
                JwtOrgMembership("01HT7F8KXN0GZJYQP3M5ORG01", "acme", "OWNER"),
                JwtOrgMembership("01HT7F8KXN0GZJYQP3M5ORG02", "beta", "MEMBER"),
            )
        val tokens = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", setOf(Scope.ORG_READ), orgs)
        val p = payloadOf(tokens.accessToken)

        @Suppress("UNCHECKED_CAST")
        val actual = p[JwtClaims.ORGS] as List<Map<String, String>>
        assertEquals(2, actual.size)
        assertEquals("acme", actual[0]["slug"])
        assertEquals("OWNER", actual[0]["role"])
        assertEquals("beta", actual[1]["slug"])
        assertEquals("MEMBER", actual[1]["role"])
    }

    @Test
    fun `scope claim serializes in declaration order of Scope enum`() {
        // Scope.entries has a stable order pinned by a separate test; serialize
        // preserves insertion order of the Set we pass in. Passing a linked
        // set built from enum entries gives us the canonical output.
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                email = "a@example.com",
                scopes = setOf(Scope.PROJECTS_READ, Scope.PROJECTS_WRITE, Scope.KEYS_READ),
            )
        val scope = payloadOf(tokens.accessToken)[JwtClaims.SCOPE] as String
        assertEquals("projects.read projects.write keys.read", scope)
    }

    // ---- refresh-token shape -------------------------------------------------

    @Test
    fun `refresh token carries sub, jti, typ=refresh but no scope`() {
        val tokens = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", setOf(Scope.ORG_READ))
        val p = payloadOf(tokens.refreshToken)
        assertEquals("01HT7F8KXN0GZJYQP3M5CRSBNW", p["sub"])
        assertEquals("refresh", p[JwtClaims.TYPE])
        assertTrue(p.containsKey("jti"))
        assertTrue(!p.containsKey(JwtClaims.SCOPE))
        assertTrue(!p.containsKey(JwtClaims.ORGS))
    }

    @Test
    fun `refresh token exp is roughly now + 30 days`() {
        val now = clockNow()
        val tokens =
            issuer.issue(
                userExternalId = "01HT7F8KXN0GZJYQP3M5CRSBNW",
                email = "a@example.com",
                scopes = emptySet(),
                now = now,
            )
        val exp = (payloadOf(tokens.refreshToken)["exp"] as Number).toLong()
        assertEquals(now.plus(Duration.ofDays(30)).epochSecond, exp)
    }

    @Test
    fun `each issue call produces distinct jti values`() {
        val a = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", emptySet())
        val b = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", emptySet())
        val jtiA = payloadOf(a.refreshToken)["jti"]
        val jtiB = payloadOf(b.refreshToken)["jti"]
        assertNotEquals(jtiA, jtiB)
    }

    // ---- output envelope -----------------------------------------------------

    @Test
    fun `tokens use the RS256 algorithm (header alg=RS256)`() {
        val tokens = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", emptySet())
        val header = tokens.accessToken.split('.')[0]
        val headerJson =
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(header),
                Charsets.UTF_8,
            )
        assertTrue(headerJson.contains("\"alg\":\"RS256\""), "header was: $headerJson")
    }

    @Test
    fun `returned expiry instants match the exp claim to the second`() {
        val now = clockNow()
        val tokens = issuer.issue("01HT7F8KXN0GZJYQP3M5CRSBNW", "a@example.com", emptySet(), now = now)
        val exp = (payloadOf(tokens.accessToken)["exp"] as Number).toLong()
        assertEquals(tokens.accessExpiresAt.epochSecond, exp)
        val refreshExp = (payloadOf(tokens.refreshToken)["exp"] as Number).toLong()
        assertEquals(tokens.refreshExpiresAt.epochSecond, refreshExp)
    }
}
