package io.translately.security.jwt

/**
 * Canonical claim-name constants used by [JwtIssuer] when minting tokens
 * and by the JWT authenticator when reading them. Kept here so the two
 * sides can't drift.
 *
 * Standard JWT claims (`iss`, `sub`, `iat`, `exp`, `nbf`, `jti`, `aud`)
 * are managed by the smallrye-jwt builder — we only name our custom
 * claims below.
 */
object JwtClaims {
    /** Marker distinguishing access tokens from refresh tokens in the same issuer. */
    const val TYPE = "typ" // values: TYPE_ACCESS or TYPE_REFRESH
    const val TYPE_ACCESS = "access"
    const val TYPE_REFRESH = "refresh"

    /** Space-separated scope tokens, matching the JWT "scope" convention. */
    const val SCOPE = "scope"

    /**
     * JSON array of `{ id: ULID, slug: String, role: OrganizationRole }` objects.
     * Carries the user's org memberships so the webapp can render the org
     * switcher without an extra API call immediately after login.
     */
    const val ORGS = "orgs"
}

/** Envelope-typed fields set on every Translately-issued JWT. */
enum class JwtTokenType(
    val claim: String,
) {
    ACCESS(JwtClaims.TYPE_ACCESS),
    REFRESH(JwtClaims.TYPE_REFRESH),
}
