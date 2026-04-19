# CLAUDE.md — Translately

Project-specific context for Claude Code. Read every session; keep short.

## What this repo is

**Translately** — open-source, MIT, self-hosted localization and translation management platform. Quarkus + Kotlin + Java 21 backend, React + Vite + shadcn/ui webapp, PostgreSQL + Redis + MinIO + Mailpit infra. No paywalled tier: Tasks, Branching, SSO, SAML, LDAP, Glossaries, Webhooks, CDN, custom storage, granular permissions, and audit all ship free. AI is **bring-your-own-key, entirely optional** — the platform must run end-to-end with zero AI configured.

Repo: <https://github.com/Pratiyush/translately> · License: MIT · Default branch: `master`.

Full plan: `~/.claude/plans/glowing-rolling-pie.md` (12-week roadmap, Phases 0–7).

## Always-loaded steering

Kiro steering files under [.kiro/steering/](.kiro/steering/) are authoritative for architecture, API conventions, UI conventions, and contributor rules. Read them before making non-trivial changes.

## Hard rules (non-negotiable)

0. **GOLDEN RULE — do not name the prior-art localization platform.** No file Translately authors — code, docs, comments, commit messages, PR titles, issue bodies, changelogs, UI strings, tests — may contain the literal name of the upstream localization platform whose sources sit under `_reference/`. Reframe positioning as a standalone product. Refer to `_reference/` generically (never name the source). The AGPL-compliance rule below is preserved without naming the upstream. The forbidden name is documented only in the out-of-repo memory file `feedback_no_competitor_name.md` (path: `~/.claude/projects/.../memory/`).
1. **GPG-signed commits required.** Never use `--no-verify`, never skip signing.
2. **Author = Pratiyush <pratiyush1@gmail.com> only.** No `Co-authored-by: Claude` or AI co-author trailers anywhere.
3. **One intent per PR.** Don't mix feature + refactor + typo. Split mixed PRs.
4. **Conventional Commits PR titles.** `<type>(<scope>): <imperative>`. Types: `feat | fix | chore | docs | test | refactor | perf | security | breaking`. Scopes: `backend | webapp | sdk-js | cli | infra | docs`.
5. **Never copy AGPL code from `_reference/`.** Third-party AGPL source is present for design reference only. Read for ideas; write clean-room MIT original code. Reject AGPL paste on review.
6. **Phase → minor version.** Phase N → v0.N.0; v1.0.0 at Phase 7. GPG-signed tags trigger `release.yml`.
7. **Light + dark + keyboard + a11y** verified for every UI change. WCAG 2.1 AA minimum.
8. **No new dependency without justification** (license check, maintenance health, size).
9. **Keep the GitHub Pages site (`docs/`) updated alongside product changes.** When a feature ships or a user-visible flow changes, update the matching page under `docs/` in the same PR. When a signed tag goes out, verify the Pages deploy at <https://pratiyush.github.io/translately/> reflects the new content (the `pages.yml` workflow deploys on every push to `master`). Stale docs are worse than missing docs — they mislead users and dampen trust.
10. **Every ticket ships its docs — product, technical, API, and LLM-ingestible.** Every PR that changes user-visible behaviour, the API surface, a config knob, or architecture MUST update the matching page(s) under `docs/` in the same PR. The doc surfaces, one per ticket type:
    - **Product** (`docs/product/`) — feature pages, walkthroughs, screenshots (light + dark) for every user-visible flow.
    - **Architecture** (`docs/architecture/` + ADRs under `docs/architecture/decisions/`) — module maps, data-flow diagrams, and an ADR for any non-trivial technical choice (library swap, auth strategy, storage layout, algorithm, performance trade-off).
    - **API** (`docs/api/`) — OpenAPI-backed endpoint reference, plus scope matrix, error-code catalogue, rate-limit policy, versioning contract. Regenerate the committed `docs/api/openapi.json` on every API change.
    - **Self-hosting** (`docs/self-hosting/`) — every new env var, compose service, Helm value, migration, or backup concern.
    - **LLM-ingestible** (`docs/llms.txt` + `docs/llms-full.txt`) — the [llmstxt.org](https://llmstxt.org) standard. Any doc addition/change must regenerate these so LLMs (Claude, Cursor, in-house assistants) can consume the full corpus at `{pages-url}/llms-full.txt`.

    Doc-missing PRs are blocked from merge. If a ticket slipped docs in-flight, open a `docs(docs): ...` follow-up in the same milestone before the phase-tag goes out. By v0.3.0 (MVP end) every shipped feature must be fully documented; by v1.0.0 the entire `docs/` tree is the canonical product source-of-truth.
11. **Repo topics + per-issue labels kept curated.** The GitHub repo's Topics list (set via `gh api repos/.../topics`) drives discoverability — the canonical list lives in [`.kiro/steering/contributing-rules.md#repository-metadata`](.kiro/steering/contributing-rules.md#repository-metadata). Review it at every signed tag and add a topic when a new headline feature ships (e.g. `react` when the webapp goes live, `openapi` when the spec is published). **Every issue** carries `type:*`, `scope:*`, `est:*`, phase-milestone, and `mvp`/`post-mvp`/`deferred` labels before leaving NEW. A PR that creates a new issue without the full label set is blocked at review.

## Behavioral rules (how to think, not what to build)

These govern *style of work*. Trivial tasks can skip them; anything non-obvious must pass all six before the first edit.

1. **Think before coding.** State assumptions out loud. When two interpretations exist, surface both — don't pick silently. When the scenario is ambiguous, stop and ask.
2. **Simplicity first.** Minimum code that solves the stated problem. No speculative flexibility, no defensive checks for scenarios the primary fix already closes. If a senior reviewer would call the diff overcomplicated, simplify before sending.
3. **Surgical diffs.** Every line must trace to the current request. No adjacent-code "improvements," no reformatting, no cleaning up pre-existing mess. Match existing style even when it's not yours. When your changes orphan code, remove only the orphans *your* changes created.
4. **Goal-driven loops.** Translate tasks into verifiable checks ("`curl -I /x` returns 200," "test Y passes"). Run the before-state check before editing so the delta is measurable. Never "retry until CI tells me what's wrong" — read the pipeline end-to-end first.
5. **Flag decisions, not just actions.** When picking between approaches with different failure modes or externally-visible consequences (URL changes, schema migrations, API surface shifts), say the tradeoff out loud before picking.
6. **Terse over comprehensive.** PR bodies, commit messages, CHANGELOG entries — the second reader beats the first. Every kept sentence earns its place; cut in half when in doubt.

These guidelines are working if: clarifying questions come *before* mistakes, diffs shrink, and rewrites-for-overcomplication disappear. Reference: Karpathy behavioral guidelines — see repo discussion 2026-04-19.

## Stack cheat-sheet

| Layer | Choice |
|---|---|
| Backend runtime | Quarkus 3.x on Kotlin, Java 21 LTS |
| Build | Gradle (Kotlin DSL) multi-module |
| Persistence | Hibernate ORM with Panache (blocking JDBC) |
| Migrations | Flyway, plain SQL (no Liquibase XML) |
| Auth | Smallrye JWT (default) · Quarkus OIDC (Keycloak profile) · Elytron LDAP (Phase 7) |
| Jobs | Quartz (Quarkus extension) + DB-backed queue |
| Email | Quarkus Mailer + Qute templates |
| Search / TM | Postgres FTS (`tsvector`) + `pg_trgm` — **no Elasticsearch in v1** |
| Webapp | React + Vite + TS + Tailwind + shadcn/ui + Radix + TanStack Query + React Router + React Hook Form + Zod |
| Icons | Lucide only (no mixed sets) |
| Motion | Framer Motion (respects `prefers-reduced-motion`) |
| Editor | CodeMirror 6 with ICU mode |
| Tests (backend) | JUnit 5 + Kotest + Testcontainers + MockK |
| Tests (webapp) | Vitest + Playwright + axe |
| Tests (perf/a11y) | Lighthouse CI |
| Infra (dev) | docker-compose: Postgres 16, Redis 7, MinIO, Mailpit, optional Keycloak |

## Repo layout (see [.kiro/steering/architecture.md](.kiro/steering/architecture.md) for detail)

```
backend/   api · data · service · security · jobs · ai · mt · storage · email · webhooks · cdn · audit · app
webapp/    src · tests · e2e
sdks/      js · react · (vue, kotlin, swift placeholders)
cli/       (Phase 6)
infra/     docker · helm · compose-prod.yml
docs/      GitHub Pages site
.github/   workflows · ISSUE_TEMPLATE · PULL_REQUEST_TEMPLATE.md · dependabot.yml
.kiro/steering/   architecture · contributing-rules · api-conventions · ui-conventions
.claude/commands/ project slash commands
_reference/                   gitignored read-only third-party reference sources
tasks.md · _progress.md   Kiro-style trackers (single source of truth)
```

## Default workflow for a new change

1. Read the relevant Phase section in `~/.claude/plans/glowing-rolling-pie.md`.
2. Open or pick up a GitHub issue; confirm type + scope + phase milestone + estimate labels.
3. Branch: `feat/<scope>/<short-desc>` (or `fix/`, `chore/`, etc.). Never commit to `master`.
4. Implement — keep each PR ≤ one intent, ≤ ~500 lines where feasible.
5. Tests first where possible (TDD). Backend coverage target ≥80%.
6. Local smoke: `docker compose up -d`, `./gradlew test`, `pnpm --filter webapp test`, `pnpm --filter e2e test`.
7. Commit GPG-signed, Conventional-Commits subject.
8. Open PR; fill the full pre-merge checklist; `Closes #N`.
9. CI green → merge (squash or rebase; no merge commits on `master`).

## Phase gate

- Each phase ends with a signed tag `v0.N.0` pushed to `master --follow-tags`.
- Before tagging: update `CHANGELOG.md` (move `Unreleased` → `[0.N.0] — YYYY-MM-DD`), update `RELEASE-NOTES.md` with the narrative.

## What NOT to do

- Don't invent new modules, libraries, or patterns without an RFC issue justifying why the locked-in choice in this file fails.
- Don't add AI-platform-paid features. If a suggestion requires a platform-owned API key, it's the wrong shape — move it behind BYOK.
- Don't touch `_reference/` — it's for reading only; the `.gitignore` excludes it.
- Don't rename files / move modules / reformat widely in the same PR as a behaviour change.

## Pointers

- Approved plan: `~/.claude/plans/glowing-rolling-pie.md`
- Obsidian Framework: `~/Documents/Obsidian Vault/00 - Framework - Open Source/Framework.md`
- Third-party reference sources (read-only, AGPL, never copy): `_reference/`
- Memories: `~/.claude/projects/-Users-deepshikhasingh-Desktop-2026-Translately-/memory/`
