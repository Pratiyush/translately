# Error-code catalogue

Every 4xx / 5xx response from the Translately API carries a uniform envelope. `error.code` is **stable across minor versions** — CLIs, SDKs, and the webapp match on it rather than parsing the human-readable message.

Introduced by: [T108](https://github.com/Pratiyush/translately/issues/133) (scope authorization + envelope).

Related: [API conventions steering](../../.kiro/steering/api-conventions.md), [scopes](scopes.md), [auth](auth.md).

## Envelope shape

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

- **`code`** — `SCREAMING_SNAKE_CASE`, stable, machine-readable. Never renamed; new codes are added, old codes are deprecated.
- **`message`** — human-readable English. The webapp i18ns from `code`, never from `message`.
- **`details`** — optional structured context. Shape varies per code; documented below.
- **`traceId`** — request id, always present, matches the server log entry. Pass this back when reporting issues.

Content type: `application/json; charset=utf-8`. HTTP status is driven by the code (see the rightmost column below).

## Catalogue

### Authentication — 401

| Code | Meaning | Typical `details` | Introduced |
|---|---|---|---|
| `UNAUTHENTICATED` | No credential on the request | — | Phase 1 |
| `INVALID_CREDENTIALS` | Wrong email + password, bad API key, or expired PAT | — | Phase 1 / T103 |
| `TOKEN_EXPIRED` | Access JWT or refresh JWT past `exp` | `{ "expiredAt": "2026-04-18T10:00:00Z" }` | Phase 1 / T104 |
| `TOKEN_INVALID` | Signature mismatch or malformed JWT | — | Phase 1 / T104 |
| `REFRESH_TOKEN_REUSED` | Replay of a consumed refresh token — invalidates **every** refresh token belonging to the user | `{ "userId": "…" }` | Phase 1 / T103 |
| `EMAIL_NOT_VERIFIED` | Valid credential but the user hasn't clicked the verify link | `{ "email": "…" }` | Phase 1 / T103 |

### Authorization — 403

| Code | Meaning | Typical `details` | Introduced |
|---|---|---|---|
| `INSUFFICIENT_SCOPE` | Authenticated but missing a required scope | `{ "required": […], "missing": […] }` | Phase 1 / T108 |
| `FORBIDDEN` | Generic forbidden — caller has every required scope but the resource-level policy rejected (e.g. cross-org access) | — | Phase 1 |

#### `INSUFFICIENT_SCOPE` in detail

Exact response body from [`InsufficientScopeExceptionMapper`](../../backend/api/src/main/kotlin/io/translately/api/security/InsufficientScopeExceptionMapper.kt):

```json
{
  "error": {
    "code": "INSUFFICIENT_SCOPE",
    "message": "Missing required scope(s): keys.write",
    "details": {
      "required": ["keys.read", "keys.write"],
      "missing":  ["keys.write"]
    }
  }
}
```

- HTTP status: **403 Forbidden**.
- `Content-Type: application/json`.
- The [OAuth 2.0 `WWW-Authenticate: Bearer error="insufficient_scope" scope="…"` header](https://www.rfc-editor.org/rfc/rfc6750#section-3) is emitted by the filter when the caller authenticated via bearer JWT, so compliant OAuth clients can observe it.
- Both `required` and `missing` are sorted alphabetically for deterministic diffs in logs / tests.

### Validation — 400 / 422

| Code | Meaning | Typical `details` | Introduced |
|---|---|---|---|
| `VALIDATION_FAILED` | Field-level validation errors on the request body | `{ "fields": [{ "path": "body.email", "code": "REQUIRED" }, …] }` | Phase 1 |
| `PAGE_TOO_LARGE` | `limit` exceeds 200 | `{ "limit": 500, "max": 200 }` | Phase 1 |
| `INVALID_SORT_FIELD` | `sort=field` references a non-whitelisted field | `{ "field": "createdAt" }` | Phase 1 |
| `ICU_MESSAGE_INVALID` | Semantic validation failure from `icu4j` (422) | `{ "position": 17, "reason": "Expected '}'" }` | Phase 2 |
| `MALFORMED_JSON` | Request body isn't valid JSON (400) | — | Phase 1 |

### Resource state — 404 / 409 / 410

| Code | Meaning | Typical `details` |
|---|---|---|
| `NOT_FOUND` | Resource not found, **or** auth prevents disclosing existence | — |
| `KEY_NAME_TAKEN` | Unique constraint: key name already exists in this namespace | `{ "keyName": "…" , "namespaceId": "…" }` |
| `ORG_SLUG_TAKEN` | Org slug already used globally | `{ "slug": "…" }` |
| `PROJECT_SLUG_TAKEN` | Project slug already used in this org | `{ "slug": "…", "orgId": "…" }` |
| `VERSION_CONFLICT` | Optimistic-locking: stored version differs from submitted | `{ "expected": 5, "actual": 7 }` |
| `GONE` | Resource soft-deleted and past retention (410) | — |

### Rate-limiting — 429

| Code | Meaning | Typical `details` |
|---|---|---|
| `RATE_LIMIT_EXCEEDED` | Per-token sliding-window cap hit | `{ "limit": 120, "windowSeconds": 60, "retryAfterSeconds": 12 }` |

Every 429 response carries the [`Retry-After` header](rate-limits.md#retry-headers).

### Server / dependency — 500 / 503

| Code | Meaning | Typical `details` |
|---|---|---|
| `INTERNAL_ERROR` | Unhandled exception; always logs a stack trace with `traceId` | — |
| `DEPENDENCY_UNAVAILABLE` | Redis / S3 / Mailpit / DB unreachable | `{ "dependency": "redis" }` |

## Never-log rule

The uniform envelope **never** includes:

- `Authorization` header values, API keys, PATs, refresh tokens, password hashes, BYOK AI keys.
- Request bodies for authenticated endpoints.
- Webhook bodies.

See the steering rule in [`.kiro/steering/api-conventions.md`](../../.kiro/steering/api-conventions.md#logging).

## For SDK authors

- Expose `error.code` as a typed enum; tolerate unknown values (forward-compat).
- Surface `error.message` to humans untranslated; localise via the `code` using your own catalogue.
- Surface `error.details` in its structured form — don't try to flatten.
- On `RATE_LIMIT_EXCEEDED` / `DEPENDENCY_UNAVAILABLE`, implement exponential backoff; honour `Retry-After`.
- On `REFRESH_TOKEN_REUSED`, invalidate local state and force a re-login — this is a security signal, not a retryable error.

## Deprecation

Deprecated codes return with the body unchanged plus response headers:

```
Deprecation: true
Sunset: Sat, 01 Nov 2026 00:00:00 GMT
Link: <https://github.com/Pratiyush/translately/blob/master/docs/api/errors.md#deprecated>; rel="deprecation"
```

Announced in [`CHANGELOG.md`](../../CHANGELOG.md) under `### Deprecated` at the release the deprecation lands, moved to `### Removed` at the sunset release.
