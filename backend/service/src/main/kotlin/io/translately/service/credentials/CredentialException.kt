package io.translately.service.credentials

import io.translately.security.Scope

/**
 * Domain-layer failure modes for API key + PAT issuance / listing /
 * revocation (T110). Mapped at the `:backend:api` boundary to the uniform
 * error-body contract from `api-conventions.md`.
 *
 * Codes are stable across minor versions — downstream clients encode them.
 * Add a new subclass and deprecate the old one rather than renaming.
 */
sealed class CredentialException(
    val code: String,
    message: String,
) : RuntimeException(message) {
    /** Request body failed boundary validation (name too short / scopes malformed / expiry in the past). */
    class ValidationFailed(
        val fields: List<FieldError>,
    ) : CredentialException("VALIDATION_FAILED", "One or more fields failed validation.") {
        data class FieldError(
            val path: String,
            val code: String,
        )
    }

    /**
     * Caller asked for scopes they do not hold themselves. Minting a
     * credential that carries elevated scopes is the obvious privilege-
     * escalation gadget and is refused here. The response echoes the
     * offending scope tokens so the UI can highlight them.
     */
    class ScopeEscalation(
        val requested: Set<Scope>,
        val held: Set<Scope>,
    ) : CredentialException(
            "SCOPE_ESCALATION",
            "Cannot mint a credential with scopes the caller does not hold: " +
                Scope.serialize((requested - held).toSortedSet(compareBy(Scope::token))),
        ) {
        val missing: Set<Scope> = requested - held
    }

    /** Referenced API key / PAT / organization not found, or not owned by the caller. */
    class NotFound(
        entity: String,
    ) : CredentialException("NOT_FOUND", "$entity not found.")

    /** Requested scope token isn't defined in [Scope]. */
    class UnknownScope(
        val token: String,
    ) : CredentialException("UNKNOWN_SCOPE", "Unknown scope token: '$token'.")
}
