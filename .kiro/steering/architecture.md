# Architecture — always-loaded steering

Authoritative. Applies to every change. If a PR conflicts with this file, update this file first (in its own PR) before merging the change.

## Runtime

- **Backend:** single Quarkus 3.x application, Kotlin, Java 21 LTS, blocking JDBC. No reactive APIs in v1 — revisit post-v1.0.
- **Frontend:** single-page React app served by Vite (dev) / nginx (prod). No SSR in v1.
- **SDKs:** pure TypeScript (no runtime framework dependency). CLI is Node only.
- **Infra:** PostgreSQL 16, Redis 7, S3-compatible object storage (MinIO dev, S3 / R2 / any compatible prod), SMTP-speaking mailer (Mailpit dev, anything SMTP prod).

## Module boundaries (backend)

The Gradle multi-module layout enforces dependencies one direction only. Upward arrows = allowed `implementation(project(...))` edges.

```
                 app
                 ↑
       ┌─────────┼──────────────────────────────┐
    service     jobs     webhooks    cdn     email
       ↑         ↑          ↑        ↑        ↑
       └─────────┴──────────┴────────┴────────┘
                    ↑
                   data          security          ai    mt    storage    audit
                                (no deps on service/data)   (leaf modules)
                    ↑
                   api   (JAX-RS resources; depends on service)
```

Rules:

1. **`api`** (JAX-RS / REST) only talks to **`service`**. Never directly to `data` or external adapters.
2. **`service`** holds use-case logic. It depends on `data`, `security`, and adapter modules (`ai`, `mt`, `storage`, `email`, `webhooks`, `cdn`, `audit`). It must not depend on `api`.
3. **`data`** owns JPA entities, Panache repositories, and Flyway migrations. No HTTP, no business logic.
4. **Adapter modules** (`ai`, `mt`, `storage`, `email`, `webhooks`, `cdn`, `audit`) expose a domain-facing interface + concrete adapters. They do not depend on `data` or `service`.
5. **`security`** provides JWT/OIDC/LDAP/RBAC primitives and `CryptoService` (envelope encryption). Leaf module.
6. **`jobs`** wires Quartz schedules; depends on `service`.
7. **`app`** is the Quarkus entry-point; wires everything and hosts OpenAPI generation.

ArchUnit (Kotlin port via `archunit-junit5`) enforces these rules as tests in `app` from Phase 0.

## Hexagonal boundaries

- **Ports** (interfaces) live in the domain-facing module (`ai`, `mt`, `storage`, `email`, `webhooks`, `cdn`, `audit`). Interfaces describe capability, not vendor.
- **Adapters** (implementations) live in the same module, one class per vendor, ≤200 LOC each where feasible.
- **No vendor type in a port signature.** `AiTranslator.translate(...)` returns domain types; nothing from `com.anthropic.*` or `com.openai.*` leaks out.
- **CDI scope:** ports are `@ApplicationScoped`, adapters are `@Singleton` or `@ApplicationScoped` selected by `@LookupIfProperty` / `@IfBuildProperty`.

## Data

- **One database.** Shared-DB multitenancy: every tenant-scoped table has `organization_id`, enforced by a Hibernate filter enabled at the start of every request by `TenantRequestFilter`. Breaking this is a security bug.
- **Primary keys:** Postgres `bigserial` for internal FK lookups; public-facing identifiers are ULIDs (stored as `text`, indexed) on `Organization`, `Project`, `Key`, `Translation`, `User`.
- **Migrations:** Flyway, plain SQL, versioned `V<n>__<snake_case>.sql`. Never edit a migration once released; create a new migration to fix.
- **Forward-compatible schema changes.** Drops require a deprecation cycle (1 minor version warn → next minor drop).
- **No N+1.** Every Hibernate read path is covered by a test asserting max-query-count via `Statistics`.

## AI / MT

- **BYOK only.** The platform ships with **zero credentials**. AI providers are configured per-project by the org owner; keys are encrypted at rest with envelope encryption (`security/CryptoService`).
- **Optional.** App must be fully usable with zero providers configured: no greying out, no nag banners; "Suggest" buttons simply not rendered.
- **Per-project budget cap** (monthly USD ceiling). Exceeded → provider disabled until the cap resets, with an audit event.
- **Single port, per-vendor adapter.** `AiTranslator` interface; one adapter per provider. Same shape for `MachineTranslator` (DeepL, Google, AWS).
- **Prompt layer.** Glossaries + tone instructions + project context are injected by a shared `PromptBuilder`; adapters don't roll their own prompt strings.

## Auth

- **Default**: Smallrye JWT issued by `security/AuthService`. Access token ≤15 min; refresh token rotated per use.
- **OIDC**: Quarkus OIDC against Keycloak, enabled via `quarkus.profile=oidc`.
- **LDAP**: Quarkus Elytron Security LDAP, enabled via `quarkus.profile=ldap`, Phase 7.
- **API keys / PATs**: hashed with Argon2id at rest, prefix shown in UI, never logged.

## Search / TM

- Postgres FTS (`tsvector` + `pg_trgm`). Generated columns for searchable text with GIN indexes. No Elasticsearch in v1.

## Background jobs

- Quartz (Quarkus extension) with a DB-backed queue. No external broker in v1.
- Every job is idempotent; retries are safe by construction.

## Observability

- **Logs:** structured JSON via `quarkus-logging-json`. Never log AI API keys, JWTs, PATs, or raw request bodies of authenticated endpoints.
- **Metrics:** Micrometer → Prometheus endpoint.
- **Tracing:** OpenTelemetry, OTLP exporter. Off by default; enabled via env.

## Webapp

- **Single SPA.** Vite dev server in dev; static bundle served by nginx in prod.
- **State**: TanStack Query for server state. No Redux; no global client-state store beyond React context (themes, auth, current org/project).
- **Forms**: React Hook Form + Zod resolver. Validation schemas are shared between the API client and the forms where possible.
- **API client**: auto-generated from OpenAPI (in Phase 1). Hand-written clients only where the generator falls short.

## Versioning

- **API**: `/api/v1/...`. No sub-v1 breaking changes. When `v2` arrives, `v1` remains for one minor-version overlap then is removed.
- **SDKs**: track the API version they target; `@translately/web@1.x` ↔ `/api/v1/`.

## Testing pyramid

- **Backend unit**: fast, no JVM startup per test class; MockK for collaborators.
- **Backend slice**: `@QuarkusTest` against Testcontainers Postgres. Real Flyway migrations.
- **Backend E2E / contract**: Playwright + running Quarkus + Testcontainers stack. Smoke only at this tier.
- **Webapp unit**: Vitest + Testing Library + axe.
- **Webapp E2E**: Playwright against docker-compose.
- **Perf/a11y**: Lighthouse CI in CI on every webapp PR.

## What this file is NOT

- It is not a task list. See [tasks.md](../../tasks.md) and [_progress.md](../../_progress.md).
- It is not the full plan. See `~/.claude/plans/glowing-rolling-pie.md`.
- It is not a style guide. See [contributing-rules.md](contributing-rules.md), [api-conventions.md](api-conventions.md), [ui-conventions.md](ui-conventions.md).
