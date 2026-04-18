---
title: Authentication endpoints
parent: API reference
nav_order: 5
---

# API — authentication

This page covers **HTTP-level** authentication — the endpoints, the headers, and the payload shapes. For the design-level view (why rotation, why Argon2id, credential-type tradeoffs), see [auth architecture](../architecture/auth.md).

Introduced by: [T103](https://github.com/Pratiyush/translately/issues/21) (email + password + verify + refresh), [T104](https://github.com/Pratiyush/translately/issues/22) (JWT issuer), [T110](https://github.com/Pratiyush/translately/issues/28) (API keys + PATs).

Related: [scopes](scopes.md), [errors](errors.md), [auth architecture](../architecture/auth.md).

## Credential types

Translately accepts four kinds of credential on an authenticated request:

| Credential | Header | Token prefix | Identifies | TTL |
|---|---|---|---|---|
| Access JWT | `Authorization: Bearer <jwt>` | — | a user | ~15 min |
| API key | `Authorization: ApiKey <prefix>_<secret>` | `tr_apikey_…` | a project | until revoked / expired |
| PAT | `Authorization: Bearer tr_pat_<prefix>_<secret>` | `tr_pat_…` | a user | until revoked / expired |
| OIDC (Phase 7) | `Authorization: Bearer <idp-jwt>` | — | a user | IdP-defined |

A request carrying **more than one** credential is rejected — exactly one authenticator runs per request. The prefix (`tr_apikey_…` vs `tr_pat_…` vs a vanilla JWT) tells the filter which branch to take.

## Endpoints

All endpoints live under `/api/v1/auth/`. No scope is required to call them — they're the pre-auth surface.

### `POST /api/v1/auth/signup`

Create a new user. Always returns 202 (see the forgot-password note below for the enumeration-avoidance rationale).

```http
POST /api/v1/auth/signup
Content-Type: application/json

{
  "email":    "me@example.com",
  "password": "correct horse battery staple",
  "fullName": "Me"
}

HTTP/1.1 202 Accepted
```

- Side-effect: sends a verify-email message via Quarkus Mailer → Mailpit in dev.
- The verify link embeds a single-use Argon2id-hashed token; clicking it hits `POST /auth/verify-email`.
- Validation errors (too-short password, malformed email) return `VALIDATION_FAILED` (400).

### `POST /api/v1/auth/verify-email`

Consume the verification token from the email. On success, stamps `users.email_verified_at = NOW()`.

```http
POST /api/v1/auth/verify-email
Content-Type: application/json

{ "token": "<opaque>" }

HTTP/1.1 204 No Content
```

- Wrong / expired token → `INVALID_CREDENTIALS` (401).

### `POST /api/v1/auth/login`

Exchange email + password for an access + refresh pair.

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "me@example.com", "password": "correct horse battery staple" }

HTTP/1.1 200 OK
Content-Type: application/json

{
  "accessToken":       "eyJ...",
  "accessExpiresAt":   "2026-04-18T11:00:00Z",
  "refreshToken":      "eyJ...",
  "refreshExpiresAt":  "2026-05-18T10:45:00Z"
}
```

- The refresh token is **also** set as an `HttpOnly; Secure; SameSite=Lax` cookie named `tr_refresh` for browser clients. CLI / server clients use the JSON body.
- Wrong credentials → `INVALID_CREDENTIALS` (401). Unverified email → `EMAIL_NOT_VERIFIED` (403).

### `POST /api/v1/auth/refresh`

Rotate the refresh token. **Single-use** — presenting a refresh token that was already consumed invalidates every refresh token for that user (session-wide kill switch on suspected replay).

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "eyJ..." }

HTTP/1.1 200 OK
{
  "accessToken":      "eyJ...",
  "accessExpiresAt":  "2026-04-18T11:15:00Z",
  "refreshToken":     "eyJ...",
  "refreshExpiresAt": "2026-05-18T11:00:00Z"
}
```

