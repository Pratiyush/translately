# AGENTS.md — Translately

Vendor-neutral guidance for any coding agent (Claude Code, Aider, Codex, Cursor, Continue, etc.) that works on this repo. Keep aligned with [CLAUDE.md](CLAUDE.md); where they differ, `CLAUDE.md` wins for Claude-specific tooling and `AGENTS.md` wins for anything cross-agent.

## Project at a glance

- **Translately** — open-source, MIT-licensed, self-hosted localization and translation management platform.
- **Backend:** Quarkus 3.x · Kotlin · Java 21 LTS · Gradle (Kotlin DSL) multi-module.
- **Frontend:** React · Vite · TypeScript · Tailwind · shadcn/ui · TanStack Query.
- **Infra (dev):** docker-compose — Postgres 16, Redis 7, MinIO, Mailpit.
- **Repo:** <https://github.com/Pratiyush/translately> · default branch `master`.
- **License:** MIT. **Do not copy code from** `_reference/` — it contains AGPL-3.0 third-party sources, present for design reference only.
- **Plan of record:** `~/.claude/plans/glowing-rolling-pie.md`.

## Always-loaded steering

Read these four files before any non-trivial change:

- [.kiro/steering/architecture.md](.kiro/steering/architecture.md)
- [.kiro/steering/contributing-rules.md](.kiro/steering/contributing-rules.md)
- [.kiro/steering/api-conventions.md](.kiro/steering/api-conventions.md)
- [.kiro/steering/ui-conventions.md](.kiro/steering/ui-conventions.md)

## Workflow contract

1. **Reproduce the intent.** Restate the ticket or user request in your own words before writing code.
2. **Pick the smallest viable change.** One intent per PR. If two concerns are tangled, split into two PRs.
3. **Tests first.** For new behaviour, add a failing test; then implement. Backend coverage ≥80%.
4. **Local verification gate** (run before requesting review):
   ```bash
   docker compose up -d                       # postgres, redis, minio, mailpit
   ./gradlew test                             # backend — JUnit 5 + Kotest + Testcontainers
   pnpm --filter webapp test                  # webapp — Vitest + axe
   pnpm --filter e2e test                     # Playwright
   pnpm --filter webapp lint                  # eslint + prettier --check
   ./gradlew ktlintCheck detekt               # Kotlin lint + static analysis
   ```
5. **Commit discipline.** GPG-signed, Conventional-Commits subject, author = **Pratiyush <pratiyush1@gmail.com> only** — no AI co-author lines.
6. **PR discipline.** Fill the `.github/PULL_REQUEST_TEMPLATE.md` checklist. Link `Closes #N`. Green CI before merge.

## Hard rules

- **GOLDEN RULE — do not name the prior-art localization platform** whose sources sit under `_reference/`. Not in code, docs, comments, commit messages, PR titles, issue bodies, changelogs, UI strings, or tests. Reframe positioning as a standalone product. Refer to `_reference/` generically; the AGPL-compliance rule stands without naming the upstream. The forbidden name is documented only in the out-of-repo memory file `feedback_no_competitor_name.md`.
- GPG-signed commits only; never `--no-verify`; never skip hooks.
- No `Co-authored-by: <AI>` trailers.
- No AGPL/GPL (or AGPL-ish) dependencies; MIT / Apache-2.0 / BSD-2/3 / ISC / MPL-2.0 only.
- No platform-owned AI keys. AI is BYOK-only; the platform must work fully with zero AI configured.
- No N+1 queries; check Hibernate stats before declaring a DB-touching PR done.
- Every UI change verified in light AND dark mode; keyboard nav; axe 0 violations.
- Every new endpoint documented via OpenAPI annotations; regenerate SDK + webapp API client.
- Keep the GitHub Pages site (`docs/`) in lock-step with product reality. Update the matching page in the same PR that ships the behaviour; verify the Pages deploy at <https://pratiyush.github.io/translately/> refreshes after every signed tag. Stale docs are worse than missing docs.

## Conventional Commits

```
<type>(<scope>): <imperative summary>

[optional body]

[optional footer(s)]
```

Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `security`, `breaking`.
Scopes: `backend`, `webapp`, `sdk-js`, `cli`, `infra`, `docs`.

Examples:

- `feat(backend): add JWT refresh rotation`
- `fix(webapp): preserve cursor position after ICU validation re-render`
- `chore(infra): upgrade Postgres image to 16.4`
- `security(backend): scrub AI API keys from structured logs`

## Branch naming

`<type>/<scope>/<short-desc>`, kebab-case, ≤60 chars. Example: `feat/backend/oidc-keycloak-profile`.

## Release cadence

Phase N (from the roadmap) → tag `v0.N.0`. Tags are GPG-signed:

```bash
git tag -s v0.N.0 -m "v0.N.0 — Phase N: <theme>"
git push origin master --follow-tags
```

Tag push triggers `.github/workflows/release.yml`: build → multi-arch Docker → SBOM (Syft) → sign (Cosign keyless) → GitHub Release. Pre-1.0 tags are marked prerelease automatically.

## Scope boundaries

- **In scope for v1.0.0 (Phases 0–7):** auth, org/project model, keys/translations/ICU, JSON import/export, BYOK AI/MT + TM, screenshots + JS SDK + in-context editor, webhooks + CDN + CLI + glossaries, tasks + branching + SSO/SAML/LDAP + audit.
- **Explicitly deferred past v1.0:** additional SDKs (Vue, Svelte, Angular, Kotlin, Swift, Python, Go), additional file formats (XLIFF, Android XML, iOS `.strings`/`.xcstrings`, PO, `.properties`, CSV, XLSX), Stripe billing scaffolding, marketplace for community glossaries/TMs.
- **Always out of scope:** platform-owned AI keys; gated "premium" tiers; telemetry that calls home without explicit opt-in.

## Red flags that should make you stop and ask

- A change touching `>500` lines across multiple areas.
- A dependency with a copyleft license (AGPL/GPL/LGPL).
- Any code that resembles snippets found in `_reference/`.
- A DB migration that is not forward-compatible (drops a column without a prior deprecation cycle).
- A feature that only works when the platform has API keys to a third-party AI.
- A commit unsigned, or authored by anyone other than Pratiyush.

When in doubt: open a draft PR early and ask on the issue thread.
