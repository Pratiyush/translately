# API versioning

The Translately REST API is versioned in the **URL path**: every endpoint lives under `/api/v1/`. This page documents what is and isn't a breaking change, how deprecations roll out, and the client-compatibility contract.

Related: [API conventions steering](../../.kiro/steering/api-conventions.md), [error codes](errors.md), [changelog](../../CHANGELOG.md).

## Contract

> **One live major version at a time, with a one-minor-version overlap when a `v2` ships.** Additive changes never bump the version. Breaking changes bump the path.

- **`v1` is the current surface.** Every Phase 0–7 ticket lands here. A hypothetical `v2` happens only when a change cannot be expressed without breaking a published shape — and even then, we prefer a differently-named endpoint inside `v1` over a whole-API bump.
- **When `v2` ships**, `v1` remains served, unchanged, for at least one minor-version overlap. Clients get a `Deprecation: true` header on `v1` responses from that release forward.
- **No `Accept` header version negotiation.** The path is authoritative. Clients don't have to invent plumbing to pass a custom media type.

## What counts as breaking

| Change | Breaking? |
|---|---|
| Add an optional request field | **No** |
| Add a required request field with a default | **No** (treat as additive) |
| Add a required request field without a default | **Yes** |
| Add a new response field | **No** — clients must tolerate unknown fields |
| Add a new enum value | **No** — clients must tolerate unknown values (see below) |
| Remove a response field | **Yes** |
| Rename a response field | **Yes** |
| Change a response field's type or semantics | **Yes** |
| Add a new status code (e.g. 202 where 200 was returned) | **Yes** |
| Tighten validation on an existing endpoint | **Yes** |
| Add a new endpoint | **No** |
| Remove an endpoint | **Yes** |
| Rename an endpoint path | **Yes** (serve the old path for the deprecation window) |
| Add a new `error.code` value | **No** — clients must tolerate unknowns |
| Remove / rename an `error.code` value | **Yes** |

Additive enum and error-code values are *never* breaking. This is a hard contract — SDKs and the webapp are written with `default` branches and "unknown" mappings.

## Deprecation workflow

When a field, endpoint, or error code is on its way out:

1. **Mark deprecated in the source** — field-level `@Deprecated` / JSDoc comment on the SDK, matching entry in `CHANGELOG.md` under `### Deprecated` in the release the deprecation lands.
2. **Ship the [Deprecation / Sunset headers](https://www.rfc-editor.org/rfc/rfc8594)** on every affected response:

   ```
   Deprecation: true
   Sunset: Sat, 01 Nov 2026 00:00:00 GMT
   Link: <https://github.com/Pratiyush/translately/blob/master/docs/api/errors.md#deprecated>; rel="deprecation"
   ```
3. **Cross-link from the docs.** The migration path lives under [`docs/migration/`](../../docs) when cross-version migration is needed.
4. **Sunset at the announced release.** Move the CHANGELOG entry from `### Deprecated` to `### Removed`.

Minimum deprecation window: **one minor version**. Longer for anything SDK consumers are likely to hit.

## Client forward-compatibility rules

- **Unknown response fields** — ignore silently. Do not fail on extra keys.
- **Unknown enum values** — map to a `"unknown"` / `"other"` sentinel in the client; do not throw. The webapp renders unknowns as "Unsupported value — update the app".
- **Unknown `error.code`** — log with the `code` string, surface the `message` to the user, treat as a generic failure. Don't branch on `code` without a default.
- **Unknown status codes** — treat `2xx` as success, `4xx` as client error, `5xx` as server error, with appropriate fallback messaging.
- **Missing optional fields** — treat as `null` / absent rather than the default of some prior version. Don't synthesize values you didn't receive.

These rules also apply to SDK regeneration: when the committed [`openapi.json`](openapi.json) gains a field, SDK callers pick it up automatically; losing a field is the breaking-change path above.

## OpenAPI compatibility

- The source of truth is Quarkus's Smallrye OpenAPI generation at build time.
- [`docs/api/openapi.json`](openapi.json) is committed and regenerated on every API change (T113).
- Every resource method must carry `@Operation`, `@APIResponses`, and request / response schemas — CI fails the build if any is missing.
- Client SDKs (`@translately/js` in Phase 5+) are generated from the committed `openapi.json` via `openapi-typescript`; regenerating the SDK as part of the API PR means types and runtime can't drift.

## Version in response bodies

Responses do **not** carry an explicit `apiVersion` field — the version is in the URL. Adding one would only be useful for negotiating between `v1` and `v2` at the payload level, which the path-versioning strategy explicitly avoids.

## Why path-versioning, not header?

Header-based versioning (`Accept: application/vnd.translately.v1+json`) is elegant in theory and a nightmare in practice:

- Browser tabs and `curl` commands can't hit it without plumbing.
- CDN caching layers ignore custom `Accept` values by default.
- Observability tools (log aggregators, Grafana, Sentry) don't see the version dimension.
- Clients get the version wrong more often than they get the path wrong.

Path-versioning keeps a curl one-liner copy-pasteable and makes the `v1` → `v2` cutover visible to everyone reading the logs. We optimize for operational clarity.

## See also

- [`CHANGELOG.md`](../../CHANGELOG.md) — every release documents its Added / Changed / Deprecated / Removed / Fixed / Security sections.
- [Error catalogue](errors.md) — the stable-across-versions contract.
- [`.kiro/steering/api-conventions.md`](../../.kiro/steering/api-conventions.md) — the authoritative steering version of this page.
