package io.translately.service.orgs

/**
 * Domain-layer failure modes for organization + membership + project
 * management (T118, T119, plus backend CRUD prerequisite).
 *
 * `code` is stable across minor versions and maps at the `:backend:api`
 * boundary to the uniform error-body envelope in `api-conventions.md`.
 */
sealed class OrgException(
    val code: String,
    message: String,
) : RuntimeException(message) {
    class ValidationFailed(
        val fields: List<FieldError>,
    ) : OrgException("VALIDATION_FAILED", "One or more fields failed validation.") {
        data class FieldError(
            val path: String,
            val code: String,
        )
    }

    /** Referenced organization / project / member not found — or the caller is not a member. */
    class NotFound(
        entity: String,
    ) : OrgException("NOT_FOUND", "$entity not found.")

    /** Caller has no membership in the target org — same shape as NOT_FOUND to avoid enumerating private orgs. */
    class NotMember : OrgException("NOT_FOUND", "Organization not found.")

    /** Unique slug collision (`org.slug` globally, `project.slug` within an org). */
    class SlugTaken(
        val slug: String,
        entity: String,
    ) : OrgException(
            "${entity.uppercase()}_SLUG_TAKEN",
            "${entity.replaceFirstChar(Char::uppercase)} slug '$slug' is already in use.",
        )

    /** Caller is authenticated but lacks the scope required for this action. */
    class InsufficientScope(
        val required: String,
    ) : OrgException("INSUFFICIENT_SCOPE", "This action requires scope: $required")

    /** Can't remove / demote the last OWNER of an org — would lock the org out of administration. */
    class LastOwner : OrgException("LAST_OWNER", "An organization must keep at least one OWNER member.")
}
