package io.translately.security

/**
 * Permission token attached to a JWT, API key, or PAT.
 *
 * Scopes are the atomic unit of authorization across the public REST API.
 * The mapping from (user role, project role) → effective scope set lives in
 * `ScopeResolver` (Phase 1, T109). Resources / methods declare required scopes
 * via [RequiresScope]; [io.translately.api.security.ScopeAuthorizationFilter]
 * enforces.
 *
 * ### Naming convention
 *
 * Each scope carries a stable, dotted, lowercase token:
 *
 *   `<domain>.<action>`
 *
 * where `<action>` is `read` or `write`. `write` implies `read` at the
 * resolver level (a caller with `keys.write` passes a `keys.read` check).
 * The enum values themselves are in `SCREAMING_SNAKE_CASE` for Kotlin
 * ergonomics.
 *
 * Never rename an existing token — downstream clients (CLI, SDKs, API keys
 * issued to customers) encode these strings. Add a new scope, deprecate the
 * old one, remove one minor version later.
 */
enum class Scope(
    val token: String,
) {
    // ---- Organization / membership ------------------------------------
    ORG_READ("org.read"),
    ORG_WRITE("org.write"),
    MEMBERS_READ("members.read"),
    MEMBERS_WRITE("members.write"),
    API_KEYS_READ("api-keys.read"),
    API_KEYS_WRITE("api-keys.write"),
    AUDIT_READ("audit.read"),

    // ---- Project-wide -------------------------------------------------
    PROJECTS_READ("projects.read"),
    PROJECTS_WRITE("projects.write"),
    PROJECT_SETTINGS_WRITE("project-settings.write"),

    // ---- Keys + translations (Phase 2 core) ---------------------------
    KEYS_READ("keys.read"),
    KEYS_WRITE("keys.write"),
    TRANSLATIONS_READ("translations.read"),
    TRANSLATIONS_WRITE("translations.write"),

    // ---- Imports + exports (Phase 3) ----------------------------------
    IMPORTS_WRITE("imports.write"),
    EXPORTS_READ("exports.read"),

    // ---- AI / MT + TM (Phase 4) ---------------------------------------
    AI_SUGGEST("ai.suggest"),
    AI_CONFIG_WRITE("ai-config.write"),
    TM_READ("tm.read"),
    GLOSSARIES_READ("glossaries.read"),
    GLOSSARIES_WRITE("glossaries.write"),

    // ---- Screenshots (Phase 5) ----------------------------------------
    SCREENSHOTS_READ("screenshots.read"),
    SCREENSHOTS_WRITE("screenshots.write"),

    // ---- Webhooks + CDN (Phase 6) -------------------------------------
    WEBHOOKS_READ("webhooks.read"),
    WEBHOOKS_WRITE("webhooks.write"),
    CDN_READ("cdn.read"),
    CDN_WRITE("cdn.write"),

    // ---- Tasks + branching (Phase 7) ----------------------------------
    TASKS_READ("tasks.read"),
    TASKS_WRITE("tasks.write"),
    BRANCHES_READ("branches.read"),
    BRANCHES_WRITE("branches.write"),
    ;

    companion object {
        /** Parse a wire token into its [Scope]. Returns null for unknown tokens. */
        fun fromToken(token: String): Scope? = entries.firstOrNull { it.token == token }

        /** Render a set of scopes as a space-separated token string (API-key / PAT format). */
        fun serialize(scopes: Collection<Scope>): String = scopes.joinToString(" ") { it.token }

        /** Parse a space-separated token string into a set of [Scope]; silently drops unknown tokens. */
        fun parse(raw: String?): Set<Scope> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { fromToken(it) }
                .toSet()
        }
    }
}
