---
title: API keys & Personal Access Tokens
parent: Product
nav_order: 4
---

# API keys & Personal Access Tokens

Translately ships two long-lived credential types for server-to-server and CLI use:

| Credential | Scope of ownership | Used by |
|---|---|---|
| **API key** | a single project | CI jobs, deploy pipelines, anything that acts on behalf of a project |
| **Personal Access Token (PAT)** | a single user, across every project they belong to | the CLI, personal scripts, integrations that act "as the user" |

Both are minted from the Translately REST API (UI lands later in Phase 1). Secrets are **shown exactly once** at mint time, stored only as Argon2id hashes, and can be revoked at any time without affecting other credentials.

Introduced by: [T110](https://github.com/Pratiyush/translately/issues/28) · Ships in `v0.1.0`.

Related: [API auth endpoints](../api/auth.md), [scopes](../api/scopes.md), [error codes](../api/errors.md).

## Token format

Both credential types share the same shape:

```
tr_<kind>_<8-char-prefix>.<43-char-secret>
└───── public prefix ──────┘ └──── secret ────┘
```

- `tr_ak_…` — API key (project-scoped)
- `tr_pat_…` — Personal Access Token (user-scoped)

The **public prefix** is stored in the database and shown in listings so you can recognise your keys at a glance. The **secret** half is Argon2id-hashed before persistence; the plaintext is only in the response to the mint call.

A full token looks like:

```
tr_ak_k9c4n2xb.a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8s9T0u1V
└── prefix ──┘ └─────────────── secret (43 chars) ────────┘
```

Present it on API requests as:

```
Authorization: ApiKey tr_ak_k9c4n2xb.a1B2c3D4…
Authorization: Bearer tr_pat_k9c4n2xb.a1B2c3D4…
```

## Minting an API key

`POST /api/v1/projects/{projectId}/api-keys` — requires the `api-keys.write` scope in the project's organization.

```bash
curl -X POST https://your-host/api/v1/projects/01HT…/api-keys \
  -H "Authorization: Bearer $ACCESS_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "name":   "CI publisher",
    "scopes": ["keys.read", "keys.write", "translations.write", "imports.write"]
  }'
```

Successful response (`201 Created`):

```json
{
  "id":        "01HT…",
  "prefix":    "tr_ak_k9c4n2xb",
  "secret":    "tr_ak_k9c4n2xb.a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8s9T0u1V",
  "name":      "CI publisher",
  "scopes":    ["imports.write", "keys.read", "keys.write", "translations.write"],
  "expiresAt": null,
  "createdAt": "2026-04-18T10:45:00Z"
}
```

**Save `secret` now.** The server will never show it again. Store it in your CI provider's secret vault (GitHub Actions secret, GitLab CI variable, HashiCorp Vault, …).

### Scope intersection

You can only mint a key with scopes **you already hold**. Asking for a scope outside your current set returns:

```
403 Forbidden
{
  "error": {
    "code": "SCOPE_ESCALATION",
    "details": {
      "requested": ["audit.read", "keys.write"],
      "held":      ["keys.write"],
      "missing":   ["audit.read"]
    }
  }
}
```

A MEMBER minting an API key can only pass the MEMBER scope set. An ADMIN can pass any ADMIN scope. This rule keeps API keys from becoming an escalation vector.

### Optional expiry

Pass `expiresAt` (ISO-8601 UTC) to mint a key that self-revokes after the given time:

```json
{
  "name": "1-day CI token",
  "scopes": ["keys.read", "keys.write"],
  "expiresAt": "2026-04-19T10:00:00Z"
}
```

## Listing API keys

`GET /api/v1/projects/{projectId}/api-keys` — requires `api-keys.read`.

```json
{
  "data": [
    {
      "id":         "01HT…",
      "prefix":     "tr_ak_k9c4n2xb",
      "name":       "CI publisher",
      "scopes":     ["imports.write", "keys.read", "keys.write", "translations.write"],
      "expiresAt":  null,
      "lastUsedAt": "2026-04-17T22:14:00Z",
      "revokedAt":  null,
      "createdAt":  "2026-04-18T10:45:00Z"
    }
  ]
}
```

Secrets are **never** in listings — only the public prefix. If you've lost the secret, revoke this key and mint a fresh one.

## Revoking an API key

`DELETE /api/v1/projects/{projectId}/api-keys/{keyId}` — requires `api-keys.write`. Returns `204 No Content`.

- **Idempotent.** Revoking a revoked key is a no-op — still `204`, no error.
- **Immediate.** Once revoked, the key fails authentication on the next request.

## Personal Access Tokens

Same shape, different audience. PATs belong to a **user** and span every project that user is a member of. They're what you'd use for a personal CLI setup or a one-off integration where a user's identity makes more sense than a project identity.

### Minting a PAT

`POST /api/v1/users/me/pats` — no scope required beyond a valid access JWT; users can always manage their own credentials.

```bash
curl -X POST https://your-host/api/v1/users/me/pats \
  -H "Authorization: Bearer $ACCESS_JWT" \
  -H "Content-Type: application/json" \
  -d '{"name":"laptop cli", "scopes":["keys.read", "keys.write"]}'
```

Response is identical in shape to the API-key mint, except the prefix is `tr_pat_…`.

Same **scope intersection** rule: the PAT's scopes must be a subset of the caller's JWT scopes.

### Listing / revoking PATs

- `GET /api/v1/users/me/pats` — list your own PATs (summaries only).
- `DELETE /api/v1/users/me/pats/{patId}` — revoke one of your PATs.

Trying to revoke someone else's PAT returns `404 NOT_FOUND` — the server never discloses whether the referenced PAT exists.

## Operational guidance

- **Rotate on a schedule.** Mint a new key, roll the CI secret, revoke the old. There's no "rotate-in-place" endpoint — the one-time-secret model makes a clean rotation trivially easier than a hot rename.
- **Prefer short-lived keys where possible.** Pass `expiresAt` in the CI flow so abandoned branches don't leave stale long-lived credentials behind.
- **Least-scoped keys.** A CI job that only publishes translations doesn't need `project-settings.write`. Grant the minimum.
- **Detect compromise.** Watch `lastUsedAt` in the UI (arrives in Phase 1's webapp). A key that hasn't been used in months + an unexpected `lastUsedAt` bump → revoke.

## Authentication on protected endpoints

Authentication is **live** — both credential types are accepted on every protected endpoint alongside access JWTs. Present the full token exactly as it was returned at mint time:

```bash
# API key
curl -H "Authorization: ApiKey tr_ak_k9c4n2xb.a1B2c3D4…" \
     https://your-host/api/v1/projects/01HT…/keys

# Personal Access Token
curl -H "Authorization: Bearer tr_pat_k9c4n2xb.a1B2c3D4…" \
     https://your-host/api/v1/organizations/acme/projects
```

The backend dispatches on the header shape:

- `Authorization: ApiKey <token>` → API-key authenticator; scopes taken from the stored `api_keys.scopes` column.
- `Authorization: Bearer tr_pat_<token>` → PAT authenticator; scopes intersected with the owning user's **current** effective scopes (see below).
- `Authorization: Bearer <jwt>` (anything else) → normal JWT access-token flow.

Every request is scoped by exactly one credential. Presenting two credentials on the same request (e.g. a JWT header plus an API-key query parameter) is refused at the HTTP layer — there's no merging of grants.

### PAT scope intersection at request time

The scopes a PAT was minted with are an **upper bound**. On every request the authenticator recomputes the owning user's effective scope set from their current `OrganizationMember` rows and intersects. Practical consequence:

- Mint a PAT with `keys.write translations.write` while you're an ADMIN of org X.
- You are demoted to MEMBER of org X (ADMIN is a superset of MEMBER, and MEMBER *does* hold `keys.write` + `translations.write`) — the PAT keeps working.
- You are demoted to MEMBER of org Y where you originally held ADMIN, and the PAT also carried `api-keys.write` — `api-keys.write` is ADMIN-only, so that scope is dropped from the request's effective set, and any endpoint that requires it will 403. Other scopes the MEMBER still holds continue to work.
- You are removed from every org you belong to → the PAT's effective scope set collapses to empty, every protected endpoint returns 403. Revoke the PAT if you want a cleaner "not authenticated" answer.

API keys don't re-intersect — they're project-scoped, and the minting admin already enforced intersection at issue time. Revocation or a past `expires_at` is the only way to cut an API key off.

### Failure modes

| HTTP | `error.code` | Meaning |
|---|---|---|
| 401 | `UNAUTHENTICATED` | Unknown prefix, bad secret, or malformed token. Intentionally indistinguishable so attackers can't probe the prefix space. |
| 401 | `CREDENTIAL_REVOKED` | `revoked_at` has been stamped on the row. |
| 401 | `CREDENTIAL_EXPIRED` | `expires_at` has passed. |
| 403 | `INSUFFICIENT_SCOPE` | Credential is valid but lacks the scope(s) the endpoint requires. |

Introduced by: [T110-enforce](https://github.com/Pratiyush/translately/issues/149).

## Error-code reference

| HTTP | `error.code` | When |
|---|---|---|
| `201` | — | Credential minted successfully |
| `200` | — | Listing returned |
| `204` | — | Revoke succeeded (or was already revoked) |
| `400` | `VALIDATION_FAILED` | Missing name, empty scopes, past expiry |
| `400` | `UNKNOWN_SCOPE` | A requested scope token isn't in [Scope](../api/scopes.md) |
| `401` | `UNAUTHENTICATED` | No credential on the request, unknown prefix, or bad secret |
| `401` | `CREDENTIAL_REVOKED` | API key / PAT exists and secret matches, but `revoked_at` is set |
| `401` | `CREDENTIAL_EXPIRED` | API key / PAT exists and secret matches, but `expires_at` has passed |
| `403` | `SCOPE_ESCALATION` | Mint request asked for a scope the caller doesn't hold |
| `403` | `INSUFFICIENT_SCOPE` | Valid credential, but the scope required by the endpoint isn't in its effective set |
| `404` | `NOT_FOUND` | Project / PAT / API key not found (or not owned by caller) |

See the [full catalogue](../api/errors.md) for response envelopes.

## Changelog

Shipped in [Unreleased](https://github.com/Pratiyush/translately/blob/master/CHANGELOG.md) (Phase 1, T110). Lands with `v0.1.0`.
