# _progress.md — Translately phase dashboard

Live status per phase. Update on phase start, weekly, and on phase completion. Long-form narrative for each release lives in [RELEASE-NOTES.md](RELEASE-NOTES.md); machine-readable per-PR log is [CHANGELOG.md](CHANGELOG.md); the actionable task ledger is [tasks.md](tasks.md).

Legend: ⚪ not started · 🟡 in progress · 🟢 shipped · 🔴 blocked · ⚫ dropped.

---

## At a glance

| Phase | Theme | Tag | Status | Started | Shipped |
|---|---|---|---|---|---|
| 0 | Bootstrap | v0.0.1 | 🟡 | 2026-04-16 | — |
| 1 | Auth + Org/Project skeleton | v0.1.0 | ⚪ | — | — |
| 2 | Keys + Translations + ICU | v0.2.0 | ⚪ | — | — |
| 3 | JSON import/export | v0.3.0 | ⚪ | — | — |
| 4 | AI / MT (BYOK) + TM | v0.4.0 | ⚪ | — | — |
| 5 | Screenshots + JS SDK + In-context editor | v0.5.0 | ⚪ | — | — |
| 6 | Webhooks + CDN + CLI + Glossaries | v0.6.0 | ⚪ | — | — |
| 7 | Tasks + Branching + SSO/SAML/LDAP + Audit + Polish | v1.0.0 | ⚪ | — | — |

Target end-to-end: **12 weeks** from Phase 0 kickoff (2026-04-16) → **v1.0.0** approximately **2026-07-09**.

---

## Phase 0 — Bootstrap → v0.0.1 🟡

**Started:** 2026-04-16 · **Target:** 2026-04-18 · **Shipped:** — · **Owner:** Pratiyush

### Deliverables

- [x] `_reference/` read-only third-party reference sources cloned, gitignored
- [x] Top-level docs: README, LICENSE, CHANGELOG, RELEASE-NOTES, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CODEOWNERS, CLAUDE.md, AGENTS.md
- [x] `.gitignore` at repo root
- [x] `.kiro/steering/` — architecture, contributing-rules, api-conventions, ui-conventions
- [x] `tasks.md` + `_progress.md` Kiro trackers
- [x] `docker-compose.yml` + `infra/` scaffold — Postgres 16, Redis 7, MinIO, Mailpit
- [x] Gradle KDSL skeleton — `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, 13 empty modules (verified via `./gradlew projects`)
- [x] Quarkus app boots — `GET /q/health` returns `200 { "status": "UP" }`; 6 @QuarkusTest assertions pass ([PR #110](https://github.com/Pratiyush/translately/pull/110))
- [x] Webapp boots — Vite dev, light/dark/system toggle, shadcn primitive, Lucide icons, 55 tests across 6 suites all green ([PR #111](https://github.com/Pratiyush/translately/pull/111))
- [x] `.github/workflows/` — ci-backend, ci-webapp, link-checker, codeql, release, pages, dependabot (all triggered and green on recent PRs)
- [x] `.github/` templates — PULL_REQUEST_TEMPLATE.md + ISSUE_TEMPLATE/{bug,feature,question}.yml + config.yml
- [x] `docs/` GitHub Pages landing (`index.html` + `README.md`) + `self-hosting/hardening.md` placeholder
- [x] `.claude/commands/` — `/new-phase`, `/release`, `/check-pr`, `/dogfood-strings`
- [x] `git init` + initial commit + push to `https://github.com/Pratiyush/translately`
- [x] Branch protection on `master` — PR required, CODEOWNERS review, signed commits, linear history, no force push, conversation resolution
- [ ] CHANGELOG `[0.0.1]` final entry + RELEASE-NOTES narrative (T017)
- [ ] Tag `v0.0.1` GPG-signed → release.yml fires → GitHub Release (prerelease) published (T018)

### Verification (Phase 0 smoke)

