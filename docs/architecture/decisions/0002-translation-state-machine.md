---
title: 0002 — Translation state machine
parent: ADRs
grand_parent: Architecture
nav_order: 2
---

# 0002 — Translation state machine

- **Status:** Accepted
- **Date:** 2026-04-19
- **Deciders:** Pratiyush
- **Context link:** <https://github.com/Pratiyush/translately/issues/42>

## Context and problem statement

Phase 2 introduces per-language translation cells. A given `Key` has one `Translation` row per configured language, and each cell needs a lifecycle state the UI can filter on, the importer can hydrate, and the audit log can diff. The state doubles as the export gate — only fully-APPROVED translations should land in a production bundle without a reviewer's explicit override.

How rich should that state machine be? Too few states and reviewers can't distinguish "I wrote this and need someone to look" from "this is ready to ship." Too many and every transition becomes a UI decision the translator has to defend.

## Decision drivers

- Covers the translator → reviewer → export loop with zero manual tagging.
- Maps cleanly onto the `CHECK` constraint we ship in V3; string values persist over decades.
- Plays well with bulk imports (rows often land already-translated with no human review).
- Matches the idioms a translator moving from another localization tool will expect.
- Expressible as a single `VARCHAR(16)` column with a CHECK constraint — no side-tables, no bitfields.

## Considered options

1. **3 states:** `EMPTY / TRANSLATED / APPROVED`. Minimal; no draft-vs-finished split, no review limbo.
2. **4 states:** `EMPTY / TRANSLATED / REVIEW / APPROVED`. Review as an explicit interstitial, but no draft state.
3. **5 states:** `EMPTY / DRAFT / TRANSLATED / REVIEW / APPROVED`. Draft = saved but not yet finished; TRANSLATED = the translator thinks it's done; REVIEW = someone else is looking; APPROVED = cleared for export.
4. **6 states:** add `OBSOLETE` or `REJECTED` to 5-state for rejected review outcomes. Every rejection re-enters the flow by reverting to DRAFT anyway, so the extra state is cosmetic.

## Decision outcome

**Chosen option: Option 3 (5 states).** The DRAFT↔TRANSLATED split is the one the UI actually needs — autosave while typing lands as DRAFT, explicit "mark done" promotes to TRANSLATED. The REVIEW phase lets the reviewer hold the cell while working through it so edits don't race.

Rejection uses the existing DRAFT transition (rejecting sends the cell back to DRAFT with a comment); no sixth state needed.

### Consequences

- **Good:** UI gets three meaningful filter chips (DRAFT, TRANSLATED, REVIEW) plus the outer bookends; exporters have one predicate (`state = 'APPROVED'`) to filter on; importers that can't know review status hydrate as TRANSLATED and let a reviewer promote.
- **Neutral:** transitions are advisory (not enforced by DB), so every state is reachable from every other — the DB CHECK is the sole hard guarantee. Service layer can add transition rules later without schema churn.
- **Bad:** "rejection" has no first-class representation beyond a comment + DRAFT revert. If the product grows a formal rejection UX later, we'd add `REJECTED` in a future migration.

### Implementation notes

- Touched modules: `backend/data` (`TranslationState.kt`, V3 migration), `backend/service` (future Phase 2 work wiring the transitions).
- Migration: new field, no backfill — this is greenfield.
- Rollback: drop the `translations` table (V3 migration revert) undoes it entirely.

## Links

- PR: <https://github.com/Pratiyush/translately/pulls?q=T201>
- Entity source: `backend/data/src/main/kotlin/io/translately/data/entity/Translation.kt` (filesystem link omitted until the file lands on master)
- [ADR index](README.md)
