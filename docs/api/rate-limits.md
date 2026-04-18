# Rate limits

Translately rate-limits every request. Limits are **per-token** (JWT subject, API key, PAT, or anonymous IP) and enforced through a Redis sliding window. The goal is to keep self-hosted instances responsive under accidental loops and genuinely-hostile traffic, not to nickel-and-dime legitimate callers.

Introduced by: lays down in Phase 1 alongside the auth endpoints; takes effect as endpoints go live.

Related: [API conventions steering](../../.kiro/steering/api-conventions.md), [error codes](errors.md#rate-limiting--429), [scopes](scopes.md).

## Default limits

| Traffic class | Limit | Window |
|---|---|---|
| Authenticated read (`GET`, `HEAD`, `OPTIONS`) | **600 req/min** | 60 s sliding |
| Authenticated write (`POST`, `PUT`, `PATCH`, `DELETE`) | **120 req/min** | 60 s sliding |
| AI suggest (`POST /suggest-translation`, batch equivalents) | **60 req/min** | 60 s sliding + per-project monthly budget cap |
| Unauthenticated (signup, login, forgot-password, refresh) | **10 req/min per IP** | 60 s sliding |
| Webhook inbound (Phase 6, if enabled) | **60 req/min per webhook** | 60 s sliding |

Rate-limit state lives in Redis under the `rl:` key prefix, keyed by credential prefix + route group. Evictions run automatically via Redis TTLs; no application housekeeping required.

## Response headers

Every response (successful and rate-limited) carries:

```
X-RateLimit-Limit:     600
X-RateLimit-Remaining: 582
X-RateLimit-Reset:     17
```

- `X-RateLimit-Limit` — the current class's request cap.
- `X-RateLimit-Remaining` — requests still available in the current window.
- `X-RateLimit-Reset` — seconds until the window rolls forward.

## Retry headers

A rate-limited request returns HTTP `429 Too Many Requests` with the [error envelope](errors.md#rate-limiting--429):

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
Content-Type: application/json

{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded.",
    "details": {
      "limit": 120,
      "windowSeconds": 60,
      "retryAfterSeconds": 12
    }
  }
}
```

`Retry-After` is integer seconds, never a date (keeps parsing simple for CLIs).

## For SDKs and callers

- **Respect `Retry-After`.** Don't back off blindly; the server already tells you when the next slot opens.
- **Use a single persistent client per credential.** Short-lived curl scripts that reconnect per request hit the unauthenticated limit even when they don't mean to.
- **Batch where you can.** Write endpoints accept batch payloads (`POST /keys/bulk`, `POST /translations/bulk`) specifically so you don't have to burn a write slot per row.
- **Backoff sensibly on 5xx.** A `429` is a rate-limit signal; a `500` / `503` means retry the request later with exponential backoff.

## Operator tuning

Each limit is a config property under `translately.rate-limit.*` in [`backend/app/src/main/resources/application.yml`](../../backend/app/src/main/resources/application.yml). Override via env var:

```
TRANSLATELY_RATE_LIMIT_AUTHENTICATED_READ=1200
TRANSLATELY_RATE_LIMIT_AUTHENTICATED_WRITE=300
TRANSLATELY_RATE_LIMIT_AI_SUGGEST=120
TRANSLATELY_RATE_LIMIT_UNAUTHENTICATED=20
```

Set a limit to `0` to disable rate-limiting for that class (self-hosters on a trusted LAN sometimes want this).

Rate-limit metrics are exposed at `/q/metrics`:

- `http_server_requests_seconds_count{status="429"}` — rejected count per route group.
- `translately_rate_limit_remaining{class="…"}` — gauge of the **minimum** remaining budget across active keys per class.

## Why sliding window, not token bucket?

Token buckets are cheaper but give bursty callers a better experience than we want to advertise: a legitimate caller who paces their requests shouldn't be punished because someone else burst. The sliding window gives a fair view of the last minute at the cost of a handful of Redis commands per request — an acceptable trade on the authenticated path.

## BYOK AI budget interaction

AI Suggest requests consume two budgets:

1. **Rate limit** — 60 req/min per credential (this page).
2. **Per-project monthly cost cap** — set by `AI_CONFIG_WRITE`, enforced in service code before the provider call.

Hitting the budget cap returns `AI_BUDGET_EXCEEDED` (403) **before** any provider network call is made. The rate-limit and budget are independent; you can exhaust one without touching the other.

## See also

- Rate-limit headers on every response — not only on 429. Use them to self-pace proactively.
- [`docs/api/errors.md`](errors.md) for the full response shape.
- [Self-host hardening](../self-hosting/hardening.md) for the reverse-proxy configuration that caps payload size before it reaches Translately.
