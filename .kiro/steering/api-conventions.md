# API conventions — always-loaded steering

The REST API Translately exposes at `/api/v1/...`. All adapters (webapp, SDKs, CLI, webhooks consumers) rely on this contract, so changes are high-stakes. When in doubt, prefer the rule here over improvisation.

## Base

- **Scheme**: HTTPS in prod, HTTP on `localhost` in dev.
- **Base path**: `/api/v1`. A `v2` base arrives only for genuinely breaking changes; `v1` stays for one minor-version overlap.
- **Content type**: `application/json; charset=utf-8` for request and response bodies. `multipart/form-data` only for screenshot uploads.
- **Encoding**: UTF-8 everywhere. Identifier fields are ULIDs (Crockford base32, 26 chars).

## Resource naming

- **Kebab-case** URL segments: `/api/v1/organizations/{orgId}/projects/{projectId}/translation-keys`.
- **Plural nouns** for collections: `/projects`, `/keys`, `/translations`, `/languages`.
- **Sub-resources** describe ownership hierarchy: `/projects/{id}/keys/{keyId}/translations/{lang}`.
- **Actions that don't fit REST** use imperative sub-paths: `POST /projects/{id}/import-json`, `POST /keys/{id}/suggest-translation`. Keep them rare and documented.

## Methods

| Method | Use |
|---|---|
| `GET` | Read, idempotent, safe. No side effects. |
| `POST` | Create, or action endpoints. Non-idempotent by default. |
| `PUT` | Replace (full representation). |
| `PATCH` | Partial update. Body: JSON Merge Patch (RFC 7396). |
| `DELETE` | Remove. Idempotent — `DELETE` on a missing resource returns `204`. |

Idempotency for `POST` is opt-in via `Idempotency-Key` header (Phase 2+).

## Status codes

| Code | When |
|---|---|
| `200` | Success with body. |
| `201` | Resource created. `Location` header required. |
| `202` | Accepted for async processing; body includes `jobId`. |
| `204` | Success, no body (most `DELETE`, some `PATCH`). |
| `400` | Malformed request (bad JSON, wrong types, failed validation). |
| `401` | No/invalid authentication. |
| `403` | Authenticated but forbidden by permissions. |
| `404` | Resource not found OR auth prevents disclosing existence. |
| `409` | Conflict (duplicate key, version mismatch, lock held). |
| `410` | Gone (soft-deleted and past retention). |
| `415` | Unsupported content type. |
| `422` | Semantic validation failure (e.g., ICU message invalid). |
| `429` | Rate limit exceeded. `Retry-After` header required. |
| `500` | Unhandled server error. Logged with a `traceId`. |
| `503` | Dependency unavailable (DB, Redis, S3, mail). |

## Error body

Uniform across all 4xx/5xx:

```json
{
  "error": {
    "code": "KEY_NAME_TAKEN",
    "message": "A key named \"home.title\" already exists in this namespace.",
    "details": {
      "keyName": "home.title",
      "namespaceId": "01HT7F8..."
    },
    "traceId": "01HT7F8..."
  }
}
```

- `code` is a stable machine-readable token in `SCREAMING_SNAKE_CASE`. Never translate or rename; add new codes, never rename existing ones.
- `message` is a human-readable English string; the UI i18ns off `code`, not `message`.
- `details` is optional structured context.
- `traceId` is always present, matches the server log entry.

## Validation

- Field-level errors come back as `code: "VALIDATION_FAILED"` with `details.fields: [{ path: "body.name", code: "REQUIRED" }, ...]`.
- Server trusts nothing from the client. Every field is validated server-side even if the webapp validates it first.
- ICU messages are validated with `icu4j`; invalid → `422 ICU_MESSAGE_INVALID` with position info in `details`.

## Pagination

- **Cursor-based** for any collection that can grow unbounded (keys, translations, activities, webhook deliveries).
  ```
  GET /api/v1/projects/{id}/keys?limit=50&cursor=<opaque>
  ```
  Response: `{ "data": [...], "nextCursor": "<opaque>" | null, "hasMore": true }`.
