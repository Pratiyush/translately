# Multi-tenancy

Translately is a single-process, multi-tenant server. Every business entity (project, key, translation, API key, screenshot, webhook) hangs off an organization; a request either scopes to exactly one organization or it's a cross-org endpoint (login, signup, organization listing, health, metrics).

Introduced by: [T111](https://github.com/Pratiyush/translately/issues/135) (TenantContext + TenantRequestFilter).

Related docs: [auth architecture](auth.md), [authorization](authorization.md), [request-lifecycle](request-lifecycle.md).

## Tenant identifier

The URL path is the single source of truth:

```
/api/v1/organizations/{orgIdOrSlug}/projects/{projectId}/keys
                      └── tenant identifier ─┘
```

`{orgIdOrSlug}` is **either**:

- a **ULID** (26 Crockford base32 chars), or
- a **slug** (lowercase kebab-case, ≤64 chars, starts and ends with `[a-z0-9]`).

The syntax check is done in the filter; resolution to an internal `organizations.id BIGINT` happens in service code once the request reaches a DB call.

### Why both?

- **Slug** is friendly for URLs users share (`/organizations/acme/…`).
- **ULID** is the stable external ID that won't change on rename.

Both are accepted at every endpoint; the service layer uses whichever hits first in `SELECT id FROM organizations WHERE external_id = ? OR slug = ? AND deleted_at IS NULL`.

## `TenantContext`

[`io.translately.security.tenant.TenantContext`](../../backend/security/src/main/kotlin/io/translately/security/tenant/TenantContext.kt) is a `@RequestScoped` CDI bean holding exactly the string the client sent — never the resolved internal id.

```kotlin
@RequestScoped
open class TenantContext {
    open fun current(): String?      // raw URL identifier, or null
    open fun set(identifier: String?) // filter calls this once per request
    open fun isBound(): Boolean
}
```

Two invariants:

1. **Set exactly once per request**, by `TenantRequestFilter`. Service code reads; nothing else writes.
2. **Never holds the resolved bigint id.** Resolution is DB-bound, cacheable, and happens inside services — not in the request filter chain.

`null` is a legitimate value (login, signup, `/q/health`, `GET /`). Resource methods that require a tenant assert `isBound()` or receive the identifier as a `@PathParam`.

## `TenantRequestFilter`

[`io.translately.api.tenant.TenantRequestFilter`](../../backend/api/src/main/kotlin/io/translately/api/tenant/TenantRequestFilter.kt) is a JAX-RS `ContainerRequestFilter` at priority `AUTHENTICATION - 100` — it runs **before** every authenticator.

Pseudocode:

```kotlin
fun filter(ctx: ContainerRequestContext) {
    tenantContext.set(extractTenant(ctx.uriInfo.path))
}
```

`extractTenant` matches `^(api/v\d+/)?organizations/([^/]+)(/.*)?$`. The captured identifier is syntax-validated against the ULID / slug regex; anything else is treated as "no tenant" (so that auth endpoints like `/api/v1/auth/login` leave `TenantContext` unbound).

**Why before auth?** Authenticators (T110 API-key, T103 JWT) need to know the tenant to scope their credential lookup. An API key is issued against a project; without a tenant in scope, the authenticator can't tell whether the credential is valid for this request.

## Row-level isolation

Phase 1 does not activate Hibernate's native multi-tenancy strategy (`@TenantId`) — it uses an explicit `organization_id BIGINT NOT NULL` FK on every tenant-scoped table and Panache repository methods that accept an `organization_id` parameter. This is simpler to reason about and avoids Hibernate's schema-per-tenant gotchas.

Phase 2 will layer a Hibernate `@Filter(name = "tenantFilter")` on the relevant entities, activated from the filter chain once the identifier is resolved. The switch is transparent to callers.

## Cross-organization endpoints

Not every endpoint is tenant-scoped. Four classes of exception:

1. **Auth** — `/api/v1/auth/*` (signup, login, refresh, verify, reset).
2. **Org listing** — `GET /api/v1/organizations` (the user's own orgs; scoped by `ScopeResolver.canResolveFor(userId, memberships, orgId = null)`).
3. **Health / metrics** — `/q/health`, `/q/metrics`.
4. **Root** — `GET /`.

For each, `TenantContext.current()` returns `null` and the resource method handles the non-scoped case explicitly.

## Testing

Integration tests live in `:backend:app` under `tenant/`:

- `TenantRequestFilterIT` drives a Quarkus request and asserts `TenantContext.current()` after the filter runs.
- Pure-unit tests of `extractTenant` live in `:backend:api`'s test tree — they exercise every path shape without bootstrapping Quarkus.
- Service-level tests use `@TestProfile` with a stub `TenantContext` so they can run without a full HTTP request.

## Operator implications

- **Per-tenant resource caps** (rate-limit, storage quota) are enforced in service code by loading `organizations.limits` (to be added Phase 6) — the filter never makes a policy decision.
- **Deleting an organization** soft-deletes the `organizations` row and cascades hard-delete on every FK with `ON DELETE CASCADE`. Multi-tenancy in Phase 1 does not provide a separate "freeze all tenants" switch; that would be a Phase 7 audit feature.
