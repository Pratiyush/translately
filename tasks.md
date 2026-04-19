# tasks.md — Translately master task list

Single source of truth for work in flight. Every GitHub issue links back here by its `T####` id. Status legend: `[ ]` pending · `[/]` in-progress · `[x]` done · `[~]` blocked · `[-]` dropped.

Scope tags: `backend` · `webapp` · `sdk-js` · `cli` · `infra` · `docs`.
Estimate tags: `xs` ≤1h · `s` ≤4h · `m` ≤1d · `l` ≤3d · `xl` ≥1w (must be split).

## MVP scope

The 97 tasks are partitioned on GitHub via three labels:

| Label | Count | Meaning |
|---|---|---|
| `mvp` | 57 | **First runnable product.** Phases 0–3 (T001–T306) — bootstrap, auth, keys & translations with ICU, JSON import/export. End-state: Translately works end-to-end as a self-hosted localization tool without AI. Tagged `v0.3.0`. |
| `post-mvp` | 37 | **v1.0 scope beyond first MVP.** Phases 4–7 minus explicit defers — BYOK AI/MT + TM, screenshots + JS SDK + in-context editor, webhooks + CDN + CLI + glossaries, Tasks + Branching + SSO/SAML/LDAP + audit + polish. Tagged `v1.0.0`. |
| `deferred` | 3 | **Not planned for v1.0.** Figma plugin (T707), MCP server (T708), migration importer (T710). Revisit after v1.0 based on demand. |

The per-phase signed-tag cadence (one minor per phase) stays unchanged; the `mvp` label is a planning lens, not a release mechanism.

---

## Phase 0 — Bootstrap → v0.0.1

