# Authentication architecture

This page documents how Translately authenticates callers — the JWT format, refresh-token rotation, API-key and PAT validation, and the module boundaries that keep these pieces cleanly separable.

Introduced by: [T104](https://github.com/Pratiyush/translately/issues/131) (`JwtIssuer` + `JwtAuthentication`), [T105](https://github.com/Pratiyush/translately/issues/132) (`PasswordHasher` + `TokenGenerator`), T110 (API keys + PATs).

Related docs: [data-model](data-model.md), [authorization](authorization.md), [multi-tenancy](multi-tenancy.md).

## Credential types

| Credential | Issuer | Verifier | Identifies | Used by |
|---|---|---|---|---|
| **Access JWT** | `JwtIssuer` | Smallrye JWT | a user | browser session, webapp, short-lived scripts |
| **Refresh JWT** | `JwtIssuer` | `RefreshTokenParser` | a user's session | `/api/v1/auth/refresh` only |
| **API key** | T110 (org-level) | `ApiKeyAuthenticator` | a project | server-to-server, CI, CLI |
| **Personal Access Token (PAT)** | T110 (user-level) | `PatAuthenticator` | a user | long-lived personal scripts |
| **OIDC token** | Keycloak (Phase 7) | Quarkus OIDC | a user | enterprise SSO |
| **LDAP bind** | Elytron (Phase 7) | Quarkus Elytron LDAP | a user | on-prem directory auth |

Exactly one authenticator runs per request. The filter chain tries them in order (JWT → API key → PAT → OIDC) and stops on the first that matches; a request arriving with both an `Authorization: Bearer <jwt>` header and an API key in a query param is rejected rather than interpreted either way.

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

## API key + PAT authentication

Both credential types follow the same shape and Argon2id-hash their secrets:

- **Prefix:** 16 lowercase base32 chars, the only part ever shown twice (once at issue, once in the API-key listing UI). Humans can recognise their keys at a glance; the backend uses the prefix to find the row without scanning.
- **Secret:** 32 random bytes, base32-encoded, **shown exactly once** at mint time. The DB stores `Argon2id(secret)` only.

On request:

1. Parse `Authorization: Bearer <prefix>_<secret>` (or an `x-api-key` header for convenience).
2. `SELECT … FROM api_keys WHERE prefix = ?` — unique index, O(1) lookup.
3. Compare `Argon2.verify(secret, row.secret_hash)`. If the hash parameters lag the current defaults, the service layer may re-hash on success (progressive hardening).
4. If `revoked_at IS NOT NULL` or `expires_at < now()` → 401. Otherwise record `last_used_at = NOW()` (best-effort async) and continue.

Argon2id parameters (see [ADR 0001](decisions/0001-argon2id-password-hashing.md)): `iterations=3`, `memory=64 MiB`, `parallelism=4`. Same settings for user passwords.

PATs identify a **user**, so their scopes are intersected with the user's effective scope set at the time the request runs. An API key identifies a **project** and can carry any scopes the minting admin is allowed to delegate.

## Module layout

```
:backend:security
  jwt/       JwtIssuer, JwtClaims, JwtTokens            ← T104 issue #131
  password/  PasswordHasher, TokenGenerator             ← T105 issue #132
  crypto/    CryptoService (envelope)                   ← T112 issue #136
  tenant/    TenantContext                              ← T111 issue #135
  rbac/      OrgRole, OrgRoleScopes, ScopeResolver      ← T109 issue #134
  Scope.kt, SecurityScopes.kt, RequiresScope.kt         ← T108 issue #133

:backend:api
  tenant/    TenantRequestFilter                        ← T111 issue #135
  security/  JwtSecurityScopesFilter
             ScopeAuthorizationFilter                   ← T108
             InsufficientScopeException + mapper
```

## Test coverage

- `JwtIssuerIT` / `JwtAuthenticationIT` (in `:backend:app`) — round-trip signed JWTs against a running Quarkus instance; assert every claim field and every rejection path.
- `PasswordHasherTest` (`:backend:security`) — verifies Argon2id parameter constants, round-trip hash+verify, wrong-password rejection, and malformed-hash graceful failure.
- `CryptoServiceTest` — envelope layout, tamper detection, KEK-size validation.
- `ScopeResolverTest` / `OrgRoleScopesTest` — role-to-scope mapping invariants.

Integration tests run under `./gradlew :backend:app:test`; unit tests under `./gradlew :backend:security:test` and don't require Docker.
