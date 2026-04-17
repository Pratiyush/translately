package io.translately.api.security

import io.translately.security.Scope

/**
 * Thrown by [ScopeAuthorizationFilter] when the current request's granted
 * scopes don't cover every scope required by the target resource method.
 * Mapped to an HTTP 403 by [InsufficientScopeExceptionMapper].
 */
class InsufficientScopeException(
    val required: Set<Scope>,
    val missing: Set<Scope>,
) : RuntimeException(
        "Insufficient scope. Required: ${Scope.serialize(required)}; " +
            "missing: ${Scope.serialize(missing)}",
    )