- Replay / already-consumed → `REFRESH_TOKEN_REUSED` (401). See [auth architecture](../architecture/auth.md#rotation-and-replay-protection-t103) for the full flow.
- Missing / invalid signature → `TOKEN_INVALID` (401).
- Past `exp` → `TOKEN_EXPIRED` (401).

Browser clients may send the refresh token via the `tr_refresh` cookie instead of the body; the endpoint accepts whichever is present (not both).

### `POST /api/v1/auth/forgot-password`

Start the password-reset flow. **Always returns 202**, regardless of whether the email exists — this is deliberate so an attacker cannot enumerate valid accounts.

```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{ "email": "me@example.com" }

HTTP/1.1 202 Accepted
```

- Side-effect: if the email matches a user, sends a reset email with a single-use, Argon2id-hashed token.
- No error responses (rate-limited like every unauthenticated endpoint — see [rate-limits](rate-limits.md)).

### `POST /api/v1/auth/reset-password`

Consume the reset token and set a new password.

```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{ "token": "<opaque>", "newPassword": "new correct horse battery staple" }

HTTP/1.1 204 No Content
```

- Invalid / expired / already-used token → `INVALID_CREDENTIALS` (401).
- Weak password (policy: ≥ 12 chars) → `VALIDATION_FAILED` (400).
- On success, every active refresh token for the user is invalidated — the user must re-login.

## JWT structure

Access tokens are compact-serialized, RS256-signed JWTs. Claims:

| Claim | Type | Meaning |
|---|---|---|
| `iss` | string | `"translately"` by default |
| `aud` | string | `"translately-webapp"` by default |
| `sub` | string | user ULID |
| `upn` | string | user email (the User Principal Name) |
| `scope` | string | space-separated scope tokens |
| `groups` | string[] | same scope tokens, array form |
| `orgs` | object[] | `[{id, slug, role}, …]` |
| `typ` | `"access"` | distinguishes from refresh |
| `iat` / `exp` | int | epoch seconds |

Refresh tokens carry only `iss`, `aud`, `sub`, `jti`, `typ="refresh"`, `iat`, `exp`.

See [auth architecture](../architecture/auth.md#jwt-format) for the full schema and rotation flow.

## Using a credential on a protected endpoint

```bash
curl -H "Authorization: Bearer $ACCESS" \
     https://api.example.com/api/v1/organizations/acme/projects

# API key variant
curl -H "Authorization: ApiKey tr_apikey_abc_1234..." \
     https://api.example.com/api/v1/organizations/acme/projects
```

Responses always carry [rate-limit headers](rate-limits.md#response-headers), and on 403 emit the [`INSUFFICIENT_SCOPE` envelope](errors.md#insufficient_scope) plus `WWW-Authenticate: Bearer error="insufficient_scope" scope="…"` when the caller authenticated via bearer.

## Minting API keys and PATs

The mint endpoint is authenticated — only an owner / admin can mint API keys; any user can mint a PAT for themselves. Scopes on the new credential are **intersected** with the caller's current scope set (you can't mint something you don't hold).

Both flows return the full secret **exactly once**, in the `201 Created` response. The secret is Argon2id-hashed before persistence; there is no "reveal" endpoint.

```http
POST /api/v1/organizations/acme/api-keys
Authorization: Bearer <access-jwt>
Content-Type: application/json

{
  "name":   "CI publisher",
  "scopes": ["keys.read", "keys.write", "translations.write", "imports.write"]
}

HTTP/1.1 201 Created
Location: /api/v1/organizations/acme/api-keys/01HT...
Content-Type: application/json

{
  "id":     "01HT...",
  "prefix": "tr_apikey_9zF4n6",
  "secret": "tr_apikey_9zF4n6_aBcDeFgHiJkLmN...",
  "scopes": ["keys.read", "keys.write", "translations.write", "imports.write"],
  "createdAt": "2026-04-18T10:45:00Z"
}
```

Save the `secret` somewhere safe — the server never shows it again.

## OpenAPI

The authoritative machine-readable spec is at [`openapi.json`](openapi.json). Every endpoint on this page carries its `@Operation` + `@APIResponses` annotations; regenerating the spec is part of every API PR (T113).
