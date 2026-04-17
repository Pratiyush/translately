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
