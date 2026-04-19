---
title: Imports and exports (i18next JSON)
parent: API reference
nav_order: 5
permalink: /api/imports-and-exports.html
---

# Imports and exports — i18next JSON

Ships in Phase 3 (T301 + T302). One synchronous import endpoint, one synchronous export endpoint. Async + SSE progress streaming (T303) moved to Phase 4.

## POST /imports/json

Upsert translations into a project for a single language tag.

```
POST /api/v1/organizations/{orgSlug}/projects/{projectSlug}/imports/json
Authorization: Bearer <jwt>           # or ApiKey / PAT with imports.write
Content-Type: application/json
```

### Request body

```json
{
  "languageTag": "en",
  "namespaceSlug": "default",
  "mode": "MERGE",
  "body": "{\"nav.signIn\":\"Sign in\"}"
}
```

- `languageTag` — BCP-47 tag. Required. Must be configured on the project (or the project has no configured languages).
- `namespaceSlug` — optional. Auto-created if it doesn't exist yet. Defaults to `default`.
- `mode` — `KEEP` / `OVERWRITE` / `MERGE`. Case-insensitive. Required.
- `body` — the raw i18next JSON as a string (so the server can auto-detect flat vs nested shape).

### Conflict modes

| Mode | Existing missing | Existing blank | Existing non-blank |
|---|---|---|---|
| `KEEP` | write | **skip** | **skip** |
| `OVERWRITE` | write | write | write |
| `MERGE` | write | write | **skip** |

Import is transactional — if any exception is raised, the whole call rolls back. Per-row ICU validation lands bad rows in the `errors[]` array without rolling back the clean rows.

### Response

```json
{
  "total": 3,
  "created": 2,
  "updated": 0,
  "skipped": 1,
  "failed": 0,
  "errors": []
}
```

If one row has bad ICU:

```json
{
  "total": 2,
  "created": 1,
  "updated": 0,
  "skipped": 0,
  "failed": 1,
  "errors": [{"keyName":"broken","code":"INVALID_ICU_TEMPLATE","message":"Unmatched '{'..."}]
}
```

### Errors

- `400 VALIDATION_FAILED` — bad mode, empty body, malformed JSON, or unsupported shape (e.g. top-level array).
- `401 UNAUTHENTICATED`
- `404 NOT_FOUND` — project not found, caller not a member, or languageTag not configured.

## GET /exports/json

Download one language's translations as i18next JSON.

```
GET /api/v1/organizations/{orgSlug}/projects/{projectSlug}/exports/json
     ?languageTag=en
     &shape=FLAT
     [&namespaceSlug=default]
     [&tags=email,onboarding]        # keys must carry every listed tag
     [&minState=APPROVED]             # EMPTY < DRAFT < TRANSLATED < REVIEW < APPROVED
Authorization: Bearer <jwt>           # or credential with exports.read
```

The response body is the JSON file itself. `Content-Disposition` carries a suggested filename (`{projectSlug}-{languageTag}-{shape}.json`), `X-Translately-Key-Count` carries the row count for progress bars.

### Errors

- `400 VALIDATION_FAILED` — missing `languageTag`, bad `shape`, bad `minState`.
- `401 UNAUTHENTICATED`
- `404 NOT_FOUND` — project not found or caller not a member.

## Scopes

| Endpoint | Scope |
|---|---|
| `POST /imports/json` | `imports.write` |
| `GET /exports/json` | `exports.read` |

See [scopes.md](scopes.md) for how scopes compose with roles.
