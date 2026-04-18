package io.translately.api.orgs

import io.translately.service.orgs.OrgException
import jakarta.enterprise.inject.Instance
import org.eclipse.microprofile.jwt.JsonWebToken

/**
 * Resolve the calling user's external ID from the JAX-RS-injected JWT.
 *
 * Returns the `sub` claim of the access token, or throws
 * [OrgException.NotMember] when the JWT is missing / has no subject
 * (mapped to NOT_FOUND so the server never discloses auth-vs-authz).
 */
internal fun callerIdFrom(jwt: Instance<JsonWebToken>): String {
    val token = if (jwt.isUnsatisfied) null else runCatching { jwt.get() }.getOrNull()
    return token?.subject?.takeIf { it.isNotBlank() } ?: throw OrgException.NotMember()
}
