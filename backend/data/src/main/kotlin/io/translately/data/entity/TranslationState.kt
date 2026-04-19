package io.translately.data.entity

/**
 * Per-cell state for a [Translation]. One [Key] has one [Translation] row
 * per configured language; each row carries its own state so partial
 * progress is visible in the table UX.
 *
 * Progression: EMPTY → DRAFT → TRANSLATED → REVIEW → APPROVED. The state
 * machine is advisory — the service accepts any transition but the UI
 * surfaces the canonical forward edge.
 *
 * Kept in sync with the `ck_translations_state` CHECK constraint in V2.
 * See also ADR `docs/architecture/decisions/0002-translation-state-machine.md`.
 */
enum class TranslationState {
    /** No value yet — placeholder row waiting for translator input. */
    EMPTY,

    /** Translator saved a partial or work-in-progress value. */
    DRAFT,

    /** Translator marked the value finished; awaiting review. */
    TRANSLATED,

    /** Under review by a different member of the org (4-eyes). */
    REVIEW,

    /** Reviewer approved; eligible for export. */
    APPROVED,
}
