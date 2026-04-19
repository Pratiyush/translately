package io.translately.api.security

import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.TokenAuthenticationRequest
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import java.security.Principal

/**
 * Coexistence shim for API-key and PAT bearer credentials.
 *
 * ### Why this exists
 *
 * Quarkus's `smallrye-jwt` `JWTAuthMechanism` grabs every `Authorization:
 * Bearer <x>` header and feeds the token to the JWT parser. With
 * `quarkus.http.auth.proactive=true` (our default — required so
 * `JsonWebToken` is populated for [JwtSecurityScopesFilter]) a token it
 * cannot parse becomes a hard 401 at the HTTP layer, *before* any JAX-RS
 * container-request filter gets to run. That short-circuits
 * [ApiKeyAuthenticator] for `ApiKey <token>` headers and
 * [PatAuthenticator] for `Bearer tr_pat_<token>` headers.
 *
 * This mechanism runs at a higher priority than `JWTAuthMechanism` and
 * returns a deliberately-empty authenticated [SecurityIdentity] for
 * those two header shapes — enough to satisfy `quarkus.security`'s
 * "something authenticated this request" check so proactive auth
 * doesn't 401, and no more than that. The real scope-bearing
 * verification happens downstream in the matching JAX-RS filter where
 * it can share [io.translately.security.SecurityScopes] with the JWT
 * path.
 *
 * For every other header shape (including a normal `Bearer <jwt>`) we
 * return `Uni.createFrom().nullItem()` so Quarkus falls through to
 * `JWTAuthMechanism`, which continues to own real JWT auth.
 *
 * ### Why not do the full authentication here
 *
 * Two reasons:
 *  1. [ApiKeyAuthenticator] / [PatAuthenticator] already run inside the
 *     JAX-RS chain (needed for `@RequiresScope` enforcement) and doing
 *     the Argon2id verify twice would be wasteful.
 *  2. Keeping this mechanism tiny means its failure modes are few — if
 *     anything changes in how Quarkus wires mechanisms, the surface
 *     area to audit is small.
 *
 * The pattern mirrors the existing design: JWT validation happens in
 * `JWTAuthMechanism`, JWT scope extraction in [JwtSecurityScopesFilter].
 */
@ApplicationScoped
@Priority(2000) // keep in sync with PRIORITY companion constant below.
class NonJwtBearerAuthMechanism : HttpAuthenticationMechanism {
    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager,
    ): Uni<SecurityIdentity> {
        val header = context.request().getHeader(AUTHORIZATION_HEADER) ?: return Uni.createFrom().nullItem()
        if (!isNonJwtBearer(header)) {
            // Real JWT (or no Authorization header we own) → hand off.
            return Uni.createFrom().nullItem()
        }
        // Return a placeholder authenticated identity so proactive auth
        // doesn't 401. The principal name is the literal `"credential"`;
        // no roles, no permissions. The downstream JAX-RS filter is
        // what actually verifies the credential and grants scopes (or
        // 401s on failure). This identity never influences authorization
        // — scope checks are handled by
        // `io.translately.api.security.ScopeAuthorizationFilter`.
        val identity =
            QuarkusSecurityIdentity
                .builder()
                .setAnonymous(false)
                .setPrincipal(Principal { PLACEHOLDER_PRINCIPAL })
                .build()
        return Uni.createFrom().item(identity)
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(HTTP_UNAUTHORIZED, null, null))

    override fun getCredentialTypes(): Set<Class<out io.quarkus.security.identity.request.AuthenticationRequest>> =
        setOf(TokenAuthenticationRequest::class.java)

    override fun getCredentialTransport(context: RoutingContext): Uni<HttpCredentialTransport> =
        Uni.createFrom().item(HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, SCHEME_API_KEY))

    override fun getPriority(): Int = PRIORITY

    /**
     * Match the two wire shapes we own:
     *  - `ApiKey <token>` (case-insensitive scheme)
     *  - `Bearer tr_pat_<token>` (case-insensitive Bearer, prefix is literal)
     *
     * Anything else — including `Bearer <jwt>` — returns false so
     * [JWTAuthMechanism] owns it.
     */
    private fun isNonJwtBearer(header: String): Boolean {
        val trimmed = header.trim()
        val space = trimmed.indexOf(' ')
        if (space <= 0) return false
        val scheme = trimmed.substring(0, space)
        val token = trimmed.substring(space + 1).trim()
        return when {
            scheme.equals(SCHEME_API_KEY, ignoreCase = true) -> true
            scheme.equals(SCHEME_BEARER, ignoreCase = true) && token.startsWith(PAT_TOKEN_PREFIX) -> true
            else -> false
        }
    }

    companion object {
        /** Authorization header name (duplicated from jakarta.ws.rs to keep this file jax-rs-independent). */
        const val AUTHORIZATION_HEADER: String = "Authorization"
        const val SCHEME_API_KEY: String = "ApiKey"
        const val SCHEME_BEARER: String = "Bearer"
        const val PAT_TOKEN_PREFIX: String = "tr_pat_"
        const val HTTP_UNAUTHORIZED: Int = 401

        /**
         * Higher than `HttpAuthenticationMechanism.DEFAULT_PRIORITY` (1000)
         * so Quarkus consults this mechanism before `JWTAuthMechanism` and
         * thus before the JWT parser has a chance to 401 on our non-JWT
         * bearer tokens.
         */
        const val PRIORITY: Int = 2000

        /**
         * Placeholder principal name used by the mechanism. Never
         * surfaces to callers; present only so `QuarkusSecurityIdentity`
         * passes its "non-anonymous identity must have a principal"
         * invariant.
         */
        const val PLACEHOLDER_PRINCIPAL: String = "non-jwt-credential"
    }
}
