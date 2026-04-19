---
title: ICU validation
parent: Architecture
nav_order: 11
---

# ICU MessageFormat validation

Translations are stored as ICU MessageFormat source strings. Every write path validates the source before persisting so bad syntax never reaches the database. `IcuValidator` (in `:backend:service`, package `io.translately.service.translations`) is the single entry point.

Introduced by T203 (#44) in Phase 2.

## Contract

```kotlin
val result: ValidationResult = icuValidator.validate(source, locale)
if (!result.ok) {
    // result.errors is a list of ValidationError(line, col, message, severity)
}
```

- **`source: String`** — the ICU MessageFormat source as typed by the translator or pulled from an import.
- **`locale: java.util.Locale`** — currently unused; accepted so future WARNING tiers can diff the plural branches against the locale's CLDR plural keywords.
- **Return value** — a `ValidationResult` with a derived `ok` flag and a list of structured `ValidationError`s. Empty list ⇔ `ok = true`.

Empty or blank sources are valid. The `TranslationState` enum on the Translation entity gates export; the validator only rejects malformed *input*, not empty rows.

## What the validator checks

| Check | Rationale |
|---|---|
| Full ICU grammar — arguments, plural / selectordinal / select, apostrophe escapes, nested branches | Parse via `com.ibm.icu.text.MessagePattern` rather than `MessageFormat` because MessagePattern's parse exceptions carry a source offset we can turn into a line+column. |
| Missing `other` branch in plural / selectordinal / select | MessagePattern enforces this at parse time — we catch the resulting `IllegalArgumentException` and surface it as a structured error. CLDR requires `other` on every selector. |
| Unknown SIMPLE argument types (`{x, bogusType}`) | Parses cleanly at the grammar level but blows up at format time. Catching here lets the editor mark the problem as the user types. Accepted types: `number`, `date`, `time`, `spellout`, `ordinal`, `duration`. |

## What the validator does NOT check (on purpose)

- **Cross-translation argument consistency.** "Does the German translation use the same `{name}` argument the English one does?" — belongs on a key-level diff, not a per-cell validator.
- **Locale-specific plural coverage.** A Russian translation that supplies only `one` + `other` is technically under-specified (Russian needs `few` + `many`). Authors routinely ship half-translated cells during a translation sprint; making this an ERROR would gate save on every keystroke. Tracked as a future WARNING once the Severity pipeline lights up in T207.

## Library

`com.ibm.icu:icu4j:76.1`. ICU is a Unicode-consortium permissive licence, MIT-compatible. No other ICU/CLDR dep is pulled in — this is the full tree-shaken package.

## Where it's consumed

- **Editor autosave (T207)** — validates each keystroke batch on the webapp's PUT path before persisting. Errors surface via CodeMirror 6's linter lane.
- **JSON importer (T301)** — validates every incoming translation value; bad ICU aborts the import job with a structured per-row error list.

## Tests

- `backend/service/src/test/kotlin/io/translately/service/translations/IcuValidatorTest.kt` — 13 cases covering happy path, CLDR plural sets (English + Russian + Serbian), missing-`other` rejection on plural / selectordinal / select, unknown arg type, malformed source with line+col recovery, nested plural-in-select, and nested errors surfaced inside well-formed outer structures.

No integration test is needed — the validator is a pure function and doesn't touch the database.

## Changelog

First shipped in `[Unreleased]` under T203 (#44).
