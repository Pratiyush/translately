---
title: Authentication architecture
parent: Architecture
nav_order: 5
---

# Authentication architecture

This page documents how Translately authenticates callers — the JWT format, refresh-token rotation, API-key and PAT validation, and the module boundaries that keep these pieces cleanly separable.

Introduced by: [T104](https://github.com/Pratiyush/translately/issues/131) (`JwtIssuer` + `JwtAuthentication`), [T105](https://github.com/Pratiyush/translately/issues/132) (`PasswordHasher` + `TokenGenerator`), T110 (API keys + PATs), [T110-enforce](https://github.com/Pratiyush/translately/issues/149) (API-key + PAT authenticator filters).

Related docs: [data-model](data-model.md), [authorization](authorization.md), [multi-tenancy](multi-tenancy.md).

## Credential types

| Credential | Header format | Issuer | Verifier | Scope source | Identifies | TTL |
|---|---|---|---|---|---|---|
| **Access JWT** | `Authorization: Bearer <jwt>` | `JwtIssuer` | Smallrye JWT → `JwtSecurityScopesFilter` | JWT `scope` + `groups` claims | a user | ~15 min |
| **Refresh JWT** | body / cookie at `/auth/refresh` only | `JwtIssuer` | `RefreshTokenParser` | — (refresh never bears scopes) | a user's session | ~30 days |
| **API key** | `Authorization: ApiKey <prefix>.<secret>` | `ApiKeyService` (project-scoped) | `ApiKeyAuthenticator` | `api_keys.scopes` verbatim | a project | until revoked / expired |
| **Personal Access Token** | `Authorization: Bearer tr_pat_<prefix>.<secret>` | `PatService` (user-scoped) | `PatAuthenticator` | `personal_access_tokens.scopes` ∩ owner's current effective scopes | a user | until revoked / expired |
| **OIDC token** | `Authorization: Bearer <idp-jwt>` | Keycloak (Phase 7) | Quarkus OIDC | IdP groups → scopes | a user | IdP-defined |
| **LDAP bind** | basic auth | — | Quarkus Elytron LDAP | directory groups → scopes | a user | session-scoped |

Exactly one authenticator populates scopes per request. The filter chain tries each authenticator in `Priorities.AUTHENTICATION` order and the first that matches the header shape handles it; the rest return early. A request with two credentials on the same `Authorization` header is impossible (HTTP allows only one); presenting both a bearer token *and* an API-key header (say via `x-api-key`) is rejected at the HTTP layer — we never merge grants across credentials.

## JWT format

Translately uses **Smallrye JWT** with RSA-256 signing. The key pair is configured via `translately.jwt.sign-key.private` and `translately.jwt.verify-key.public` (the Quarkus defaults); operators rotate by deploying a new key pair and keeping the old public key in the verifier for one refresh-TTL window.

### Access token

| Claim | Type | Meaning |
|---|---|---|
| `iss` | string | `translately.jwt.issuer`, default `translately` |
| `aud` | string | `translately.jwt.audience`, default `translately-webapp` |
| `sub` | string | User `external_id` (ULID) |
| `upn` | string | User email (the User Principal Name) |
| `scope` | string | Space-separated scope tokens — same grammar as `Scope.serialize` |
| `groups` | string[] | Same scope tokens as a JSON array — Smallrye uses this for `@RolesAllowed` interop |
| `orgs` | object[] | `[{id, slug, role}, …]` — one entry per org the user belongs to |
| `typ` | string | `"access"` |
| `iat` / `exp` | int | Issued-at and expiry (epoch seconds). Default TTL: **15 minutes** (`translately.jwt.access-ttl = PT15M`) |

Tokens are compact-serialized; the `orgs` claim means the webapp rarely needs a second round-trip to resolve membership when deciding what to render.

### Refresh token

Minimal claim set — everything the server needs to validate one request and then rotate:

| Claim | Type | Meaning |
|---|---|---|
| `iss`, `aud`, `sub` | string | Same as access |
| `jti` | string | Cryptographically-random ULID-like token (24-byte base32); single-use |
| `typ` | string | `"refresh"` |
| `iat` / `exp` | int | Default TTL: **30 days** (`translately.jwt.refresh-ttl = P30D`) |

### Rotation and replay protection (T103)

`/api/v1/auth/refresh` performs an atomic rotation:

1. Verify the inbound refresh JWT (signature, issuer, audience, `typ=refresh`, `exp`).
2. Look up `jti` in the `refresh_tokens` ledger (table introduced by T103's `V2` migration).
3. If the row is already marked `consumed_at IS NOT NULL` → **replay attempt.** Return `REFRESH_TOKEN_REUSED` and invalidate every refresh-token row linked to the same user (forces all sessions to re-login). This is the "what if an attacker has cloned my refresh token?" answer.
4. Otherwise: stamp `consumed_at = NOW()`, mint a fresh access+refresh pair, record the new `jti`.

The ledger is the only DB read on the hot path; it carries a `(jti UNIQUE, consumed_at, user_id, expires_at)` shape.

### Bearer-credential split

`JwtSecurityScopesFilter` deliberately rejects refresh tokens on regular endpoints — refresh tokens are only valid at the `/api/v1/auth/refresh` controller. This prevents a stolen refresh token from being used to read data directly; it also means the refresh TTL can be longer than the access TTL without compromising the API surface.

### Claim reading

`JwtSecurityScopesFilter` reads the `typ` and `scope` claims with a **typed `String` generic** — `token.getClaim<String>(name)` rather than `token.getClaim<Any?>(name)?.toString()`. Smallrye stores JSON string claims as `jakarta.json.JsonString` internally, and `JsonString.toString()` returns the quoted JSON literal (for example `"access"` with the quote characters included) rather than the underlying value. Reading through the typed generic makes Smallrye unwrap the claim via its internal converter and hand back the raw `String`. The filter additionally strips a leading/trailing `"` pair from the result as a belt-and-braces guard against any code path that still surfaces a quoted value. Root cause for [issue #151](https://github.com/Pratiyush/translately/issues/151); regression guarded by `JwtSecurityScopesFilterIT` (`:backend:app`).

## API key + PAT authentication

Both credential types follow the same shape and Argon2id-hash their secrets:

- **Prefix:** a stable `tr_<kind>_<tail>` string (`tr_ak_…` for API keys, `tr_pat_…` for PATs). Stored in the `prefix` column of `api_keys` / `personal_access_tokens` with a unique index so lookup is O(1). The prefix is safe to display — it never encodes any part of the secret.
- **Secret:** 32 random bytes, base64url-encoded without padding (43 chars), **shown exactly once** at mint time. The DB stores `Argon2id(secret)` only.
- **Separator:** a single `.` between the prefix and secret on the wire. The separator lets us split the two halves cleanly even when the base64url-encoded secret contains `_` or `-`.

On request:

1. Parse the `Authorization` header. `ApiKey <prefix>.<secret>` routes to `ApiKeyAuthenticator`; `Bearer tr_pat_…` routes to `PatAuthenticator`; any other `Bearer` payload routes to `JwtSecurityScopesFilter` via the smallrye-jwt auth layer.
2. `SELECT … FROM api_keys WHERE prefix = ?` (or `personal_access_tokens` for a PAT) — unique index, O(1) lookup.
3. Compare `Argon2.verify(secret, row.secret_hash)`. A prefix miss and a bad secret collapse into the same 401 `UNAUTHENTICATED` response so attackers can't probe the prefix space.
4. If `revoked_at IS NOT NULL` → 401 `CREDENTIAL_REVOKED`. If `expires_at < now()` → 401 `CREDENTIAL_EXPIRED`.
5. On success, record `last_used_at = NOW()` (synchronous for v0.1.0 — a follow-up moves this to a Quartz-backed batch update).

Argon2id parameters (see [ADR 0001](decisions/0001-argon2id-password-hashing.md)): `iterations=3`, `memory=64 MiB`, `parallelism=4`. Same settings for user passwords.

### Scope handling

- **API key.** `api_keys.scopes` (space-separated tokens) is pushed into [`SecurityScopes`](../api/scopes.md) verbatim. The minting admin already enforced that the requested scopes were a subset of their own; at request time we trust the row. The owning organization's slug is also bound into `TenantContext` so multi-tenant filters see the right tenant when the URL path doesn't provide one.
- **PAT.** `personal_access_tokens.scopes` is **intersected with the owning user's current effective scope set** before being granted. The user's scope set is computed from their `OrganizationMember` rows via `OrgRoleScopes`: so a PAT minted while the user was ADMIN of org X, whose role has since been demoted to MEMBER, can only exercise MEMBER-level scopes going forward. This intersection runs on every request — the stored `scopes` column is an upper bound, never a grant.

### Coexistence with the JWT mechanism

Quarkus's proactive authentication layer hands every `Authorization: Bearer <x>` token to the smallrye-jwt mechanism. Smallrye-jwt treats any bearer token that isn't a parseable JWT as an authentication failure and returns 401 before JAX-RS filters run. That would short-circuit both the `ApiKey` scheme (wrong scheme — smallrye-jwt doesn't claim it, but some downstream checks still expected an auth path) and the `Bearer tr_pat_…` PAT shape (wrong JWT shape).

`NonJwtBearerAuthMechanism` (priority 2000, higher than smallrye-jwt's default 1000) intercepts exactly those two header shapes and returns a placeholder authenticated identity. It does **no** real credential verification — that happens downstream in the JAX-RS filter where it can share `SecurityScopes` with the JWT path. The mechanism exists only so proactive auth doesn't 401 a legitimate API-key or PAT request before our filter sees it. For every other header shape it defers to smallrye-jwt.

`JwtSecurityScopesFilter` is tolerant of non-JWT principals: if the active `SecurityIdentity` was produced by `NonJwtBearerAuthMechanism`, accessing the injected `JsonWebToken` throws `IllegalStateException`, which the filter catches and treats as "no JWT scopes to contribute" — the API-key / PAT authenticator owns the scope grants in that request.

## Module layout

```
:backend:security
  jwt/       JwtIssuer, JwtClaims, JwtTokens            ← T104 issue #131
  password/  PasswordHasher, TokenGenerator             ← T105 issue #132
  crypto/    CryptoService (envelope)                   ← T112 issue #136
  tenant/    TenantContext                              ← T111 issue #135
  rbac/      OrgRole, OrgRoleScopes, ScopeResolver      ← T109 issue #134
  Scope.kt, SecurityScopes.kt, RequiresScope.kt         ← T108 issue #133

:backend:service
  credentials/ ApiKeyService, PatService                ← T110 issue #28
               CredentialAuthenticator                  ← T110-enforce issue #149

:backend:api
  tenant/    TenantRequestFilter                        ← T111 issue #135
  security/  NonJwtBearerAuthMechanism                  ← T110-enforce issue #149
             JwtSecurityScopesFilter
             ApiKeyAuthenticator                        ← T110-enforce
             PatAuthenticator                           ← T110-enforce
             ScopeAuthorizationFilter                   ← T108
             InsufficientScopeException + mapper
```

## Request lifecycle

```
Request arrives
        │
        ▼
┌───────────────────────────────────────────┐
│ Quarkus proactive auth                    │
│  • NonJwtBearerAuthMechanism (priority 2000)                                             │
│     · Authorization: ApiKey ...  → placeholder identity, falls through                   │
│     · Authorization: Bearer tr_pat_... → placeholder identity, falls through             │
│     · anything else → defer to JWTAuthMechanism                                          │
│  • JWTAuthMechanism (priority 1000)                                                      │
│     · Authorization: Bearer <jwt> → parsed JsonWebToken in SecurityIdentity              │
└───────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│ JAX-RS request filters                    │
│  • TenantRequestFilter (AUTHENTICATION - 100)                                            │
│     · parses /api/v1/organizations/<id>/... into TenantContext                            │
│  • ApiKeyAuthenticator  (AUTHENTICATION) — claims ApiKey header, populates scopes         │
│  • PatAuthenticator     (AUTHENTICATION) — claims Bearer tr_pat_ header, populates scopes │
│  • JwtSecurityScopesFilter (AUTHENTICATION) — reads JWT claims, populates scopes          │
│  • TestScopeHeaderFilter (AUTHENTICATION, test-only) — X-Test-Scopes header               │
│     · All four run at the same priority; each short-circuits on header-shape mismatch so  │
│       at most one populates SecurityScopes per request.                                   │
│  • ScopeAuthorizationFilter (AUTHORIZATION) — enforces @RequiresScope                     │
└───────────────────────────────────────────┘
        │
        ▼
Resource method runs (or 401 / 403 rendered)
```

## Test coverage

- `JwtIssuerIT` / `JwtAuthenticationIT` (in `:backend:app`) — round-trip signed JWTs against a running Quarkus instance; assert every claim field and every rejection path.
- `JwtSecurityScopesFilterIT` (`:backend:app`) — regression for [issue #151](https://github.com/Pratiyush/translately/issues/151): mints a JWT via `JwtIssuer`, presents it on a `@RequiresScope` probe endpoint, and asserts the filter unwraps `JsonString` claims correctly so `SecurityScopes.granted` is populated.
- `ApiKeyAuthenticatorIT` / `PatAuthenticatorIT` — mint real credentials via `ApiKeyService` / `PatService`, present them on a probe endpoint, and assert the full chain (parse → Argon2id verify → revocation / expiry check → scope grant → `@RequiresScope` enforce). Covers: happy path, revoked, expired, bad secret, unknown prefix, malformed token, other-scheme header ignored, cross-org PAT scope intersection (MEMBER cannot exercise ADMIN-level scope).
- `PasswordHasherTest` (`:backend:security`) — verifies Argon2id parameter constants, round-trip hash+verify, wrong-password rejection, and malformed-hash graceful failure.
- `CryptoServiceTest` — envelope layout, tamper detection, KEK-size validation.
- `ScopeResolverTest` / `OrgRoleScopesTest` — role-to-scope mapping invariants.

Integration tests run under `./gradlew :backend:app:test`; unit tests under `./gradlew :backend:security:test` and don't require Docker.
