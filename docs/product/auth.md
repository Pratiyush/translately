---
title: Authentication
parent: Product
nav_order: 3
---


# Authentication

Translately ships with a self-contained email + password authentication flow. No third-party service is required; Mailpit (dev) or any SMTP provider (prod) delivers the verification and password-reset links. Everything in this page is backed by the public REST API at `/api/v1/auth/...` so any SDK or webapp can integrate directly.

Introduced in: **v0.1.0** (Phase 1).

## In the webapp

T117 wires five public routes against these endpoints:

| Route | What it does |
|---|---|
| `/signin` | Email + password → `POST /auth/login` → stores the token pair → redirects to the original destination (if the user was bounced from a protected route) or `/`. |
| `/signup` | Full name + email + password → `POST /auth/signup` → "check your inbox" success screen. No auto-login; the account is unverified until the user clicks the emailed link. |
| `/verify-email?token=…` | Runs `POST /auth/verify-email` once on mount. Surfaces success / expired-link / consumed-link / missing-token states inline. |
| `/forgot-password` | Email → `POST /auth/forgot-password` → always "check your inbox" (the backend's anti-enumeration 202 is mirrored in the UI). |
| `/reset-password?token=…` | New password → `POST /auth/reset-password` → redirects to `/signin` on success. Every active refresh token is invalidated server-side; the user must re-login. |

### Sign in

<picture>
  <source srcset="../screenshots/signin-dark.png" media="(prefers-color-scheme: dark)">
  <img src="../screenshots/signin-light.png" alt="Sign in page — Translately logo, email + password form, primary sign-in button." />
</picture>

### Sign up

<picture>
  <source srcset="../screenshots/signup-dark.png" media="(prefers-color-scheme: dark)">
  <img src="../screenshots/signup-light.png" alt="Sign up page — full name, email, and password fields with the password strength hint." />
</picture>

### Forgot password

<picture>
  <source srcset="../screenshots/forgot-password-dark.png" media="(prefers-color-scheme: dark)">
  <img src="../screenshots/forgot-password-light.png" alt="Forgot password page — single email input, anti-enumeration 202 means we always show the same success message." />
</picture>

### Verify email (pending state)

<picture>
  <source srcset="../screenshots/verify-email-pending-dark.png" media="(prefers-color-scheme: dark)">
  <img src="../screenshots/verify-email-pending-light.png" alt="Verifying email page — spinner visible while the token is exchanged." />
</picture>

All five pages use **React Hook Form + Zod** for client-side validation (mirrors the backend's `VALIDATION_FAILED` rules so first-wrong-click feedback is instant) and **TanStack Query** mutations against the **typed `api` client** (T120). Server errors round-trip via `error.code`; localised strings live in [`webapp/src/i18n/en.json`](https://github.com/Pratiyush/translately/blob/master/webapp/src/i18n/en.json) under the `auth.error.*` namespace — unknown codes fall back to `error.message`.

Dev bundles still expose a "Seed dev user" button on `/signin` so reviewers can drop into the shell without a running backend. Production bundles strip it via `import.meta.env.DEV`.

## Flow at a glance

```
 POST /api/v1/auth/signup          ── creates an unverified user, sends verify email
 POST /api/v1/auth/verify-email    ── consumes the email-verification token
 POST /api/v1/auth/login           ── returns { accessToken, refreshToken }
 POST /api/v1/auth/refresh         ── rotates the token pair (jti single-use)
 POST /api/v1/auth/forgot-password ── always 202; sends reset email only if user exists
 POST /api/v1/auth/reset-password  ── consumes the reset token, updates the password
```

All requests and responses are `application/json`. Every non-2xx response uses the project-wide error envelope:

```json
{
  "error": {
    "code": "EMAIL_TAKEN",
    "message": "Email 'alice@example.com' is already in use.",
    "details": { ... }
  }
}
```

## 1. Sign up

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{
        "email": "alice@example.com",
        "password": "correcthorsestaple!",
        "fullName": "Alice Example"
      }'
```

Success: `201 Created` with `{ "userExternalId": "01HT..." }`. The user is created in the `users` table with `email_verified_at = null` and a verification link is dispatched via Quarkus Mailer. Log-in attempts before verification are rejected with `403 EMAIL_NOT_VERIFIED`.

Validation rules (all enforced server-side):

- email: contains an `@`, has a domain TLD, ≤254 chars
- password: 12-128 characters
- fullName: non-blank, ≤128 chars

## 2. Verify email

The email contains a link like `http://localhost:5173/verify-email?token=...`. The SPA extracts `token` and POSTs it:

```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H 'Content-Type: application/json' \
  -d '{ "token": "<raw-token-from-email>" }'
```

Tokens are single-use (replays return `409 TOKEN_CONSUMED`) and expire after 48 hours (expired replays return `401 TOKEN_EXPIRED`). Only an Argon2id hash of the raw token is stored in `email_verification_tokens` so a DB dump cannot replay the link.

## 3. Log in

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{ "email": "alice@example.com", "password": "correcthorsestaple!" }'
```

Returns:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "accessExpiresAt": "...",
  "refreshExpiresAt": "..."
}
```

The access token is a short-lived (default 15 min) RS256 JWT carrying `sub`, `upn`, `scope`, `groups`, and `orgs` claims. Present it as `Authorization: Bearer <accessToken>` on every authenticated request.

## 4. Refresh

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{ "refreshToken": "<previous-refresh-jwt>" }'
```

Refresh tokens are single-use. The `jti` claim is recorded in the `refresh_tokens` ledger; consuming it returns a fresh pair and marks the old `jti` as `consumed_at = now`. A replay of the old refresh JWT returns `401 REFRESH_TOKEN_REUSED` — the webapp should treat that as a forced logout.

## 5. Forgot / reset password

`POST /api/v1/auth/forgot-password` always returns `202 Accepted` regardless of whether the email matches a user. This prevents account-enumeration. If a matching user exists, a reset email is sent with a single-use token valid for 1 hour.

```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H 'Content-Type: application/json' \
  -d '{ "email": "alice@example.com" }'
```

Reset consumes the token and updates the password hash:

```bash
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d '{ "token": "<raw-reset-token>", "newPassword": "rotated-passphrase!" }'
```

## Error codes

| HTTP | `error.code`           | When |
|------|------------------------|------|
| 400  | `VALIDATION_FAILED`    | any field rule violated — `details.fields[]` lists them |
| 400  | `TOKEN_INVALID`        | token string doesn't match any known record |
| 401  | `INVALID_CREDENTIALS`  | login: unknown user OR wrong password |
| 401  | `TOKEN_EXPIRED`        | valid token past its TTL |
| 401  | `REFRESH_TOKEN_REUSED` | refresh JWT's `jti` was already consumed |
| 403  | `EMAIL_NOT_VERIFIED`   | login attempted before verification |
| 409  | `EMAIL_TAKEN`          | signup: email already registered |
| 409  | `TOKEN_CONSUMED`       | verify or reset token used more than once |

## Running the flow locally

```bash
docker compose up -d postgres mailpit
./gradlew :backend:app:quarkusDev
# Mailpit UI is at http://localhost:8025 — every verify/reset email lands there.
```
