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
- **Tenant request context** (`:backend:security`, `io.translately.security.tenant.TenantContext`) — `@RequestScoped` bean holding the per-request URL-path organization identifier (ULID or slug). Null when the request isn't tenant-scoped (login, signup, `/q/health`, root).
- **Tenant request filter** (`:backend:api`, `io.translately.api.tenant.TenantRequestFilter`) — JAX-RS `@Provider` running at `Priorities.AUTHENTICATION - 100`. Extracts the identifier from `/api/v1/organizations/{orgIdOrSlug}/...` paths and binds it to the request context. Syntactic validation only (ULID = 26-char Crockford base32 or slug = lowercase kebab-case ≤64 chars). Resolution to the internal `organization_id` is the service layer's job. Hibernate filter activation lands with the first DB-touching feature (T103+).
- 14 Kotest assertions: 6 for `TenantContext` (default state, set/clear/replace/blank semantics, ULID-shaped identifier), 5 regex-level for `extractTenant` (ULID path / slug path / missing leading slash / no organizations segment / invalid characters), 3 end-to-end `@QuarkusTest` scenarios exercising the full filter path (tenant-less path, slug path, ULID path).
- **JWT issuer + authenticator** (`:backend:security` + `:backend:api`, `io.translately.security.jwt`). `JwtIssuer` mints short-lived access tokens (default 15 min, carrying `sub` / `upn` / `scope` / `groups` / `orgs` / `typ=access`) and longer-lived refresh tokens (default 30 days, minimal claim set with a single-use `jti`). `JwtSecurityScopesFilter` reads the validated `JsonWebToken` at `Priorities.AUTHENTICATION`, rejects refresh tokens presented as bearer credentials, and unions the access token's scopes into `SecurityScopes`.
- Dev/test RSA keypair committed at `backend/app/src/main/resources/jwt-dev/` with a `README.md` flagging it as non-secret. `%prod` profile overrides via `TRANSLATELY_JWT_PUBLIC_KEY_PATH` / `TRANSLATELY_JWT_PRIVATE_KEY_PATH` env vars.
- Quarkus OIDC extension (on the classpath for Phase 7 SSO/SAML) disabled in the default profile so `smallrye-jwt` cleanly owns `JsonWebToken` production.
- Proactive Quarkus auth (`quarkus.http.auth.proactive=true`) so the `JsonWebToken` is populated for every request, not only endpoints with MicroProfile security annotations — our authorization decisions live in `@RequiresScope`, not `@RolesAllowed`.
- 14 Kotest + JUnit assertions: 10 for `JwtIssuer` end-to-end (claim shape, iss/aud, exp, orgs array, scope order, refresh-token minimal shape + unique jti, RS256 header, expiry-instant round-trip), 4 for `JwtSecurityScopesFilter` via `@QuarkusTest` (unprotected endpoint open, missing token → 401 on `@Authenticated`, wrong scope → 403, refresh token → 403). The full access-token-opens-protected-endpoint happy path lands in T103 with real signup/login wiring.

- **Email + password signup flow with verification** (T103). Six new endpoints under `/api/v1/auth/*`: `signup`, `verify-email`, `login`, `refresh`, `forgot-password`, `reset-password`. Documented end-to-end in `docs/getting-started/auth.md`.
- **Flyway V2 migration** (`backend/data/src/main/resources/db/migration/V2__auth_tokens.sql`) — three new tables `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens`, each with `external_id` ULID, per-user FK with `ON DELETE CASCADE`, unique token / jti columns, `consumed_at` + `expires_at` for single-use semantics, and `idx_*_user` indexes for lookups.
- **JPA entities** (`:backend:data`, `io.translately.data.entity`): `RefreshToken`, `EmailVerificationToken`, `PasswordResetToken` — each extending `BaseEntity` with an `isUsable` helper that checks expiry + consumption.
- **AuthService** (`:backend:service`, `io.translately.service.auth`) — use-case entry point for the flow. Signup writes an Argon2id password hash, generates a 32-byte random token via `TokenGenerator`, persists its Argon2id hash in `email_verification_tokens`, and sends the verification email. Verify-email consumes the token atomically with flipping `users.email_verified_at`. Login verifies the password hash, refuses unverified users with `EMAIL_NOT_VERIFIED`, and returns a `JwtTokens` pair while recording the new refresh jti in the ledger. Refresh validates the signed JWT via `RefreshTokenParser` (Smallrye `JWTParser`), looks up the `jti`, consumes it atomically, mints a fresh pair, and rejects replays with `REFRESH_TOKEN_REUSED`. Forgot-password is intentionally silent for unknown emails. Reset-password consumes the token and overwrites the password hash in the same transaction.
- **AuthException sealed hierarchy** and **AuthValidator** — boundary validation for every endpoint; stable machine-readable codes (`EMAIL_TAKEN`, `VALIDATION_FAILED`, `EMAIL_NOT_VERIFIED`, `INVALID_CREDENTIALS`, `TOKEN_INVALID`, `TOKEN_CONSUMED`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`) mapped at the resource layer to the uniform error envelope specified in `api-conventions.md`.
- **EmailSender** (`:backend:email`, `io.translately.email`) — Quarkus Mailer + Qute templates (`templates/email/verify.html`, `templates/email/reset.html`) with a plain-text alternative for clients without HTML rendering. URL-encodes raw tokens before inlining them; configurable via `translately.mail.from` + `translately.mail.base-url`.
- **RefreshTokenParser** (`:backend:security`) — new CDI-scoped helper that cryptographically validates a refresh JWT (signature + issuer + audience + expiry via Smallrye `JWTParser`) and projects it down to `subject` + `jti`. Used only by the `/auth/refresh` path; bearer-credential access is still blocked by `JwtSecurityScopesFilter`.
- **AuthResource** (`:backend:api`, `io.translately.api.auth`) — JAX-RS resources annotated with `@PermitAll` because these endpoints sit outside the tenant scope (`TenantRequestFilter` leaves the context unbound for non-`/organizations/...` paths). Every endpoint has an `@Operation` + `@APIResponses` annotation so OpenAPI generation is fully populated.
- `JwtTokens` now exposes the freshly-minted `refreshJti` alongside the signed tokens, so `AuthService` can write the ledger row without re-parsing the JWT.
- 42 new assertions: 17 unit tests for `AuthValidator` (email + password + name + token rules, error-code mapping); 5 unit tests for `EmailSender` (URL encoding, trailing-slash normalization, HTML + text rendering for both verify and reset flows); 9 unit tests for the three token entities (`isUsable` truth table, default ULID wiring); and 11 integration tests across `AuthServiceIT` + `AuthResourceIT` + `MigrationV2Test` (happy-path signup → verify → login, duplicate-email rejection, invalid-email / short-password validation, unknown-user login, wrong-password rejection, distinct refresh jtis across logins, refresh rotation, replay detection, password-reset round-trip, every error-body `code` assertion via RestAssured, and raw schema-shape checks for V2). Integration tests run in CI against real Postgres 16 + Mailpit via Testcontainers.

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