- **Offset+limit** only for bounded lists (organizations of a user, languages of a project).
- Default `limit=50`, max `limit=200`. Larger requests return `400 PAGE_TOO_LARGE`.

## Filtering / sorting

- **Filtering** via query params:
  `GET /keys?namespaceId=...&tag=marketing&translatedInLang=de`.
- **Sorting** via `sort=field,-other` (`-` prefix = descending). Only whitelisted fields; unknown fields → `400 INVALID_SORT_FIELD`.
- **Search** via `q=...`; server decides full-text vs. substring per endpoint.

## Authentication

- **Bearer JWT**: `Authorization: Bearer <token>`. Access tokens short-lived (≤15 min).
- **Refresh**: `POST /api/v1/auth/refresh` with the refresh token in a `HttpOnly; Secure; SameSite=Lax` cookie. Rotates on use.
- **API keys**: `Authorization: ApiKey <key>` (keys are per-project, scoped). Hashed with Argon2id; prefix shown in UI.
- **PATs** (Phase 1+): `Authorization: Bearer tr_pat_...` where `tr_pat_` is the prefix. Scoped like API keys; expire.
- **OIDC**: Token from the IdP, validated by Quarkus OIDC.

## Authorization

- Permission scope enum defined in `security/`. Checked per endpoint via a `@RequiresScope(...)` annotation on the JAX-RS resource method.
- Multi-tenancy is enforced by the `TenantRequestFilter` + Hibernate filter. A missing `organizationId` anywhere in the request path is a 500, not a 404 — it's a bug.

## Rate limiting

- **Per token** (user JWT, API key, PAT, anonymous IP) via Redis + sliding window.
- Limits:
  - Authenticated read: 600 req/min
  - Authenticated write: 120 req/min
  - AI suggest: 60 req/min and subject to per-project budget cap
  - Unauthenticated (signup, login, password reset): 10 req/min per IP
- `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers on every response.
- On `429`: `Retry-After` seconds.

## Async jobs

- Long operations (import, export, bulk translate) return `202` with body `{ "jobId": "...", "status": "QUEUED" }`.
- Poll `GET /api/v1/jobs/{jobId}`. Statuses: `QUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED`.
- SSE stream endpoint `GET /api/v1/jobs/{jobId}/events` for realtime progress in the webapp.

## Webhooks

- Outgoing: `POST` with body `{ "event": "...", "data": {...}, "projectId": "...", "eventId": "..." }`.
- Signed with HMAC-SHA256 in `X-Translately-Signature: t=<unix>,v1=<hex>`. Consumer recomputes over `t + "." + body`.
- Retries: exponential backoff (1m, 5m, 15m, 1h, 6h, 24h). Abandoned after 24h; recorded in delivery log.
- `X-Translately-Delivery` unique per attempt; `X-Translately-Event` names the event.

## OpenAPI

- Source of truth is Quarkus Smallrye OpenAPI generation at build time.
- Every resource method must have `@Operation`, `@APIResponses`, and request/response schemas.
- `openapi.json` published to `docs/api/openapi.json` on every release; SDK and webapp client regenerate from it.

## Versioning of responses

- Additive changes (new optional fields, new enum values) are NOT breaking.
- Breaking changes (removed field, changed type, new required field, tightened validation) require `v2` and a 1-minor-version overlap.
- Enum consumers must tolerate unknown values (forward-compat).

## Large payloads

- Hard cap: request body 10 MB; JSON imports 50 MB via async endpoint.
- Screenshots: max 10 MB each; accepted types `image/png`, `image/jpeg`, `image/webp`.

## Logging

- Every request logs: `traceId`, `orgId`, `userId`, `method`, `path`, `status`, `durationMs`.
- Never log request bodies for authenticated endpoints. Never log `Authorization` headers, API keys, PATs, AI keys, or webhook bodies.

## Deprecation

- Deprecated endpoint returns `Deprecation: true` + `Sunset: <RFC 8594 date>` headers.
- Announced in `CHANGELOG.md` under `### Deprecated` at the release the deprecation lands; removed under `### Removed` at the sunset release.
