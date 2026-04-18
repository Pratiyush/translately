---
title: Data model
parent: Architecture
nav_order: 2
---

# Data model

Translately's persistence layer is PostgreSQL 16 with Hibernate ORM + Panache (blocking JDBC). Schema evolution is driven by Flyway, plain-SQL migrations under [`backend/data/src/main/resources/db/migration/`](https://github.com/Pratiyush/translately/blob/master/backend/data/src/main/resources/db/migration/). This page is the narrative partner of `V1__auth_and_orgs.sql` — start here for the **why** and jump to the migration for the **how**.

Introduced by: [T101](https://github.com/Pratiyush/translately/issues/129) · First migration: `V1__auth_and_orgs.sql`.

## Identifier strategy

Every durable entity carries **two** identifiers:

- `id BIGSERIAL PRIMARY KEY` — monotonic, internal only. Foreign keys reference this.
- `external_id CHAR(26) NOT NULL UNIQUE` — [ULID](https://github.com/ulid/spec), Crockford base32, lexicographically time-sortable. This is the only identifier that leaves the database: every URL, JSON payload, webhook event, and API-key prefix uses `external_id`.

**Why both.** Using a bigserial for FKs keeps indexes tiny and joins fast; exposing ULIDs on the wire avoids leaking row counts, dodges integer-enumeration attacks, and lets callers sort by ID as a coarse creation-time sort.

Generation lives in [`io.translately.data.Ulid`](https://github.com/Pratiyush/translately/blob/master/backend/data/src/main/kotlin/io/translately/data/Ulid.kt); a Hibernate `@PrePersist` hook assigns it if the entity is persisted without one.

## Conventions

| Convention | Rule |
|---|---|
| Table names | plural snake_case (`users`, `project_languages`) |
| Timestamps | `created_at`, `updated_at`, optional `deleted_at` — all `TIMESTAMPTZ NOT NULL` (`deleted_at` nullable) |
| Soft delete | only where retention matters (users, organizations, projects). Everything else hard-deletes on cascade |
| FK naming | `fk_<child>_<parent>` |
| Unique constraints | `uk_<table>_<cols>` |
| Indexes | `idx_<table>_<cols>` |
| Booleans | avoided; prefer nullable `TIMESTAMPTZ` so we keep the "when" for free (`email_verified_at` vs. `email_verified`) |
| Enums | `VARCHAR(n)` + `CHECK` constraint — keeps migrations cheap and readable in psql |

## V1 entity-relationship diagram

```mermaid
erDiagram
  USERS ||--o{ ORGANIZATION_MEMBERS : "is_member_via"
  USERS ||--o{ PERSONAL_ACCESS_TOKENS : "owns"
  ORGANIZATIONS ||--o{ ORGANIZATION_MEMBERS : "has"
  ORGANIZATIONS ||--o{ PROJECTS : "owns"
  PROJECTS ||--o{ PROJECT_LANGUAGES : "supports"
  PROJECTS ||--o{ API_KEYS : "issues"

  USERS {
    bigserial id PK
    char external_id UK "26 — ULID"
    varchar email UK "254"
    timestamptz email_verified_at
    varchar password_hash "Argon2id — nullable for SSO"
    varchar full_name "128"
    varchar locale "default en"
    varchar timezone "default UTC"
    timestamptz deleted_at
  }

  ORGANIZATIONS {
    bigserial id PK
    char external_id UK "26 — ULID"
    varchar slug UK "64 — kebab-case"
    varchar name "128"
    timestamptz deleted_at
  }

  ORGANIZATION_MEMBERS {
    bigserial id PK
    char external_id UK "26 — ULID"
    bigint organization_id FK
    bigint user_id FK
    varchar role "OWNER ADMIN MEMBER"
    timestamptz invited_at
    timestamptz joined_at
  }

  PROJECTS {
    bigserial id PK
    char external_id UK "26 — ULID"
    bigint organization_id FK
    varchar slug "unique per org"
    varchar name "128"
    varchar description "1024"
    varchar base_language_tag "default en"
    varchar ai_provider "ANTHROPIC OPENAI OPENAI_COMPATIBLE"
    varchar ai_model
    varchar ai_base_url
    bytea ai_api_key_encrypted "envelope-encrypted T112"
    numeric ai_budget_cap_usd_monthly
    timestamptz deleted_at
  }

  PROJECT_LANGUAGES {
    bigserial id PK
    char external_id UK "26 — ULID"
    bigint project_id FK
    varchar language_tag "BCP-47"
    varchar name "display name"
    varchar direction "LTR or RTL"
  }

  API_KEYS {
    bigserial id PK
    char external_id UK "26 — ULID"
    bigint project_id FK
    varchar prefix UK "16 — shown once"
    varchar secret_hash "Argon2id"
    varchar name "128"
    varchar scopes "space-separated tokens"
    timestamptz expires_at
    timestamptz last_used_at
    timestamptz revoked_at
  }

  PERSONAL_ACCESS_TOKENS {
    bigserial id PK
    char external_id UK "26 — ULID"
    bigint user_id FK
    varchar prefix UK "16"
    varchar secret_hash
    varchar name
    varchar scopes
    timestamptz expires_at
    timestamptz last_used_at
    timestamptz revoked_at
  }
```

## Notable per-entity decisions

### `users`

- `password_hash` is nullable — SSO-only users (Phase 7) do not have a local password. Login attempts with email + password against a null hash always fail on the same code path as "wrong password", so there is no enumeration signal.
- `email_verified_at` gates anything that isn't signup / verify / login / password-reset. Resource filters check this when T103's email-verify ships.
- `locale` and `timezone` are stored so server-rendered emails (Qute templates) and audit exports can respect the user's preferences without another round-trip.

### `organizations`

- `slug` is unique globally. A cheap sanity check — URLs are shorter and sharable when the slug is unique.
- No explicit billing fields: the platform is open-source self-host; SaaS operators fork and add billing tables on top.

### `organization_members`

- `(organization_id, user_id)` is UNIQUE — a user has exactly one role per org at any time.
- `invited_at` / `joined_at` split lets the service layer represent a pending invite without a separate table. A row with `joined_at IS NULL` is an outstanding invite.
- Roles are enforced by `CHECK (role IN ('OWNER','ADMIN','MEMBER'))` so bad enum values can't land even from a rogue INSERT.

### `projects`

- The five `ai_*` columns are all nullable — a project with zero AI columns set is perfectly functional; Suggest simply isn't offered in the UI. This is the schema shape that enforces CLAUDE.md's BYOK-optional rule.
- `ai_api_key_encrypted BYTEA` stores the envelope produced by [`CryptoService`](crypto.md) — never the plaintext API key.
- `(organization_id, slug)` is UNIQUE — slugs can collide across orgs, just not inside one.

### `api_keys` and `personal_access_tokens`

- Secrets are never stored. The 16-character `prefix` is shown once to the caller so we can disambiguate in the UI; the hash is Argon2id (see [auth architecture](auth.md)).
- `scopes` is a denormalized space-separated token list. A key with no scopes (`''`) can still be authenticated but will fail every scope-authorization check — a defensive default.
- `expires_at`, `last_used_at`, `revoked_at` together give the admin UI everything it needs to surface key health and mint / rotate confidently.

## Soft-delete policy

Only `users`, `organizations`, and `projects` soft-delete. Rationale: retention regulations (GDPR deletion requests, SOC2 audit history) push in opposite directions; we keep the row long enough that an undo is cheap and the audit trail intact, then periodically hard-delete.

Anything referenced via `FOREIGN KEY … ON DELETE CASCADE` hard-deletes automatically — that's the default for tokens, memberships, project-languages, api-keys, and PATs. A soft-deleted `users` row does *not* cascade: the service layer decides when to fully purge.

## V1 migration story

[`V1__auth_and_orgs.sql`](https://github.com/Pratiyush/translately/blob/master/backend/data/src/main/resources/db/migration/V1__auth_and_orgs.sql) is the first migration, shipped with T102. It lands seven tables, every FK, every unique constraint, every enum `CHECK`, and the hot-path indexes.

### Creation order

Flyway applies the migration as a single transaction. Tables are created top-down so child FKs always have their parent present:

1. `users` — no FK.
2. `organizations` — no FK.
3. `organization_members` — FKs to `organizations`, `users`.
4. `projects` — FK to `organizations`.
5. `project_languages` — FK to `projects`.
6. `api_keys` — FK to `projects`.
7. `personal_access_tokens` — FK to `users`.

Indexes and `CHECK` constraints are declared inline with each table. No data seeding — the first user is created through the signup endpoint (T103), never via SQL.

### `ON DELETE CASCADE` coverage

Every child FK cascades:

| Child table | Parent | Cascade effect |
|---|---|---|
| `organization_members` | `organizations.id` | Org hard-delete removes every membership row. |
| `organization_members` | `users.id` | User hard-delete removes every membership row the user held. |
| `projects` | `organizations.id` | Org hard-delete removes every project. |
| `project_languages` | `projects.id` | Project delete removes every language. |
| `api_keys` | `projects.id` | Project delete revokes every key by deletion. |
| `personal_access_tokens` | `users.id` | User hard-delete revokes every PAT. |

Three top-level tables (`users`, `organizations`, `projects`) carry `deleted_at TIMESTAMPTZ` for soft-delete. A soft delete does **not** cascade — the row sticks around, referencing data stays intact. A later hard-delete (triggered when the service layer decides retention has expired) cascades through the FKs above.

### `CHECK` constraints

Enum-like columns use `VARCHAR(n)` plus a `CHECK` constraint. Migrating the enum values later is a cheap `ALTER TABLE … DROP CONSTRAINT … ADD CONSTRAINT`.

| Table | Column | Allowed values | Rationale |
|---|---|---|---|
| `organization_members` | `role` | `OWNER`, `ADMIN`, `MEMBER` | Matches `io.translately.data.entity.OrganizationRole`; mirrored in security's `OrgRole`. |
| `project_languages` | `direction` | `LTR`, `RTL` | UI-relevant only; covers every language in CLDR `v45`. |
| `projects` | `ai_provider` | `NULL`, `ANTHROPIC`, `OPENAI`, `OPENAI_COMPATIBLE` | BYOK providers; `NULL` means no AI wired (the default, per CLAUDE.md). |
| `projects` | `ai_budget_cap_usd_monthly` | `NULL` or `>= 0` | Prevent accidental negative budgets that would bypass the cap. |

Every CHECK has at least one integration-test assertion in `MigrationV1Test` that inserts a violating row and confirms the database rejects it — the constraint is the contract, the test is the proof.

### Unique constraints

| Table | Columns | Why |
|---|---|---|
| all tables | `external_id` | ULID is the public identifier; collisions would leak via URLs |
| `users` | `email` | One account per email |
| `organizations` | `slug` | Slugs are global; URL-sharability |
| `organization_members` | `(organization_id, user_id)` | A user has one role per org |
| `projects` | `(organization_id, slug)` | Slugs collide freely across orgs; not within |
| `api_keys` | `prefix` | Lookups are by prefix; must be unique globally |
| `personal_access_tokens` | `prefix` | Same as API keys |

### Hot-path indexes

Beyond the uniques, V1 creates five covering indexes chosen for the first authenticated flows:

- `idx_users_email_verified` partial index `WHERE email_verified_at IS NOT NULL` — the "send-me-emails" query pattern.
- `idx_org_members_user` — "what orgs is this user in?" on login.
- `idx_projects_organization` — "list projects in this org" on dashboard.
- `idx_project_languages_project` — "what languages does this project support?" on the translation UI.
- `idx_api_keys_project` and `idx_pats_user` — credential listing for the UI.

Anything else can run off the uniques / PKs; we'll add more indexes in later phases as real query patterns emerge, never speculatively.

## Future migrations

Phase 2 (`V2__keys_translations_icu.sql`) introduces `keys`, `translations`, `namespaces`, `tags`, `comments`, `activities`. Phase 3 adds `import_jobs` / `export_jobs`. Phase 4 adds translation memory, budgets, per-provider audit rows. The ID and naming conventions above are inviolate across all of them.

See [`.kiro/steering/architecture.md`](https://github.com/Pratiyush/translately/blob/master/.kiro/steering/architecture.md) for the operational guardrails (forward-only migrations, no destructive changes without a deprecation window).
