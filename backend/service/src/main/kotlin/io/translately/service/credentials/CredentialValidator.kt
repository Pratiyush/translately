package io.translately.service.credentials

import io.translately.security.Scope
import java.time.Instant

/**
 * Boundary validation for credential requests (mint). Called before any
 * DB work so the service layer can trust its inputs.
 *
 * Rules are intentionally narrow:
 *  - `name` is 1..128 characters after trim, and non-empty. The DB column
 *    is `VARCHAR(128)`; we reject longer inputs at the boundary with a
 *    structured error rather than letting Postgres surface a cryptic
 *    "value too long" failure.
 *  - `scopes` is a non-empty set of known [Scope] tokens. The service
 *    layer enforces the scope-intersection rule separately (see
 *    [CredentialException.ScopeEscalation]); the validator only guards
 *    against unknown tokens and empty sets here.
 *  - `expiresAt` must be strictly in the future when supplied. A null
 *    value is legal and means the credential does not expire on its own —
 *    it lives until revoked.
 */
object CredentialValidator {
    private const val MAX_NAME_LENGTH = 128

    /**
     * Validate a mint request. Throws
     * [CredentialException.ValidationFailed] on any structural issue;
     * throws [CredentialException.UnknownScope] if a scope token is not
     * defined in the [Scope] enum.
     */
    fun validateMint(
        name: String,
        scopeTokens: Collection<String>,
        expiresAt: Instant?,
        now: Instant = Instant.now(),
    ): Set<Scope> {
        val errors = mutableListOf<CredentialException.ValidationFailed.FieldError>()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errors += CredentialException.ValidationFailed.FieldError("body.name", "REQUIRED")
        } else if (trimmed.length > MAX_NAME_LENGTH) {
            errors += CredentialException.ValidationFailed.FieldError("body.name", "TOO_LONG")
        }

        if (scopeTokens.isEmpty()) {
            errors += CredentialException.ValidationFailed.FieldError("body.scopes", "REQUIRED")
        }

        if (expiresAt != null && !expiresAt.isAfter(now)) {
            errors += CredentialException.ValidationFailed.FieldError("body.expiresAt", "MUST_BE_FUTURE")
        }

        if (errors.isNotEmpty()) {
            throw CredentialException.ValidationFailed(errors)
        }

        // Scope-token resolution is a separate failure mode — surface the
        // first unknown token so the UI can highlight it.
        val resolved = mutableSetOf<Scope>()
        for (raw in scopeTokens) {
            val scope = Scope.fromToken(raw.trim())
                ?: throw CredentialException.UnknownScope(raw)
            resolved += scope
        }
        return resolved
    }
}
