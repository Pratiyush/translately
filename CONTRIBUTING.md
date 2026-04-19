# Contributing to Translately

Thanks for considering a contribution! Translately is MIT-licensed, community-driven, and built around small focused PRs.

## Quick start

```bash
git clone https://github.com/Pratiyush/translately.git
cd translately
docker compose up -d                          # postgres, redis, minio, mailpit
./gradlew :backend:app:quarkusDev             # backend on :8080
cd webapp && pnpm install && pnpm dev         # webapp on :5173
```

Run all tests:

```bash
./gradlew test                                # backend (JUnit + Kotest + Testcontainers)
pnpm --filter webapp test                     # vitest + axe
pnpm --filter e2e test                        # playwright
```

## Ground rules

1. **One intent per PR.** Don't mix a feature with a refactor with a typo fix. Multiple PRs are cheap; reviews of mixed PRs are not.
2. **Small PRs.** If your change touches >500 lines, split it. The reviewer will ask.
3. **Conventional Commits PR titles.** Format: `<type>(<scope>): <imperative summary>` — e.g. `feat(backend): add JWT refresh rotation`. Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `security`, `breaking`. Scopes: `backend`, `webapp`, `sdk-js`, `cli`, `infra`, `docs`.
4. **GPG-signed commits required.** Branch protection rejects unsigned commits. Set up: `git config commit.gpgsign true`. See GitHub's [signed-commit guide](https://docs.github.com/en/authentication/managing-commit-signature-verification).
5. **No AI co-author trailers.** Author yourself. AI assistance is welcome; attribution to AI in commit metadata is not.
6. **All CI green before merge.** No exceptions, no `--no-verify`.

## Issue lifecycle

```
NEW → TRIAGED → READY → IN-PROGRESS → IN-REVIEW → MERGED → RELEASED → DONE
                                                             ↘ WONTFIX / DUPLICATE
```

A new issue is **NEW**. After triage adds type + scope + milestone + estimate it becomes **TRIAGED → READY**. When you start work, assign yourself and move to **IN-PROGRESS**. Open a draft PR linking the issue to move to **IN-REVIEW**. Merge → **MERGED**. Released → **RELEASED**. Verified → **DONE**.

### Labels

- **Type:** `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `security`, `breaking`
- **Scope:** `backend`, `webapp`, `sdk-js`, `cli`, `infra`, `docs`
- **Phase milestone:** `Phase 0` … `Phase 7`, then `v0.x`, `v1.0`, `v1.x`
- **Estimate:** `xs` (≤1h), `s` (≤4h), `m` (≤1d), `l` (≤3d), `xl` (≥1w — must be split before READY)

## Pre-merge checklist (in PR template)

Every PR must tick all 14 boxes (or document a waiver in the PR body):

- [ ] PR is one intent (no mixed concerns)
- [ ] All CI checks green (backend, webapp, e2e, link checker, lint, security scan)
- [ ] Linked issue closed by `Closes #N`
- [ ] Migrations are forward-compatible (no destructive without prior deprecation cycle)
- [ ] Public API changes documented in CHANGELOG.md (Unreleased)
- [ ] Breaking changes labeled `breaking` and announced in PR body
- [ ] No new dependency without justification + license check (no AGPL/GPL into MIT codebase)
- [ ] No outdated code left behind
- [ ] Light AND dark mode verified for any UI change
- [ ] Accessibility verified (keyboard nav, ARIA labels, contrast ≥4.5:1) for any UI change
- [ ] Security: no secrets, no SQL injection, validation at boundaries
- [ ] Performance: no N+1 queries (Hibernate stats checked locally)
- [ ] Commits GPG-signed by you, no AI co-author lines
- [ ] Reviewer has read every changed line

## Coding style

- **Kotlin:** Idiomatic Kotlin (data classes for DTOs, sealed for ADTs, `Result`/exceptions for errors). `ktlint` enforced. No `!!` outside of test code.
- **TypeScript:** Strict mode on. No `any`. Explicit types on exported APIs. Zod for runtime validation at boundaries.
- **SQL:** Migration files in `backend/data/src/main/resources/db/migration/V<n>__<snake_case_summary>.sql`. Plain SQL only (no Liquibase XML). Forward-compatible by default.
- **Tests:** JUnit 5 + Kotest matchers + Testcontainers (no in-memory H2 fakes). Vitest + Playwright. Coverage ≥80%.

## License compatibility

Translately is **MIT-licensed**. Do not introduce dependencies under restrictive copyleft licenses (AGPL, GPL, LGPL outside dynamic linking). The third-party source under `_reference/` is AGPL-licensed — you may read it for ideas, **never copy code into Translately**. PRs containing pasted AGPL code will be rejected.

## Reporting security issues

See [SECURITY.md](SECURITY.md). Do not file public issues for vulnerabilities.

## Code of conduct

This project follows the [Contributor Covenant 2.1](CODE_OF_CONDUCT.md). Be kind. Disagreements are fine; ad-hominem is not.

## License

Translately is distributed to end users under the [MIT License](LICENSE). When you submit a pull request you also agree to the Translately **Contributor License Agreement** ([CLA.md](CLA.md)) — a copyright + patent *license* (you retain ownership) that lets the project maintainer relicense or redistribute the combined work.

Signing the CLA is a single checkbox in the pull-request template:

> `[x] I have read and agree to the terms of [CLA.md](CLA.md).`

Tick it on every PR. No external service, no signature file — the ticked checkbox in the PR body is the agreement. Your first PR must also include the checkbox, and every subsequent PR reconfirms. If your employer has IP rights to code you write on their time, make sure they've consented before you tick the box (see CLA §4).
