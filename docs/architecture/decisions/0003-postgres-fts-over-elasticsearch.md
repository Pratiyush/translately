---
title: 0003 — Postgres FTS over Elasticsearch for v1
parent: ADRs
grand_parent: Architecture
nav_order: 3
---

# 0003 — Postgres FTS over Elasticsearch for v1

- **Status:** Accepted
- **Date:** 2026-04-19
- **Deciders:** Pratiyush
- **Context link:** <https://github.com/Pratiyush/translately/issues/47>

## Context and problem statement

Phase 2 ships key search — the UI needs to query across `keys.key_name`, `keys.description`, and translated strings in `translations.value`, combined with filters for namespace, tags, and lifecycle state. The Phase-2 corpus is dozens-to-thousands of keys per project, optionally filtered to a single namespace. Searching is a hot path: the translator clicks into a project and the filter box is the first thing they touch.

Translately is meant to be self-hosted. Every added infrastructure dependency — a separate service, its RAM, its index-sync job, its upgrade cadence, its failure mode — is a tax on the operator. The question: do we lean on Postgres's built-in full-text search, or wire Elasticsearch / OpenSearch / Meilisearch as a secondary read-model from day one?

## Decision drivers

- Self-hosters run `docker compose up -d` and get a working stack. Every extra service widens the blast radius of that command.
- Phase-2 scale is measured in thousands of keys per project, not millions.
- We already have Postgres 16 in the stack; `tsvector`, GIN indexing, and `pg_trgm` ship in core with no licensing or install overhead.
- Schema evolution on a generated column is cheap; ripping out a secondary index is not.
- The search UX (`T207`) needs: exact-ish matching on key names, fuzzy substring matching on translations, filter composition (namespace · tags · state), ranking, pagination. All of this is within Postgres's native capabilities.
- MIT-only dependency policy — Elasticsearch's licensing split (Elastic v2 / SSPL) would force us onto OpenSearch, another integration surface.

## Considered options

1. **Postgres FTS (`tsvector`) + `pg_trgm`** — generated `tsvector` column on `keys`, trigram GIN on `translations.value`. Zero new infra.
2. **Elasticsearch / OpenSearch as primary search backend** — write-through index; Postgres remains system of record.
3. **Meilisearch** — lightweight, simpler than Elasticsearch; still a separate service with its own lifecycle.
4. **Trigram-only (no `tsvector`)** — skip FTS, rely on `pg_trgm` substring search for everything.

## Decision outcome

**Chosen option: Option 1 — Postgres FTS + `pg_trgm`.** The Phase-2 corpus is tiny by any search backend's standards, Postgres already sits on the critical path, and the generated-column approach keeps the FTS artefact in lock-step with `key_name` / `description` without triggers or application-side bookkeeping. Trigram on `translations.value` is the fuzzy-match fallback when FTS returns no hits.

If a deployment later outgrows this — tens of millions of keys, complex language-specific stemming, per-field scoring profiles — Elasticsearch / Meilisearch can land as a read-model in a Phase-8+ optimisation. The service-layer interface (`KeySearchService.search(...)` returning `KeySearchHit`) hides the storage choice, so swapping is a local concern.

### Consequences

- **Good:** one fewer service for self-hosters; no index-sync job; transactional consistency between writes and search results by construction; no licensing carve-outs; Postgres upgrades carry search forward.
- **Neutral:** search quality is "good enough, not great" — no per-language stemming, no typo tolerance beyond trigram similarity, no phrase proximity scoring.
- **Bad:** doesn't scale to very large corpora (millions of translation rows with frequent updates). Mitigated by the Phase-8+ escape hatch above.
- **Bad:** the `'simple'` text-search configuration gives up English-aware stemming. We accept this in exchange for uniform behaviour across every language a translator works in. See the [search architecture page](../search.md) for the full rationale.

### Implementation notes

- Touched modules: `backend/data` (V4 migration), `backend/service/keys` (new `KeySearchService`), `backend/app` (integration test).
- Migration: forward-only. `V4__keys_fts_trigram.sql` adds the generated column + indexes; no data backfill needed (`GENERATED ALWAYS` populates on insert).
- Rollback: drop the generated column + the two indexes. The extension can stay; it's cheap.
- Future: a per-language `tsvector` on `translations` can land in a later migration without touching V4.

## Links

- PR: <https://github.com/Pratiyush/translately/pulls?q=T206>
- Migration source: `V4__keys_fts_trigram.sql`
- Service source: `KeySearchService.kt`
- Architecture page: [Search](../search.md)
- [ADR index](README.md)
