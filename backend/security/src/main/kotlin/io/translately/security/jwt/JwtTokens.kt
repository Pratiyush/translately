package io.translately.security.jwt

import java.time.Instant

/**
 * Output of a successful authentication: a short-lived access token and a
 * longer-lived refresh token. Both are compact-serialized RS256 JWTs signed
 * by the backend's private key.
 *
 * - [accessToken] carries subject, scopes, and org membership claims; issued
 *   per login and on every refresh. TTL defaults to 15 minutes.
 * - [refreshToken] has a minimal claim set (subject + jti + type=refresh)
 *   and a longer TTL (default 30 days). The JTI is single-use: the
 *   `/auth/refresh` endpoint rejects any refresh token it's already seen.
 * - [accessExpiresAt] is what the webapp stores to schedule silent refresh.
 */
data class JwtTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant,
)
