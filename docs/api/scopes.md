---
title: Scopes
parent: API reference
nav_order: 1
---

# Permission scopes

Every protected endpoint in the Translately API declares the scope(s) a caller must hold. Scopes are the atomic unit of authorization — API keys and PATs carry scope sets directly, user JWTs derive them from organization-role membership.

Introduced by: [T108](https://github.com/Pratiyush/translately/issues/133) (scope enum + `@RequiresScope` + filter), [T109](https://github.com/Pratiyush/translately/issues/27) (role → scope resolver).

Related: [authorization architecture](../architecture/authorization.md), [error codes](errors.md).

## Naming

Each scope is a dotted, lowercase token: `<domain>.<action>` where `<action>` is `read` or `write` (with one exception: `ai.suggest`).

- `write` **implies** `read` at resolver time: `keys.write` passes a `keys.read` check.
- Scopes are **stable across minor versions**. Add new ones, deprecate old ones, remove one minor later. Never rename.
- On the wire, scopes serialize as a **space-separated string** (the same grammar OAuth 2.0 uses):
  - In JWTs — the `scope` claim (`"scope": "keys.read keys.write translations.write"`) plus a mirrored `groups` array for `@RolesAllowed` interop.
  - In API keys and PATs — the `api_keys.scopes` / `personal_access_tokens.scopes` `VARCHAR(512)` column.

## Catalogue

The full 31-token catalogue is defined in [`io.translately.security.Scope`](https://github.com/Pratiyush/translately/blob/master/backend/security/src/main/kotlin/io/translately/security/Scope.kt). Tokens are grouped by domain:

### Organization + membership

| Token | Purpose | Introduced |
|---|---|---|
| `org.read` | Read organization metadata | Phase 1 |
| `org.write` | Rename / update organization | Phase 1 |
| `members.read` | List organization members | Phase 1 |
| `members.write` | Invite / remove members, change roles | Phase 1 |
| `api-keys.read` | List API keys (prefix + metadata only) | Phase 1 |
| `api-keys.write` | Mint / revoke API keys | Phase 1 |
| `audit.read` | Read audit log entries | Phase 7 |

### Project-wide

| Token | Purpose | Introduced |
|---|---|---|
| `projects.read` | List and read projects in an org | Phase 1 |
| `projects.write` | Create / archive projects | Phase 1 |
| `project-settings.write` | Rename / reconfigure / delete a project | Phase 1 |

### Keys + translations (Phase 2)

| Token | Purpose |
|---|---|
| `keys.read` | List keys, read metadata |
| `keys.write` | Create / edit / delete keys, namespaces, tags |
| `translations.read` | Read translation values |
| `translations.write` | Author / edit translations |

### Imports + exports (Phase 3)

| Token | Purpose |
|---|---|
| `imports.write` | Upload, preview, and run JSON imports |
| `exports.read` | Generate export bundles |

### AI / MT + TM (Phase 4)

| Token | Purpose |
|---|---|
| `ai.suggest` | Invoke AI-suggest on a key or batch (BYOK) |
| `ai-config.write` | Configure provider, model, key, budget |
| `tm.read` | Read translation-memory matches |
| `glossaries.read` | Read glossary entries |
| `glossaries.write` | Create / edit glossary entries |

### Screenshots (Phase 5)

| Token | Purpose |
|---|---|
| `screenshots.read` | Read screenshots pinned to keys |
| `screenshots.write` | Upload + pin screenshots |

### Webhooks + CDN (Phase 6)

| Token | Purpose |
|---|---|
| `webhooks.read` | Read webhook configs + delivery log |
| `webhooks.write` | Create / edit / disable webhooks |
| `cdn.read` | Read CDN bundle config + URLs |
| `cdn.write` | Configure CDN content |

### Tasks + branching (Phase 7)

| Token | Purpose |
|---|---|
| `tasks.read` | Read translation tasks |
| `tasks.write` | Create / assign / close tasks |
| `branches.read` | Read translation branches |
| `branches.write` | Create / merge / delete branches |

## Role → scope mapping

The three built-in organization roles map to curated scope sets via `ScopeResolver`:

| Role | Scope set |
|---|---|
| **OWNER** | every scope in the catalogue — new scopes default to OWNER so we never forget to grant them |
| **ADMIN** | OWNER minus `project-settings.write`, `ai-config.write`, `api-keys.write` (retains `audit.read`) |
| **MEMBER** | every `*.read` scope plus `keys.write`, `translations.write`, `imports.write`, `ai.suggest` |

Invariant: `OWNER ⊃ ADMIN ⊃ MEMBER`. Enforced by `OrgRoleScopesTest`.

See [`docs/architecture/authorization.md`](../architecture/authorization.md) for the rationale behind the ADMIN exclusion list.

## How a scope is checked

1. The authenticator (JWT / API key / PAT) resolves the caller's full scope set into `SecurityScopes`.
2. [`ScopeAuthorizationFilter`](https://github.com/Pratiyush/translately/blob/master/backend/api/src/main/kotlin/io/translately/api/security/ScopeAuthorizationFilter.kt) reads the `@RequiresScope(...)` annotation on the target resource method.
3. If `SecurityScopes ⊇ required`, the request continues; otherwise the filter throws `InsufficientScopeException`.
4. [`InsufficientScopeExceptionMapper`](https://github.com/Pratiyush/translately/blob/master/backend/api/src/main/kotlin/io/translately/api/security/InsufficientScopeExceptionMapper.kt) serialises that to a 403 with the [`INSUFFICIENT_SCOPE`](errors.md#insufficient_scope) envelope.

Multiple scopes on `@RequiresScope(A, B)` are an **AND** — the caller must hold every listed scope. If you need OR semantics, document it explicitly in the endpoint and express the alternative in code; don't overload the annotation.

## Minting API keys and PATs with scopes

When a user mints an API key (T110), they pick a subset of the scopes **they currently hold** (intersected with the org role). A MEMBER cannot mint an API key carrying `api-keys.write` — they don't have it themselves. This intersection rule is enforced service-side, not UI-side; the UI hints but the server decides.

## Forward compatibility

- **Adding a scope.** New scope lands in the enum and on OWNER by default. ADMIN and MEMBER pick up read scopes automatically (any `*.read` joins MEMBER) and opt-in for writes.
- **Deprecating a scope.** Mark it deprecated in the enum + `CHANGELOG` under the release it lands. Keep it in responses for one minor version; remove under `### Removed` in the sunset release.
- **Unknown scopes in JWTs.** `Scope.parse` silently drops tokens it doesn't recognize — forward-compat for the case where an older server verifies a token minted by a newer one.

## OpenAPI surface

Every endpoint in [`openapi.json`](openapi.json) carries an `x-required-scopes` extension listing the scopes its handler annotated with `@RequiresScope`. Generated SDK clients lift this into their types so IDE completion can surface the requirement at call sites.
