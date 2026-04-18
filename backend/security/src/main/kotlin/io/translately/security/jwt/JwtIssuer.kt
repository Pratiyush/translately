package io.translately.security.jwt

import io.smallrye.jwt.build.Jwt
import io.translately.security.Scope
import io.translately.security.password.TokenGenerator
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.Instant

/**
 * Mints signed JWTs for users who've completed authentication.
 *
 * Access tokens are short-lived (default 15 min) and carry `sub`, `upn`,
 * `scope`, `groups`, `orgs`, `typ=access`. Refresh tokens are longer-lived
 * (default 30 days) with a minimal claim set: `sub`, `jti` (single-use),
 * `exp`, `typ=refresh`. The `/auth/refresh` endpoint (T103) rejects any
 * `jti` it's already consumed.
 */
@ApplicationScoped
open class JwtIssuer(
    @ConfigProperty(name = "translately.jwt.issuer", defaultValue = "translately")
    private val issuer: String,
    @ConfigProperty(name = "translately.jwt.audience", defaultValue = "translately-webapp")
    private val audience: String,
    @ConfigProperty(name = "translately.jwt.access-ttl", defaultValue = "PT15M")
    private val accessTtl: Duration,
    @ConfigProperty(name = "translately.jwt.refresh-ttl", defaultValue = "P30D")
    private val refreshTtl: Duration,
) {
    /**
     * Build a compact-serialized access+refresh pair for [userExternalId].
     *
     * @param userExternalId ULID external ID of the authenticated user.
     * @param email user's verified email (becomes `upn`).
     * @param scopes complete scope set granted to this user.
     * @param orgs zero or more memberships (empty for a fresh signup).
     * @param now injection seam for tests.
     */
    open fun issue(
        userExternalId: String,
        email: String,
        scopes: Set<Scope>,
        orgs: List<JwtOrgMembership> = emptyList(),
        now: Instant = Instant.now(),
    ): JwtTokens {
        val accessExpiresAt = now.plus(accessTtl)
        val refreshExpiresAt = now.plus(refreshTtl)
        val scopeString = Scope.serialize(scopes)
        val scopeGroups = scopes.map { it.token }.toSet()

        val accessToken =
            Jwt
                .issuer(issuer)
                .audience(audience)
                .subject(userExternalId)
                .upn(email)
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .claim(JwtClaims.SCOPE, scopeString)
                .claim(JwtClaims.ORGS, orgs.map { it.toMap() })
                .groups(scopeGroups)
                .issuedAt(now.epochSecond)
                .expiresAt(accessExpiresAt.epochSecond)
                .sign()

        val refreshToken =
            Jwt
                .issuer(issuer)
                .audience(audience)
                .subject(userExternalId)
                .claim("jti", TokenGenerator.generate())
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_REFRESH)
                .issuedAt(now.epochSecond)
                .expiresAt(refreshExpiresAt.epochSecond)
                .sign()

        return JwtTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt,
            refreshExpiresAt = refreshExpiresAt,
        )
    }
}

/**
 * Org-membership entry embedded in an access token's `orgs` claim.
 * Role is serialized as the enum name (UPPERCASE).
 */
data class JwtOrgMembership(
    val id: String,
    val slug: String,
    val role: String,
) {
    fun toMap(): Map<String, String> = mapOf("id" to id, "slug" to slug, "role" to role)
}