```bash
docker compose up -d                                # postgres, redis, minio, mailpit
./gradlew build                                     # all modules compile
./gradlew :backend:app:quarkusDev &                 # boot Quarkus in dev
sleep 15 && curl -s http://localhost:8080/q/health | jq .
pnpm --filter webapp install
pnpm --filter webapp build                          # clean production bundle
```

Pass criteria: docker containers healthy; `./gradlew build` green; `/q/health` returns UP; webapp bundle builds without error.

### Risks / notes

- Gradle + Quarkus + Kotlin multi-module boilerplate is the heaviest lift in Phase 0 — budget a full day for T008+T009.
- Signed commits require GPG key on the local machine; verify `git config commit.gpgsign` and a working signer before T015.

---

## Phase 1 — Auth + Org/Project skeleton → v0.1.0 ⚪

**Planned start:** after Phase 0 ships.

### Deliverables (high level; see tasks.md T101–T123 for breakdown)

- [ ] User / Organization / Project / Language / ApiKey / Pat entities + Flyway migration
- [ ] Email+password signup → email verify (Mailpit) → login; Argon2id hashes
- [ ] JWT access + refresh rotation
- [ ] Google OAuth + optional Keycloak OIDC profile
- [ ] Permission enum + `@RequiresScope` + org/project RBAC
- [ ] API keys + PATs (hashed, prefixed)
- [ ] Multi-tenant `TenantRequestFilter` + Hibernate filter
- [ ] `CryptoService` envelope encryption (skeleton for Phase 4 keys)
- [ ] OpenAPI generated + checked in at `docs/api/openapi.json`
- [ ] Webapp: theme toggle + `⌘K` + app shell + signup/login/org/project pages
- [ ] Auto-generated webapp API client
- [ ] CI e2e workflow against docker-compose stack

### Verification (Phase 1 smoke)

```bash
docker compose up -d
# UI: signup → verify email (Mailpit at :8025) → login → create org → invite user → create project → create API key → revoke API key
./gradlew test                                      # JUnit + Kotest + Testcontainers
pnpm --filter webapp test                           # Vitest + axe
pnpm --filter e2e test                              # Playwright end-to-end
```

---

## Phase 2 — Keys + Translations + ICU → v0.2.0 ⚪

### Deliverables (high level)

- [ ] Key/Namespace/Tag/Translation/Comment/Activity entities + Flyway
- [ ] ICU MessageFormat parse + validate (icu4j) + CLDR plurals
- [ ] Bulk ops via Quartz
- [ ] Per-field activity log
- [ ] FTS search with `tsvector` + `pg_trgm`
- [ ] Translation table UX — CodeMirror 6 ICU, sticky col, keyboard nav, autosave, optimistic updates

### Verification

```bash
# UI: add 5 keys with namespaces + tags → translate to 3 languages including plurals → verify activity log entries
```

---

## Phase 3 — JSON import/export → v0.3.0 ⚪

### Deliverables (high level)

- [ ] i18next flat + nested JSON importer with conflict policies
- [ ] Filtered JSON exporter (by namespace, tag, state)
- [ ] Async via Quartz; `GET /jobs/{id}` + SSE
- [ ] Import wizard + export modal

### Verification

```bash
# CLI/UI: import sample i18next JSON → export filtered → round-trip diff is empty
```

---

## Phase 4 — AI / MT (BYOK) + TM → v0.4.0 ⚪

### Deliverables (high level)

- [ ] `AiTranslator` + adapters (Anthropic Claude, OpenAI, OpenAI-compatible)
- [ ] `MachineTranslator` + adapters (DeepL, Google, AWS)
- [ ] Per-project provider config (encrypted at rest) + monthly budget cap
- [ ] `PromptBuilder` with glossary + tone + project context
- [ ] Translation Memory via `tsvector` + `pg_trgm`
- [ ] "Suggest" per cell + "Translate selected" batch
- [ ] E2E: zero providers configured → "Suggest" UI absent, app fully usable

### Verification

```bash
# Settings: no AI configured → "Suggest" buttons absent, app fully usable
# Settings: configure Anthropic key → "Suggest" appears → click → state=MACHINE_TRANSLATED
# Add similar key → TM panel suggests prior translation
```

