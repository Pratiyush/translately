package io.translately.security

import jakarta.enterprise.context.RequestScoped

/**
 * Per-request bag of scopes granted to the current principal. Populated by
 * whichever authenticator ran for this request (JWT claim extraction, API-key
 * lookup, PAT lookup). Unauthenticated requests start with an empty set.
 *
 * Intentionally tiny — just a set with a couple of derived accessors. The
 * filter layer in `:backend:api` does the enforcement; service code should
 * prefer explicit checks via `hasAll`/`hasAny` only when it needs to branch
 * on authorization (e.g. hiding fields).
 */
@RequestScoped
open class SecurityScopes {
    private val scopes: MutableSet<Scope> = mutableSetOf()

    /** Immutable view of the granted scope set. */
    val granted: Set<Scope>
        get() = scopes.toSet()

    /** Replaces the current scope set. Callable only by authenticators. */
    open fun grantAll(newScopes: Collection<Scope>) {
        scopes.clear()
        scopes.addAll(newScopes)
    }

    /** Clears the scope set — used by tests and the anonymous path. */
    open fun revokeAll() {
        scopes.clear()
    }

    /** True iff every scope in [required] is granted. */
    open fun hasAll(required: Collection<Scope>): Boolean = required.isEmpty() || scopes.containsAll(required)

    /** True iff at least one of [candidates] is granted. */
    open fun hasAny(candidates: Collection<Scope>): Boolean = candidates.any { it in scopes }

    /** Returns scopes the principal is missing relative to [required]. */
    open fun missing(required: Collection<Scope>): Set<Scope> = required.toSet() - scopes
}
