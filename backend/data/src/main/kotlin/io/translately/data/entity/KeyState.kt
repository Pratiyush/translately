package io.translately.data.entity

/**
 * Lifecycle state of a translation [Key].
 *
 * Keys progress NEW → TRANSLATING → REVIEW → DONE as translations land and
 * get approved. Transitions are advisory — the UI exposes explicit state
 * changes but the service doesn't block out-of-order moves.
 *
 * Kept in sync with the `ck_keys_state` CHECK constraint in V2 migration.
 */
enum class KeyState {
    /** Just created; no translations yet. */
    NEW,

    /** At least one language has a DRAFT or TRANSLATED translation. */
    TRANSLATING,

    /** All configured languages are TRANSLATED; a reviewer is working through them. */
    REVIEW,

    /** All translations APPROVED. */
    DONE,
}
