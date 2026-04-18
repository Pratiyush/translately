package io.translately.service.orgs

/**
 * Slug canonicalisation rules — shared by org-create and project-create.
 *
 * Rules (matches the regex the backend's [io.translately.api.tenant.TenantRequestFilter]
 * validates against for the URL-path tenant identifier):
 *
 *   - lowercase ASCII
 *   - letters, digits, hyphens only
 *   - can't start or end with a hyphen
 *   - length 2..64
 *
 * If the caller provides a slug that already satisfies the shape, we keep
 * it verbatim. Otherwise we derive one from the name (lowercase, strip
 * non-alphanumeric, collapse runs of `-`).
 */
object SlugNormalizer {
    private val VALID = Regex("""^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$""")
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 64

    /**
     * Normalise a user-supplied slug or derive one from [fallbackName].
     * Returns `null` if neither input can produce a valid slug — callers
     * should surface that as `VALIDATION_FAILED`.
     */
    fun canonicalise(
        requested: String?,
        fallbackName: String,
    ): String? {
        requested
            ?.trim()
            ?.lowercase()
            ?.takeIf { VALID.matches(it) }
            ?.let { return it }
        val derived =
            fallbackName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        if (derived.length < MIN_LENGTH || derived.length > MAX_LENGTH) return null
        return derived.takeIf { VALID.matches(it) }
    }
}
