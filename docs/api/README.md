# API reference

Translately exposes a single versioned HTTP API under `/api/v1/`. This tree is the canonical reference.

Per [CLAUDE.md rule #10](../../CLAUDE.md), every PR that changes an endpoint, scope, error code, rate-limit, or versioning rule lands its matching page update here — including a regenerated [`openapi.json`](openapi.json) — in the same PR.

## Pages

- [`openapi.json`](openapi.json) — machine-readable OpenAPI 3.1 spec, auto-generated from the backend. Regenerate on every API change (T113).
- [Scopes](scopes.md) — permission scope matrix (role → scope set), scope naming convention, `@RequiresScope` usage.
- [Error codes](errors.md) — stable catalogue of `error.code` strings, HTTP status mapping, troubleshooting.
- [Rate limits](rate-limits.md) — per-token and per-endpoint policy, headers, `429` retry semantics.
- [Versioning](versioning.md) — URL-path versioning, deprecation policy, breaking-change rule.
- [Authentication](auth.md) — JWT vs. PAT vs. API key, bearer-credential split, refresh rotation.

## Conventions (mirror of [`.kiro/steering/api-conventions.md`](../../.kiro/steering/api-conventions.md))

- **Base path:** `/api/v1/`. v2 only lands when v1 cannot absorb a change without breaking clients.
- **Errors:** uniform envelope — `{"error":{"code":"ERROR_CODE","message":"human readable","details":{...}}}`. Codes are `SCREAMING_SNAKE_CASE`, stable across minor versions.
- **Pagination:** cursor-based, `?cursor=...&limit=...`, responses carry `nextCursor` (null when exhausted).
- **IDs:** ULIDs, 26-char base32, sortable. Exposed verbatim on the wire.
- **Times:** ISO-8601 UTC, always with a `Z` suffix.
- **Scopes:** every protected endpoint declares a minimum scope set via `@RequiresScope`. `INSUFFICIENT_SCOPE` is the 403 code.

## OpenAPI ingestion

The committed [`openapi.json`](openapi.json) is the source of truth for generated clients:

- The `@translately/js` SDK (Phase 5 — `sdks/js/`) is generated from this file via `openapi-typescript`.
- The webapp uses the same generated types for every network call (T120).

If you change a controller, regenerate the spec in the same PR. A stale `openapi.json` breaks the SDK build.
