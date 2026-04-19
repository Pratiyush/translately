package io.translately.data.entity

/**
 * Append-only activity-log action types for an [Activity] row.
 *
 * Phase 2 fills in `actor_user_id`, the target [Activity.key], and the
 * type — the structured `diff` JSONB column stays null until the Phase 7
 * audit log wires it in. The type alone gives the UI enough to render a
 * per-key timeline; rich diffs are a 0.7.0 concern.
 *
 * Kept in sync with the `ck_key_activity_action_type` CHECK constraint
 * in V2 migration.
 */
enum class ActivityType {
    /** Key first created. */
    CREATED,

    /** Key metadata (name, description, namespace, tags) edited. */
    UPDATED,

    /** Key soft-deleted. */
    DELETED,

    /** Key's overall state enum changed (NEW → TRANSLATING, etc.). */
    STATE_CHANGED,

    /** A translation cell was edited or its state changed. */
    TRANSLATED,

    /** A new comment was posted on the key. */
    COMMENTED,

    /** Tag membership changed (added or removed). */
    TAGGED,
}
