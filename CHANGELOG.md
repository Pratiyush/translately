# Changelog

All notable changes to Translately are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0 (`0.x`) tags are marked as prereleases on GitHub.

## [Unreleased]

### Added (Phase 1 in progress)
- **JPA entities for auth + orgs** (`:backend:data`): `User`, `Organization`, `OrganizationMember`, `Project`, `ProjectLanguage`, `ApiKey`, `Pat`. Common `BaseEntity` superclass with auto-maintained `createdAt` / `updatedAt` + external ULID. Enums: `OrganizationRole`, `LanguageDirection`, `AiProvider`.
- **ULID generator** (`io.translately.data.Ulid`) — Crockford-base32, 26 chars, monotonic within a single millisecond batch.
- 47 Kotest tests covering ULID properties, email normalization, slug normalization, verification status, AI-flag logic, token active/expired/revoked semantics, and entity defaults.

- **Flyway V1 migration** (`backend/data/src/main/resources/db/migration/V1__auth_and_orgs.sql`) — 7 tables (`users`, `organizations`, `organization_members`, `projects`, `project_languages`, `api_keys`, `personal_access_tokens`), matching FK/unique/check constraints, indexes on hot lookups, `ON DELETE CASCADE` on every child FK. `CHECK` constraints enforce `OrganizationRole`, `LanguageDirection`, `AiProvider` enums and non-negative AI budget.
- Integration test (`MigrationV1Test`, Kotest `DescribeSpec` + Testcontainers Postgres 16-alpine) applies the migration and asserts table shape, unique constraints, cascade deletes, and all three `CHECK` constraint violations surface as SQL errors.
- **Permission scope system** (`:backend:security`): `Scope` enum with 31 tokens covering the v1.0 roadmap (org, members, api-keys, audit, projects, keys, translations, imports/exports, AI, glossaries, screenshots, webhooks, CDN, tasks, branches); `@RequiresScope` annotation (method + class level, method wins); `SecurityScopes` request-scoped bean holding the principal's granted scope set.
- **Scope authorization filter** (`:backend:api`): `ScopeAuthorizationFilter` (JAX-RS `@Provider` at `Priorities.AUTHORIZATION`) enforces the declarative annotation; `InsufficientScopeExceptionMapper` emits a uniform 403 body `{ error: { code: "INSUFFICIENT_SCOPE", details: { required, missing } } }`.
- 40 Kotest assertions: 12 for the enum (naming convention, uniqueness, round-trip serialize/parse, exhaustiveness, declaration-order stability), 18 for `SecurityScopes` (grant/revoke/hasAll/hasAny/missing semantics), plus 10 end-to-end `@QuarkusTest` scenarios for the filter (header-driven allow/deny, method-over-class override, multi-scope requirements, unknown-token tolerance).
- **CryptoService envelope encryption** (`:backend:security`, `io.translately.security.crypto`): AES-256-GCM over a two-layer envelope — a fresh per-secret Data Encryption Key (DEK) encrypted with a single operator-provided Key Encryption Key (KEK). Self-describing format with a leading version byte, IVs, and GCM tags; minimum envelope size 89 bytes. Rotating the KEK means re-encrypting rows (DEKs never need rotation).
- `CryptoServiceProducer` CDI bean reads `translately.crypto.master-key` (env var `TRANSLATELY_CRYPTO_MASTER_KEY`, base64, 32 bytes). Dev/test profiles ship an obvious non-secret placeholder; `%prod` requires the env var to be set at boot (app refuses to start otherwise).
- 26 Kotest assertions covering constructor validation (4), byte-array round-trip including 1 MiB payload (4), UTF-8 string round-trip with emoji (3), non-determinism across 100 encryptions (2), envelope structure + overhead (3), AEAD tamper resistance for every mutable byte region and truncation and wrong-KEK (6), version-byte handling + too-short inputs (3), and a plaintext-leak guard.
- **PasswordHasher** (`:backend:security`, `io.translately.security.password`) — Argon2id wrapper with OWASP-recommended defaults (m=64 MiB, t=3, p=4). Hashes user passwords, API-key / PAT secrets, and email-verification / password-reset token bodies. Uses the self-describing Argon2 output so parameter upgrades don't need schema changes. Verification is constant-time and never throws on malformed input (returns `false` instead).
- **TokenGenerator** (same package) — SecureRandom → base64url-without-padding single-use tokens. 32 raw bytes → 43 URL-safe characters, with an optional human-readable prefix for API keys and PATs (e.g. `tr_apikey_…`).
- 24 Kotest assertions: 14 for `PasswordHasher` (output shape, parameter embedding, salt freshness, verify for correct / wrong / case-mismatched / null / blank / malformed inputs, empty password, 1 KiB password, Unicode+emoji, multi-user cross-contamination guard); 10 for `TokenGenerator` (default length, URL-safe alphabet, 10 000 uniqueness, custom entropy sizes, min/max validation, prefixed output).