---

## Phase 5 — Screenshots + JS SDK + In-context editor → v0.5.0 ⚪

### Deliverables (high level)

- [ ] Screenshot entity + S3 upload + key-position pinning
- [ ] `@translately/web` — init / t / plural
- [ ] In-context editor (postMessage, ALT+click)
- [ ] Demo Vite app dogfooding the SDK

### Verification

```bash
# Upload screenshot → pin key → load demo app → ALT+click string → editor opens
```

---

## Phase 6 — Webhooks + CDN + CLI + Glossaries → v0.6.0 ⚪

### Deliverables (high level)

- [ ] Outgoing webhooks (HMAC, retries, delivery log)
- [ ] CDN bundles → S3 signed URLs, content-hash versioned
- [ ] `@translately/cli` + GitHub Action wrapper
- [ ] `@translately/react` SDK
- [ ] Glossaries (terminology tables + AI prompt injection)

### Verification

```bash
# Configure webhook → edit translation → webhook.site receives HMAC-signed POST
# Configure CDN → fetch JSON via signed S3 URL
# CLI: translately push / pull against running stack
```

---

## Phase 7 — Tasks + Branching + SSO/SAML/LDAP + Audit + Polish → v1.0.0 ⚪

### Deliverables (high level)

- [ ] Translation Tasks (assignable, deadlines, progress)
- [ ] Translation Branches (long-lived, merge-back)
- [ ] SSO (Keycloak) + SAML (via OIDC adapter)
- [ ] LDAP (Elytron) + group→role mapping
- [ ] Granular per-language, per-namespace permissions + view-only
- [ ] Audit log (append-only, queryable, exportable)
- [ ] Helm chart (stretch: Figma plugin, MCP server)
- [ ] Migration tool (importer for incumbent localization platforms)
- [ ] Pre-launch QA — santa-method adversarial review, link audit, license audit, Lighthouse, axe, k6

### Verification

```bash
# Configure Keycloak SSO → log in via SSO
# Configure LDAP → log in with LDAP creds
# Create task → assign → complete → verify audit log entry
# All Lighthouse scores ≥ budget; axe 0 violations; k6 load 100 rps sustained
```

---

## Weekly log

### Week of 2026-04-13 (Phase 0 kickoff)
- 2026-04-16: Plan approved. Memories saved. Seed docs (README, LICENSE, CHANGELOG, RELEASE-NOTES, CONTRIBUTING) written. Third-party reference sources cloned into `_reference/` (gitignored). `.gitignore` added. Top-level files (CODE_OF_CONDUCT, SECURITY, CODEOWNERS, CLAUDE.md, AGENTS.md) written. `.kiro/steering/` complete. Trackers (`tasks.md`, `_progress.md`) in place.
- 2026-04-16: Docker compose + `infra/` scaffold. Gradle KDSL multi-module skeleton verified via `./gradlew projects`. 97 GitHub issues seeded (T001–T713) with type/scope/estimate labels + 8 phase milestones. MVP labels applied: 57 mvp / 37 post-mvp / 3 deferred (Figma plugin, MCP server, migration importer).
- 2026-04-17: **T009 merged** ([PR #110](https://github.com/Pratiyush/translately/pull/110)). Quarkus application runs end-to-end: `/q/health`, `/q/health/live|ready|started`, `GET /` service metadata, `/q/openapi`, `/q/swagger-ui`. 6 @QuarkusTest assertions green. Fixed ktlint, CodeQL JS/TS gate, lychee forward-URL exclude list; split Quarkus convention plugin into app + library flavors.
- 2026-04-17: **T010 merged** ([PR #111](https://github.com/Pratiyush/translately/pull/111)). Webapp shell: Vite 6 + React 18 + TS strict + Tailwind + shadcn Button + ThemeProvider (light/dark/system with localStorage + OS reactivity) + Lucide + dogfood i18n wrapper. 55 tests / 6 suites all green, axe 0 violations in light AND dark. Fixed pnpm-action-setup version collision and GitHub-advisory 503 false-positive.
