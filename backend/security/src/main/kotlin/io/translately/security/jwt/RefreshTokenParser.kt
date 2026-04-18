package io.translately.security.jwt

import io.smallrye.jwt.auth.principal.JWTAuthContextInfo
import io.smallrye.jwt.auth.principal.JWTParser
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.JsonWebToken

/**
 * Parses and cryptographically validates refresh-JWT payloads.
 *
 * The public [JwtSecurityScopesFilter] refuses refresh tokens as bearer
 * credentials — a separate path is needed for the `POST /auth/refresh`
 * endpoint to turn the presented JWT back into a structured view of its
 * claims. This class handles that narrowly: signature + issuer + audience
 * + expiry are verified, then `sub`, `jti`, `typ` are exposed to the
 * service layer.
 */
@ApplicationScoped
open class RefreshTokenParser(
    private val parser: JWTParser,
    @ConfigProperty(name = "translately.jwt.issuer", defaultValue = "translately")
    private val issuer: String,
    @ConfigProperty(name = "translately.jwt.audience", defaultValue = "translately-webapp")
    private val audience: String,
) {
    /**
     * Validate [rawJwt] against the configured issuer + audience + public
     * key, and return [ParsedRefresh] when the claims shape matches a
     * refresh token. Returns `null` on any validation failure so callers
     * can render a uniform 401 without caring why.
     */
    open fun parse(rawJwt: String): ParsedRefresh? {
        val info =
            JWTAuthContextInfo().apply {
                issuedBy = issuer
                expectedAudience = setOf(audience)
            }
        val jwt: JsonWebToken = runCatching { parser.parse(rawJwt, info) }.getOrNull() ?: return null
        return buildRefresh(jwt)
    }

    private fun buildRefresh(jwt: JsonWebToken): ParsedRefresh? {
        val typ = jwt.getClaim<Any?>(JwtClaims.TYPE)?.toString()
        if (typ != JwtClaims.TYPE_REFRESH) return null
        val sub = jwt.subject?.takeIf { it.isNotBlank() } ?: return null
        val jti = jwt.getClaim<Any?>("jti")?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return ParsedRefresh(subject = sub, jti = jti)
    }
}

/** Minimal projection of a verified refresh JWT that the service layer needs. */
data class ParsedRefresh(
    val subject: String,
    val jti: String,
)