| ID | Status | Scope | Est | Title | Issue |
|---|---|---|---|---|---|
| T001 | [x] | docs | xs | Seed README, LICENSE, CHANGELOG, RELEASE-NOTES, CONTRIBUTING | — |
| T002 | [x] | infra | xs | Clone third-party reference sources into `_reference/` (gitignored) | — |
| T003 | [x] | infra | xs | `.gitignore` at repo root | — |
| T004 | [x] | docs | s | CODE_OF_CONDUCT, SECURITY, CODEOWNERS, CLAUDE.md, AGENTS.md | — |
| T005 | [x] | docs | s | `.kiro/steering/` — architecture, contributing-rules, api-conventions, ui-conventions | — |
| T006 | [x] | docs | xs | `tasks.md` + `_progress.md` Kiro trackers | — |
| T007 | [x] | infra | s | `docker-compose.yml` (postgres 16, redis 7, minio, mailpit) + `infra/` scaffold | — |
| T008 | [x] | backend | m | Gradle KDSL skeleton — `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, empty modules | — |
| T009 | [x] | backend | m | Quarkus app boots — `GET /q/health` green, minimal index endpoint | [#9 / PR #110](https://github.com/Pratiyush/translately/pull/110) |
| T010 | [x] | webapp | m | Vite + React + TS + Tailwind + shadcn placeholder shell boots | [#10 / PR #111](https://github.com/Pratiyush/translately/pull/111) |
| T011 | [x] | infra | m | `.github/workflows/` — ci-backend, ci-webapp, link-checker, codeql, dependabot | [#11](https://github.com/Pratiyush/translately/issues/11) |
| T012 | [x] | infra | s | `.github/` templates — PULL_REQUEST_TEMPLATE.md, ISSUE_TEMPLATE/{bug,feature,question}.yml | [#12](https://github.com/Pratiyush/translately/issues/12) |
| T013 | [x] | docs | s | `docs/` GitHub Pages landing + placeholder sections | [#13](https://github.com/Pratiyush/translately/issues/13) |
| T014 | [x] | infra | s | `.claude/commands/` project slash commands (`/new-phase`, `/release`, `/check-pr`, `/dogfood-strings`) | [#14](https://github.com/Pratiyush/translately/issues/14) |
| T015 | [x] | infra | xs | `git init` + initial commit + `gh repo` push to `Pratiyush/translately` | [#15](https://github.com/Pratiyush/translately/issues/15) |
| T016 | [x] | infra | xs | Branch protection on `master` (require PR, CI, signed commits, up-to-date, CODEOWNERS) | [#16](https://github.com/Pratiyush/translately/issues/16) |
| T017 | [ ] | docs | xs | CHANGELOG `[0.0.1]` final entry + RELEASE-NOTES | [#17](https://github.com/Pratiyush/translately/issues/17) |
| T018 | [ ] | infra | xs | Tag `v0.0.1` GPG-signed; `release.yml` fires; GH Release created, prerelease flag set | [#18](https://github.com/Pratiyush/translately/issues/18) |

---

## Phase 1 — Auth + Org/Project skeleton → v0.1.0

| ID | Status | Scope | Est | Title | Issue |
|---|---|---|---|---|---|
| T101 | [ ] | backend | m | Entities: User, Organization, OrganizationMember, Project, ProjectLanguage, ApiKey, Pat | — |
| T102 | [ ] | backend | m | Flyway `V1__auth_and_orgs.sql` — tables, indexes, FKs, ULIDs | — |
| T103 | [ ] | backend | m | Email+password signup flow + email verification (Mailpit dev) | — |
| T104 | [ ] | backend | m | JWT access + refresh token rotation (Smallrye JWT) | — |
| T105 | [ ] | backend | s | Argon2id password hashing + password reset flow | — |
| T106 | [ ] | backend | m | Google OAuth sign-in (Quarkus OIDC Google profile) | — |
| T107 | [ ] | backend | l | Optional Keycloak OIDC profile (`quarkus.profile=oidc`) + docker-compose addition | — |
| T108 | [ ] | backend | m | Permission scope enum + `@RequiresScope` annotation on resource methods | — |
| T109 | [ ] | backend | m | Org membership roles + project membership roles (RBAC) | — |
| T110 | [ ] | backend | s | API key + PAT issuance, listing, revocation (hashed Argon2id, prefix shown) | — |
| T111 | [ ] | backend | s | `TenantRequestFilter` + Hibernate multi-tenant filter enabled on request | — |
| T112 | [ ] | backend | s | `security/CryptoService` envelope encryption skeleton (master key from env) | — |
| T113 | [ ] | backend | s | OpenAPI generation + `openapi.json` committed to `docs/api/` | — |
| T114 | [ ] | webapp | m | Theme tokens + light/dark toggle (`next-themes`-equivalent) + localStorage persistence | — |
| T115 | [ ] | webapp | m | App shell: nav, org switcher, user menu, keyboard-first routing | — |
| T116 | [ ] | webapp | m | `⌘K` command palette (cmdk) with Navigate/Create/Actions/Settings/Recent groups | — |
| T117 | [ ] | webapp | m | Signup / Login / Email-verify / Forgot-password pages | — |
| T118 | [ ] | webapp | s | Organizations list + create + settings | — |
| T119 | [ ] | webapp | s | Project list + create + settings + member management | — |
| T120 | [ ] | webapp | s | Auto-generated API client from `openapi.json` (openapi-typescript or orval) | — |
| T121 | [ ] | infra | s | `.github/workflows/ci-e2e.yml` — docker-compose + full stack + Playwright smoke | — |
| T122 | [ ] | docs | xs | CHANGELOG `[0.1.0]` + RELEASE-NOTES narrative + screenshots | — |
| T123 | [ ] | infra | xs | Tag `v0.1.0` GPG-signed | — |

---

## Phase 2 — Keys + Translations + ICU → v0.2.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T201 | [x] | backend | m | Entities: Key, KeyMeta, Namespace, Tag, Translation, Comment, Activity |
| T202 | [x] | backend | m | Flyway `V3__keys_translations_icu.sql` (renamed — V2 was taken) |
| T203 | [x] | backend | m | ICU MessageFormat parse + validate via `com.ibm.icu:icu4j`; CLDR plurals |
| T204 | [→] | backend | m | Bulk ops via Quartz (create-many, translate-many, delete-many) — moved to Phase 4 (AI batch reuse) |
| T205 | [→] | backend | s | Activity log with per-field diffs — moved to Phase 7 (audit pairing) |
| T206 | [x] | backend | s | Key search + tag filter (Postgres FTS + trigram) |
| T207 | [x] | webapp | l | Translation table UX — sticky key col, autosave; CodeMirror 6 + keyboard-grid nav deferred to post-v0.2.0 polish |
| T208 | [x] | webapp | m | Key create/edit/delete; namespaces; tags |
| T209 | [→] | webapp | s | Activity log panel (per-key timeline) — moved to Phase 7 (with T205) |
| T210 | [x] | infra | xs | Tag `v0.2.0` GPG-signed |

---

## Phase 3 — JSON import/export → v0.3.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T301 | [x] | backend | m | i18next flat + nested JSON import (conflict: keep / overwrite / merge) |
| T302 | [x] | backend | m | i18next flat + nested JSON export (filtered by namespace, tag, state) |
| T303 | [→] | backend | s | Async via Quartz; `GET /jobs/{id}` status polling + SSE events — moved to Phase 4 (bulk-AI batch workflow) |
| T304 | [x] | webapp | m | Import wizard (upload → preview → conflict resolution → run → status) |
| T305 | [x] | webapp | s | Export modal with filters |
| T306 | [x] | infra | xs | Tag `v0.3.0` GPG-signed — **MVP shipped** |

---

## Phase 4 — AI / MT (BYOK) + TM → v0.4.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T401 | [ ] | backend | m | `AiTranslator` port + adapters: AnthropicClaude, OpenAi, OpenAiCompatible |
| T402 | [ ] | backend | m | `MachineTranslator` port + adapters: DeepL, GoogleTranslate, AwsTranslate |
| T403 | [ ] | backend | s | Per-project AI provider config (provider, model, API key, budget cap) + envelope-encrypted at rest |
| T404 | [ ] | backend | s | `PromptBuilder` — injects glossary terms + tone instructions + project context |
| T405 | [ ] | backend | m | Translation Memory via `tsvector` + `pg_trgm`; "similar keys" panel |
| T406 | [ ] | backend | s | Per-project monthly budget cap + auto-disable + audit event |
| T407 | [ ] | webapp | m | Project settings → AI tab: choose provider, set key, model, budget |
| T408 | [ ] | webapp | s | "Suggest" button per translation cell; "Translate selected" batch |
| T409 | [ ] | webapp | s | TM suggestion panel beside editor |
| T410 | [ ] | e2e | s | E2E test: no providers configured → "Suggest" absent, app fully usable |
| T411 | [ ] | infra | xs | Tag `v0.4.0` GPG-signed |

---

## Phase 5 — Screenshots + JS SDK + in-context editor → v0.5.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T501 | [ ] | backend | m | Screenshot entity + S3 upload + key-position pinning |
| T502 | [ ] | sdk-js | m | `@translately/web` — `init`, `t(key, params)`, plural support |
| T503 | [ ] | sdk-js | m | In-context editor via `postMessage` (ALT+click opens editor) |
| T504 | [ ] | webapp | m | Screenshot upload UI + key pinning canvas |
| T505 | [ ] | docs | s | Demo Vite app in `_reference/demo/` that dogfoods the SDK |
| T506 | [ ] | infra | xs | Tag `v0.5.0` GPG-signed |

---

## Phase 6 — Webhooks + CDN + CLI + Glossaries → v0.6.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T601 | [ ] | backend | m | Outgoing webhook delivery: HMAC-SHA256, retries, delivery log |
| T602 | [ ] | backend | m | CDN: per-project content config → JSON bundles → S3 signed URLs, content-hash versioned |
| T603 | [ ] | backend | s | Glossaries (terminology tables) + link to keys + inject into AI prompt |
| T604 | [ ] | cli | m | `@translately/cli` — `login`, `push`, `pull`, `sync`, `extract` |
| T605 | [ ] | cli | s | GitHub Action wrapping the CLI |
| T606 | [ ] | sdk-js | s | `@translately/react` — `<TranslatelyProvider>`, `useT`, `<T>` |
| T607 | [ ] | webapp | s | Webhook config UI + delivery log viewer |
| T608 | [ ] | webapp | s | CDN config UI + content preview |
| T609 | [ ] | webapp | s | Glossary UI |
| T610 | [ ] | infra | xs | Tag `v0.6.0` GPG-signed |

---

## Phase 7 — Tasks + Branching + SSO/SAML/LDAP + Audit + Polish → v1.0.0

| ID | Status | Scope | Est | Title |
|---|---|---|---|---|
| T701 | [ ] | backend | l | Translation Tasks — assignable, deadline, progress, completion |
| T702 | [ ] | backend | xl | Translation Branches — long-lived, merge-back, conflict resolution |
| T703 | [ ] | backend | m | SSO via Keycloak (IdP + broker); SAML via Quarkus OIDC adapter |
| T704 | [ ] | backend | m | LDAP via Quarkus Elytron Security LDAP + group→role mapping |
| T705 | [ ] | backend | m | Granular permissions — per-language, per-namespace, view-only |
| T706 | [ ] | backend | s | Audit log — append-only, queryable, CSV export |
| T707 | [ ] | backend | l | Figma plugin (stretch) — manifest + fetch keys + live translate |
| T708 | [ ] | backend | l | MCP server (stretch) — expose Translately to Claude/Cursor |
| T709 | [ ] | infra | m | Helm chart for Kubernetes self-host |
| T710 | [ ] | backend | m | Importer for incumbent platforms (migration tool) |
| T711 | [ ] | docs | m | Migration guides + launch documentation pass |
| T712 | [ ] | docs | m | Pre-launch QA — `/santa-method`, link audit, license audit, Lighthouse, axe, k6 |
| T713 | [ ] | infra | xs | Tag `v1.0.0` GPG-signed — launch release |

---

## Unscheduled / post-v1.0

- Additional SDKs (Vue, Svelte, Angular, Kotlin Android, Swift iOS, Python, Go).
- Additional file formats (XLIFF 1.2/2.0, Android XML, iOS `.strings`/`.stringsdict`/`.xcstrings`, PO/POT, `.properties`, CSV, XLSX).
- Stripe billing scaffolding (opt-in, off by default).
- Marketplace for community glossaries / TMs.
- Advanced search (saved views, per-user filters).
- Translator workflow: review states (DRAFT/REVIEW/APPROVED), translator-reviewer separation.

## Bugs / debt (rolling)

> Log issues here as they're found; migrate to a phase once scheduled. Format: `BUG-### | scope | short description`.

- _(empty so far — log here on first encounter)_