### Infrastructure
- Convention plugin `translately.quarkus-module` now adds `jakarta.persistence.Entity` / `MappedSuperclass` / `Embeddable` to the `kotlin-allopen` annotation set so entity classes are non-final (required by Hibernate proxies).
- `:backend:data` gains `testcontainers-postgresql` + `testcontainers-junit` on the test classpath.

## [0.0.1] — 2026-04-17 — Phase 0: Bootstrap

First prerelease. The repository, CI surface, and runtime pipeline exist end-to-end; no product features yet. Next release (v0.1.0, Phase 1) lights up auth and the org / project model.

### Added

**Repository & governance**
- Top-level files: `README.md`, `LICENSE` (MIT), `CHANGELOG.md`, `RELEASE-NOTES.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CODEOWNERS`.
- Agent-pair-programming context: `CLAUDE.md`, `AGENTS.md`, `.claude/commands/` (`/new-phase`, `/release`, `/check-pr`, `/dogfood-strings`).
- Always-loaded Kiro steering: `.kiro/steering/architecture.md`, `contributing-rules.md`, `api-conventions.md`, `ui-conventions.md`.
- Kiro-style trackers: `tasks.md` (T001–T713) and `_progress.md` (phase dashboard + weekly log).
- Branch protection on `master`: PR required, CODEOWNERS review, signed commits, linear history, no force push / deletions.
- 97 GitHub issues seeded across 8 phase milestones with `type:*` / `scope:*` / `est:*` labels and an MVP partition (`mvp` / `post-mvp` / `deferred`).

**CI / CD**
- `.github/workflows/`: `ci-backend`, `ci-webapp`, `link-checker` (lychee), `codeql` (java-kotlin + javascript-typescript + actions), `release`, `pages`, Dependabot.
- Issue templates (`bug.yml`, `feature.yml`, `question.yml`), PR template with 14-point pre-merge checklist.

**Backend (Quarkus · Kotlin · Java 21)**
- Gradle (Kotlin DSL) multi-module skeleton: `backend/{api,data,service,security,jobs,ai,mt,storage,email,webhooks,cdn,audit,app}` with convention plugins (`translately.base`, `translately.quarkus-module`, `translately.quarkus-app`).
- Version catalog at `gradle/libs.versions.toml` pinning Kotlin 2.1 · Quarkus 3.17 · Kotest / MockK / Testcontainers / ArchUnit.
- Runtime: Quarkus application boots, serves `GET /` (service metadata), `/q/health/{live,ready,started}`, `/q/openapi`, `/q/swagger-ui`. 6 @QuarkusTest assertions.
- CDI bean discovery via `META-INF/beans.xml` in every backend module.
- LDAP extension placeholder values in the default profile so the app boots without LDAP configured (Phase 7 wires real values).

**Webapp (React · Vite · TypeScript · Tailwind · shadcn)**
- pnpm workspace + Vite 6 + React 18 + TS 5.7 strict.
- Design tokens for light + dark in `src/theme/tokens.css`; hsl-var references; honors `prefers-reduced-motion`; persistent focus rings.
- `ThemeProvider` + `ThemeToggle` cycling light → dark → system with `localStorage` persistence and OS-preference reactivity.
- shadcn primitive: `Button` (6 variants × 4 sizes × `asChild`).
- Lucide icons only; `src/i18n/` dogfood wrapper with `en.json`.
- App shell: header/main/footer landmarks, primary nav, metric cards, MVP summary.
- 55 tests across 6 suites (`utils.test.ts`, `i18n.test.ts`, `button.test.tsx`, `ThemeProvider.test.tsx`, `ThemeToggle.test.tsx`, `App.test.tsx`) — all green, no axe violations in light or dark.

**Infra**
- `docker-compose.yml` for dev: Postgres 16, Redis 7, MinIO (auto-created buckets), Mailpit; Keycloak behind `--profile keycloak`.
- `infra/docker/`: `backend.Dockerfile` (JVM fast-jar), `backend.native.Dockerfile` (GraalVM native-image), `webapp.Dockerfile` (nginx static + SPA + `/api` reverse proxy), `nginx.conf`.
- `infra/compose-prod.yml` + `.env.prod.example` for single-host production.
- `docs/` landing page + `self-hosting/hardening.md` placeholder.

### Notes

- No paywalled premium tier: every planned feature (SSO, SAML, LDAP, Tasks, Branching, Webhooks, Glossaries, custom storage, granular permissions, audit) will ship MIT.
- AI is bring-your-own-key, entirely optional. The platform must work end-to-end with zero AI configured.
- Third-party reference sources under `_reference/` are read-only, AGPL-licensed, gitignored, never copied.

[Unreleased]: https://github.com/Pratiyush/translately/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/Pratiyush/translately/releases/tag/v0.0.1
