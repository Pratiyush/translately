<!--
PR title must follow Conventional Commits:
  <type>(<scope>): <imperative summary>

Types: feat | fix | chore | docs | test | refactor | perf | security | breaking
Scopes: backend | webapp | sdk-js | cli | infra | docs
-->

## Summary

<!-- One paragraph. What does this PR do? -->

## Why

<!-- Context. Link the issue this closes. -->

Closes #

## How

<!-- Key files + reasoning. Anything non-obvious. -->

## Tests

- [ ] Unit tests added / updated
- [ ] Integration tests pass against Testcontainers
- [ ] Manual smoke-test described below

<!-- Manual smoke notes here -->

## Screenshots

<!-- For any UI change: attach light + dark mode captures. Delete this section otherwise. -->

## Docs

Every PR ships its docs. Tick the surfaces this change touches (strike through with `~~...~~` those that genuinely don't apply, and explain in one line why):

- [ ] **Product** (`docs/product/`) — walkthrough + light/dark screenshots for any user-visible flow
- [ ] **Architecture** (`docs/architecture/` + a new `docs/architecture/decisions/NNN-...md` ADR for any non-trivial technical choice)
- [ ] **API** (`docs/api/` + regenerated `docs/api/openapi.json`) for new/changed endpoints, scopes, errors, rate-limits, or versioning rules
- [ ] **Self-hosting** (`docs/self-hosting/`) for env vars, compose services, Helm values, migrations, backups, or hardening
- [ ] **LLM-ingestible** — regenerated `docs/llms.txt` + `docs/llms-full.txt` (per llmstxt.org) so Claude / Cursor / in-house LLMs stay in sync

If none of the surfaces apply, say so explicitly and the reviewer will re-check before merge.

## Pre-merge checklist

- [ ] PR is **one intent** (no mixed concerns)
- [ ] All CI checks green (backend, webapp, e2e, link checker, lint, security scan)
- [ ] Linked issue closed by `Closes #N`
- [ ] Migrations are forward-compatible (no destructive changes without prior deprecation)
- [ ] Public API changes documented in `CHANGELOG.md` (Unreleased)
- [ ] Breaking changes labeled `type:breaking` and announced in PR body
- [ ] No new dependency without justification + license check (no AGPL / GPL into MIT)
- [ ] No outdated code left behind (dead `TODO`/`FIXME` resolved)
- [ ] **Light AND dark mode verified** for any UI change
- [ ] **Accessibility verified** (keyboard nav, ARIA labels, contrast ≥4.5:1) for any UI change
- [ ] Security: no secrets, no SQL injection, validation at boundaries
- [ ] Performance: no N+1 queries introduced (Hibernate stats checked locally)
- [ ] Commits **GPG-signed** by Pratiyush, no AI co-author trailers
- [ ] Reviewer has read every changed line (no rubber-stamping)
- [ ] **Docs updated** per the `## Docs` section above; no doc surface silently skipped
- [ ] **I have read and agree to the terms of the [Contributor License Agreement](../CLA.md).** (Required on every PR — ticking grants a copyright + patent license on this Contribution; you retain ownership.)
