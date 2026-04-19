# Release notes

Long-form release narratives. For raw diffs and per-PR detail, see [CHANGELOG.md](CHANGELOG.md).

---

## v0.2.0 — Phase 2: Keys, translations, and ICU

**Released:** 2026-04-19
**Status:** prerelease
**Tag:** `v0.2.0` (GPG-signed) · [Compare with v0.1.0](https://github.com/Pratiyush/translately/compare/v0.1.0...v0.2.0)

### The headline

v0.1.0 gave you accounts, organizations, and projects. **v0.2.0 gives you what goes inside a project** — translation keys, per-language translations, namespaces to group keys, and the ICU MessageFormat validator that keeps translators honest. The webapp now has a real translation table with a sticky key column, a per-cell autosave editor that surfaces ICU errors inline, and create/delete flows for keys and namespaces. Every feature ships MIT, zero paywalled tier.

The backend search layer lights up Postgres full-text + trigram behind a single `KeySearchService` so large projects stay snappy without dragging Elasticsearch into the self-hosted footprint. Tasks, bulk ops, and the activity-log panel moved to later phases so Phase 2 can close on time — see `tasks.md` for the full status.

### Data model + migrations (T201 + T202)

Seven new JPA entities (`Namespace`, `Tag`, `Key`, `KeyMeta`, `Translation`, `Comment`, `Activity`) and three enums (`KeyState`, `TranslationState`, `ActivityType`) land in `:backend:data`. Flyway `V3__keys_translations_icu.sql` creates eight tables with matching `CHECK` / `UNIQUE` / `FK ON DELETE CASCADE` constraints. [ADR 0002](https://pratiyush.github.io/translately/architecture/decisions/0002-translation-state-machine.html) documents the 5-state translation lifecycle (`EMPTY / DRAFT / TRANSLATED / REVIEW / APPROVED`) — totally ordered so "approved" downloads filter cleanly. A follow-up V4 migration enables `pg_trgm` and adds a generated `keys.search_vector` tsvector column + GIN indexes for the search service.

### ICU MessageFormat validator (T203)

`IcuValidator` in `:backend:service/translations` parses ICU source via `com.ibm.icu:icu4j:76.1` `MessagePattern`. Rejects malformed syntax, missing `other` branches on plural/selectordinal/select, and unknown SIMPLE argument types. Reports line + column on every error so the editor can highlight the offending character. Accepts empty strings — `TranslationState` gates export, not the validator. The same instance powers the translation editor's autosave path and the JSON importer landing in v0.3.0.

### Backend CRUD for keys, namespaces, and translations (T208)

`KeyService` covers list / create / get / rename / soft-delete for keys and list / create for namespaces. `upsertTranslation` is the single write path for translation cells — flips the per-cell state to `DRAFT` on non-blank input, leaves it `EMPTY` on blank, writes an `Activity(TRANSLATED)` row on every edit. Every operation runs membership-gated: non-members see `NOT_FOUND`, never `FORBIDDEN`, so private projects don't leak via existence oracle. Five new paths and 13 new OpenAPI operations under `/api/v1/organizations/{orgSlug}/projects/{projectSlug}/{keys,namespaces}`.

### Postgres full-text + trigram search (T206)

`KeySearchService` composes a native-SQL query from a `KeySearchQuery` DTO — filters by namespace, tag intersection, lifecycle state; `ts_rank` surfaces the match score; trigram similarity is the fallback when FTS has no hits. The text-search configuration is deliberately `'simple'` (no language-specific stemming) — [ADR 0003](https://pratiyush.github.io/translately/architecture/decisions/0003-postgres-fts-over-elasticsearch.html) explains why Elasticsearch didn't make the cut for v1.

### Translation table + editor + key/namespace CRUD in the webapp (T207 + T208)

New route at `/orgs/:orgSlug/projects/:projectSlug` with **Keys** / **Namespaces** / **Settings** tabs. The Keys tab renders a sticky-first-column table with 5-state translation badges and a per-cell autosave textarea — blur or `⌘/Ctrl+↵` commits, `Escape` reverts, ICU errors surface inline. Create-key + delete-key + create-namespace dialogs use react-hook-form + Zod; project tiles under `/orgs/:slug` and `/projects` now link through. CodeMirror 6 ICU syntax highlighting, keyboard-grid cell navigation, and the activity-log panel are tracked for the post-v0.2.0 polish milestone — MVP ships the minimum a translator needs to be productive.

### Governance: CLA inbound + MIT outbound + behavioral rules

A new [Contributor License Agreement](https://github.com/Pratiyush/translately/blob/master/CLA.md) (Apache-ICLA-adapted, copyright-license form — contributor retains ownership, grants the maintainer perpetual unlimited rights including relicensing) now backs every accepted PR via a click-wrap checkbox in the PR template. Rule #12 in CLAUDE.md enforces the checkbox at review; review-rejects PRs that omit or strike it. A new Behavioral Rules section codifies how Claude Code should think about changes (think-before-code, simplicity-first, surgical diffs, goal-driven loops, flag-decisions-not-actions, terse-over-comprehensive).

### API-key + PAT authentication enforcement ([#149](https://github.com/Pratiyush/translately/issues/149))

Two new JAX-RS filters plug the credential surface into the authentication pipeline. `ApiKeyAuthenticator` parses `Authorization: ApiKey <prefix>.<secret>`, Argon2id-verifies the secret, pushes scopes into `SecurityScopes`. `PatAuthenticator` parses `Bearer tr_pat_…` and intersects the PAT's stored scopes with the owning user's **current** effective scope set computed from org memberships — a PAT minted while a user was ADMIN of an org it's now only MEMBER of loses its ADMIN-only scopes on every subsequent request, no revoke needed. `NonJwtBearerAuthMechanism` at priority 2000 makes the coexistence with smallrye-jwt clean; `CredentialAuthenticator` in `:backend:service/credentials` is the DB boundary.

### Documentation

New pages under `docs/`:
- [`product/keys-and-translations.md`](https://pratiyush.github.io/translately/product/keys-and-translations.html) — walkthrough of the table, editor, and dialogs.
- [`architecture/icu-validation.md`](https://pratiyush.github.io/translately/architecture/icu-validation.html) — what the validator checks and what it deliberately leaves out.
- [`architecture/search.md`](https://pratiyush.github.io/translately/architecture/search.html) — FTS vs. trigram composition.
- [`architecture/decisions/0002-translation-state-machine.md`](https://pratiyush.github.io/translately/architecture/decisions/0002-translation-state-machine.html) and [`0003-postgres-fts-over-elasticsearch.md`](https://pratiyush.github.io/translately/architecture/decisions/0003-postgres-fts-over-elasticsearch.html).
- [`api/keys-and-namespaces.md`](https://pratiyush.github.io/translately/api/keys-and-namespaces.html) — endpoint reference.
- 14 light + dark screenshots committed under `docs/product/screenshots/`, embedded via `<picture>` so the docs site automatically shows the right variant.

### What's next

v0.3.0 closes MVP with **i18next JSON import + export** — a four-step wizard in the webapp paired with the sync endpoints already merged on master. Async + SSE progress streaming for very large payloads is deferred to Phase 4 (T303). After v0.3.0, the bill is paid: Translately works end-to-end without AI configured. Phase 4 starts BYOK AI + MT.

---

## v0.1.0 — Phase 1: Authentication + Organizations

**Released:** 2026-04-18
**Status:** prerelease
**Tag:** `v0.1.0` (GPG-signed) · [Compare with v0.0.1](https://github.com/Pratiyush/translately/compare/v0.0.1...v0.1.0)

### The headline

v0.1.0 is the first release where a visitor can actually *do* something on Translately. Sign up with email + password, verify the address, log in, create an organization, create projects inside it, manage roles on members who joined organically — all backed by a real persistence layer, real JWT rotation, and a typed API surface the webapp consumes end-to-end. Zero AI required, zero paywalled tier, every "enterprise" feature ships free.

Everything in this release is a foundation the rest of the roadmap builds on. Keys, translations, imports, and the CodeMirror editor land in v0.2.0.

### Authentication (T103)

Email + password signup with email verification, login, refresh-token rotation, forgot-password, and reset-password — all behind a uniform error envelope with stable machine-readable codes. Passwords are hashed with Argon2id (OWASP-recommended parameters: m=64 MiB, t=3, p=4). Email + password + token validation rules are enforced at the boundary via `AuthValidator`; unverified users are rejected on login with `EMAIL_NOT_VERIFIED`; refresh-token reuse trips `REFRESH_TOKEN_REUSED` and invalidates the entire chain.

Verification and reset emails are sent via Quarkus Mailer + Qute templates, URL-encoding tokens before they hit the inbox. Dev and test profiles run Mailpit under docker-compose; production reads SMTP config from env vars. Token tables are single-use: `consumed_at` stamps flip atomically with the side-effect they authorize, so replaying the same link twice hits `TOKEN_CONSUMED` rather than re-verifying.

### Organizations, projects, and members (T118 + T119)

10 new endpoints under `/api/v1/organizations` cover list / create / rename for orgs, list / change-role / remove for members, and full CRUD for projects nested inside an org. Every endpoint is `@Authenticated` and runs a membership check in the service layer — non-members get `NOT_FOUND` (404) rather than a discoverable 403, so private orgs never leak via the existence oracle.

A `LAST_OWNER` guard blocks removing or demoting the final OWNER of an org so admins can't accidentally lock themselves out. Slug canonicalisation validates against the same kebab-case regex the tenant filter uses, so the URL shape is consistent everywhere.

The webapp ships three real routes backing this surface: `/orgs` (list + create dialog with role pills), `/orgs/:slug` (tabbed detail: Projects + Members + Settings), and `/projects` (active-org index driven by the header `OrgSwitcher`). Dialogs are Radix-based with focus trap, Esc-to-close, and click-outside; every form runs React Hook Form + Zod against the same regex the backend uses. Server error codes (`ORG_SLUG_TAKEN`, `PROJECT_SLUG_TAKEN`, `LAST_OWNER`) map to localised copy via `api.error.{CODE}` keys in `en.json` with a sane fallback to `error.message`.

Invite-by-email is explicitly deferred to Phase 7 — it needs the SSO / SAML / LDAP flow and the pending-membership lifecycle. Until then, members grow through self-serve org creation (each user makes their own org and runs solo, or an existing OWNER / ADMIN promotes them via the role-change endpoint once the new user's `sub` claim is known).

### Authorization (T105)

31-token `Scope` enum covers the v1.0 roadmap — orgs, members, API keys, audit, projects, keys, translations, imports / exports, AI, glossaries, screenshots, webhooks, CDN, tasks, branches. `@RequiresScope` is a JAX-RS annotation that works at method or class level (method wins when both are present); `ScopeAuthorizationFilter` runs at `Priorities.AUTHORIZATION` and rejects under-scoped callers with a uniform 403 `INSUFFICIENT_SCOPE` envelope that lists the required + missing scope sets.

Role → scope mapping is a pure-function `ScopeResolver`: OWNER = every scope, ADMIN = every scope minus the project-settings / AI-config / API-keys writes, MEMBER = every `.read` scope plus the translation / import / AI-suggest writes that let a translator do their job without touching org governance. Every `Scope.entries` value is reachable from at least one role — enforced by a `checkAll` property test.

### API keys and Personal Access Tokens (T110)

Project-scoped API keys and user-scoped PATs share a `tr_ak_<8>.<43>` / `tr_pat_<8>.<43>` token format. The prefix is the public identifier (so callers can grep for credential mismatches in logs without leaking the secret); the suffix is Argon2id-hashed at rest. Mint is gated by scope intersection at the boundary — a non-admin caller can't fabricate an admin-scoped credential, and the error body echoes back requested / held / missing scope sets so the UI can say exactly what's blocking the mint. Revoke is idempotent: stamping `revoked_at` twice is a no-op.

Scope-intersection is an under-appreciated security primitive: it means losing an API key can never grant more than the issuing user already had, even if the server is later misconfigured.

### Webapp (T115 – T120)

The app shell landed in v0.0.1. T115 – T120 fleshed it out into something that looks and feels like a product:

- Five public auth routes — sign-in, sign-up, verify-email, forgot-password, reset-password — each a React Hook Form + Zod form posting against the typed API client. Successful login derives the user shape from the access-token claims (no `/users/me` round-trip), persists the pair via `authStore.setTokens()`, and bounces to the pre-auth destination.
- Auto-generated API client via `openapi-typescript` + `openapi-fetch` wired against the committed `docs/api/openapi.json`. `pnpm codegen:check` is a lint step that fails the PR when the committed types drift from the committed schema — mirroring the backend's `checkOpenApiUpToDate`.
- Radix `Dialog` + `DropdownMenu` + `Avatar` + `Input` + `Label` primitives styled through `hsl(var(--...))` tokens so every surface switches themes without a rebuild. Light + dark are verified via `axe-core` in every test run — zero violations, mandatory for every UI change per CLAUDE.md rule #7.
- TanStack Query handles every fetch — caching, retries, error envelope propagation via `ApiRequestError`, invalidation on mutation success. The `@/lib/api/orgs.ts` hooks module is the pattern for every future API integration.

### Operational polish

- **OpenAPI** scoped to the production `io.translately.api` package via `mp.openapi.scan.packages`, so test-only probe resources can never leak into the committed schema.
- **Docs site** built with Jekyll + the `just-the-docs` remote theme on GitHub Pages, with hierarchical nav, working search, and a downloadable `docs-bundle.zip` snapshot on every deploy. ADR 0001 (Argon2id) documents the password-hashing choice and the rejected alternatives.
- **LLM-ingestible docs** — `docs/llms.txt` + `docs/llms-full.txt` follow the [llmstxt.org](https://llmstxt.org) standard, regenerated by `scripts/gen-llms-txt.sh` on every docs change.
- **Repo topics** — 15 GitHub Topics set across the stack (`localization`, `translation-management`, `i18n`, `self-hosted`, `open-source`, `mit-license`, `quarkus`, `kotlin`, `java`, `react`, `typescript`, `tailwindcss`, `icu-messageformat`, `byok`). A new CLAUDE.md rule #11 requires curating the topic list + per-issue labels at every signed tag.
- **CI** — backend (`./gradlew build`), webapp (`pnpm lint && pnpm test && pnpm build`), lychee link-checker, CodeQL for Java/Kotlin + JS/TS + Actions all run on every PR and every push to `master`.

### Quality gates

- **85+ backend integration tests** under `:backend:app` (Testcontainers Postgres + Mailpit) cover every happy path, every error code, scope escalation, duplicate slugs, tenant leakage, and refresh-token reuse.
- **100 webapp tests** across 14 suites (Vitest + Testing Library + axe-core) cover auth forms, app shell, org switcher, user menu, routing, theming, and the new orgs CRUD flow (empty state → create → refetch).
- **Coverage** reported per-module via JaCoCo (backend) and Vitest (webapp). Uploaded as CI artifacts.

### Migration notes

From v0.0.1:

1. `docker compose up -d` — the stack layout hasn't changed, but v0.1.0 adds two Flyway migrations (`V1__auth_and_orgs.sql`, `V2__auth_tokens.sql`) that run on boot.
2. Set `TRANSLATELY_CRYPTO_MASTER_KEY` and `TRANSLATELY_JWT_PRIVATE_KEY_PATH` / `TRANSLATELY_JWT_PUBLIC_KEY_PATH` in production. Dev/test profiles ship non-secret placeholders that fail loudly when used in `%prod`.
3. Regenerate any embedded API client against the new `docs/api/openapi.json` — it grew from 11 paths in v0.0.1 to 17 paths here.

### Known limitations

- **Invites are self-serve only.** Email invites + pending-membership lifecycle land with SSO / SAML / LDAP in Phase 7.
- **Base language tag is immutable** on a project after creation. Phase 2 adds a migration path.
- **AI is not wired** — bring-your-own-key AI (per-project provider + encrypted API key) lands in Phase 4. Translately must remain fully usable with zero AI configured.
- **JWT scope filter follow-ups** — issue #151 tracks a test-mode race where `JwtSecurityScopesFilter` doesn't populate `SecurityScopes` from JWT claims under `@QuarkusTest`. Not a runtime bug; tests work around it with an `X-Test-Scopes` header. Issue #149 tracks the T110 authenticator-filter enforcement follow-up.

### What's next — Phase 2 → v0.2.0

- Translation keys + values with ICU MessageFormat validation.
- Per-language pluralization + gender handling.
- JSON (flat + nested) import/export round-trip.
- CodeMirror 6 editor in the webapp with ICU syntax highlighting.
- Postgres FTS (`tsvector`) + `pg_trgm` over keys and translations — no Elasticsearch.
- Change history per key + per translation (audit trail starts here).
- First SDK shape: `@translately/js` generated off the v0.2.0 OpenAPI.

Target: **2026-05-21**.

---

## v0.0.1 — Phase 0: Bootstrap

**Released:** 2026-04-17
**Status:** prerelease
**Tag:** `v0.0.1` (GPG-signed) · [Compare with master](https://github.com/Pratiyush/translately/compare/v0.0.1...master)

### What's in the box

This is the seed prerelease for Translately — an open-source, MIT-licensed, self-hosted localization and translation management platform with bring-your-own-key AI. No product features yet; v0.0.1 exists to nail down the structure, conventions, CI, and runtime pipeline before any business logic ships.

**What actually runs**

- **Backend.** Quarkus 3.17 on Kotlin / Java 21. `./gradlew :backend:app:quarkusDev` starts the app; probes respond at `/q/health/{live,ready,started}`, aggregate health at `/q/health`, OpenAPI at `/q/openapi`, Swagger UI at `/q/swagger-ui`. A `GET /` service-metadata endpoint returns name, version, and well-known paths.
- **Webapp.** `pnpm --filter @translately/webapp dev` boots a Vite + React + TypeScript + Tailwind app with a shadcn-style Button primitive, a working light/dark/system theme toggle (localStorage + OS reactivity), Lucide icons, and a dogfood `i18n.t()` wrapper backed by `en.json`.
- **Infra.** `docker compose up -d` starts Postgres 16, Redis 7, MinIO (with auto-created buckets), and Mailpit. Keycloak is available behind `--profile keycloak`.

**Quality gates**

- 6 backend `@QuarkusTest` assertions cover health + index + OpenAPI reachability.
- 55 webapp tests across 6 suites cover the `cn()` helper, i18n lookup, the Button primitive, the ThemeProvider + ThemeToggle, and App shell structure + interaction. **`axe-core` reports zero violations in light AND dark.**
- CI runs on every push + PR: `ci-backend` (Gradle build + ktlint + detekt + test + JaCoCo), `ci-webapp` (pnpm lint + test + build), `lychee` link-checker, `codeql` (java-kotlin + javascript-typescript + actions).

**Governance**

- Master branch protected: PR required, CODEOWNERS review, signed commits, linear history, no force push / deletions, conversation resolution.
- 97 GitHub issues seeded across 8 phase milestones with `type:*` / `scope:*` / `est:*` labels. MVP partition applied: 57 `mvp` (Phases 0–3), 37 `post-mvp` (Phases 4–7), 3 `deferred` (Figma plugin, MCP server, migration importer).
- Dependabot configured for Gradle, npm workspaces, GitHub Actions, Docker.

### Migration notes

None — first prerelease. Nothing to migrate from.

### Known limitations

- No product features. No auth, no organizations, no keys, no translations. Those land in v0.1.0 and v0.2.0.
- Backend depends on the Phase 7 LDAP extension at build time, so the default profile ships with inert LDAP placeholder config. Real wiring comes in Phase 7.
- Webapp does not yet dogfood against a live Translately project — `t()` resolves locally from `en.json` until the JS SDK lands in Phase 5.
- Release images (`ghcr.io/pratiyush/translately-backend`, `-webapp`) are NOT published for `v0.0.1`. The `release.yml` Docker job is gated to skip on this version because the fast-jar doesn't yet have product binary shape. Images start publishing at `v0.1.0`.

### What's next — Phase 1 → v0.1.0

- Email + password signup with email verification (via Mailpit in dev).
- JWT access + refresh token rotation (Smallrye JWT).
- Google OAuth; optional Keycloak OIDC profile.
- Entities: `User`, `Organization`, `OrganizationMember`, `Project`, `ProjectLanguage`, `ApiKey`, `Pat`.
- Permission scope enum + `@RequiresScope` annotation + org / project RBAC.
- Multi-tenant `TenantRequestFilter` + Hibernate filter per-request.
- `CryptoService` envelope encryption skeleton (for Phase 4 AI keys).
- OpenAPI published to `docs/api/openapi.json`; webapp API client auto-generated.
- Webapp app shell: nav, org switcher, user menu, ⌘K command palette, signup / login / org / project pages.
- `.github/workflows/ci-e2e.yml` spinning up the full docker-compose stack and running Playwright smoke tests.

Target: **2026-05-07**.
