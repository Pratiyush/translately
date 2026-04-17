# Contributing rules — always-loaded steering

Short, enforceable. Full onboarding text is in [CONTRIBUTING.md](../../CONTRIBUTING.md); this file is the quick-reference any agent or human reads before opening a PR.

## Commits

- **GPG-signed.** `git config commit.gpgsign true`. Unsigned commits are blocked by branch protection and by `.githooks/pre-commit`. Never use `--no-verify` or `-c commit.gpgsign=false`.
- **Author = Pratiyush <pratiyush1@gmail.com> only.** Set `git config user.name "Pratiyush"` and `user.email "pratiyush1@gmail.com"` in the repo.
- **No AI co-author trailers.** No `Co-authored-by: Claude`, `Co-authored-by: Cursor`, `Co-authored-by: Aider`, etc.
- **Conventional Commits subject:** `<type>(<scope>): <imperative>`
  - Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `security`, `breaking`.
  - Scopes: `backend`, `webapp`, `sdk-js`, `cli`, `infra`, `docs`.
- **Atomic.** One logical change per commit. Rebase local fix-ups before pushing.

## Branches

- Default branch: `master`.
- Feature branch naming: `<type>/<scope>/<short-desc>` — kebab-case, ≤60 chars.
  - `feat/backend/oidc-keycloak-profile`
  - `fix/webapp/icu-cursor-jump`
  - `chore/infra/bump-postgres-16-4`
- Never push directly to `master`. Always via PR.

## Pull requests

- **One intent per PR.** Don't bundle a feature with a refactor or a typo fix. Split before opening.
- **Size:** target ≤500 lines diff. If unavoidable, note why in the PR body and expect a slower review.
- **Title:** same Conventional-Commits format as the commit subject.
- **Body:** use the PR template (Summary · Why · How · Tests · Screenshots · Checklist · `Closes #N`).
- **Closes issue:** every feature/fix PR links an issue via `Closes #N`, which must already be in state `READY` or `IN-PROGRESS`.
- **Full pre-merge checklist ticked** (14 items in `.github/PULL_REQUEST_TEMPLATE.md`).
- **CI all-green** before merge; no `[skip ci]`.

## Issue lifecycle

```
NEW → TRIAGED → READY → IN-PROGRESS → IN-REVIEW → MERGED → RELEASED → DONE
                                                            ↘ WONTFIX / DUPLICATE
```

Every issue must carry **type** + **scope** + **phase-milestone** + **estimate** labels before leaving NEW. Estimate labels: `xs` ≤1h, `s` ≤4h, `m` ≤1d, `l` ≤3d, `xl` ≥1w (must be split before READY).

## Tests

- **Tests first** for new behaviour where the shape is clear (TDD).
- **Coverage**: backend ≥80% line/branch (JaCoCo). Webapp ≥80% line (Vitest `--coverage`).
- **No in-memory DB fakes.** Backend tests run against Testcontainers Postgres.
- **Property tests** (Kotest `forAll`) for ICU parsing, permission checks, and import/export round-trips.

## Verification gate before asking for review

```bash
docker compose up -d                        # postgres, redis, minio, mailpit
./gradlew ktlintCheck detekt                # lint + static analysis
./gradlew test                              # JUnit 5 + Kotest + Testcontainers
pnpm --filter webapp lint                   # eslint + prettier --check
pnpm --filter webapp test                   # Vitest + axe
pnpm --filter webapp build                  # production bundle builds clean
pnpm --filter e2e test                      # Playwright (may be skipped for docs-only PRs)
```

Docs-only PRs still require link-checker green.

## License discipline

- **MIT only.** Incoming dependencies must be MIT / Apache-2.0 / BSD-2/3 / ISC / MPL-2.0. No AGPL, GPL, LGPL, SSPL, BSL, or Commons Clause.
- **Never copy code from `_reference/`.** The third-party sources there are AGPL-3.0, present solely to study design. Clean-room reimplementation only.
- **Third-party snippets** (Stack Overflow, blog posts) must be attributed in code comments if >10 lines and compatible with MIT.

## Security

- No secrets in the repo. `.gitignore` already excludes `.env`, `.env.local`, `*.pem`, `*.key`, `secrets/`.
- No `TODO(security)` without a tracking issue linked.
- Anything handling user input, authn, authz, crypto, SQL, file uploads, or outbound HTTP goes through the `security-review` skill checklist before merge.

## Outdated code

- **Remove dead code in the same PR that orphans it.** Don't leave a `fooV2` beside a now-unused `foo`.
- **Deprecation:** mark, warn for one minor version, remove in the next. Note in `CHANGELOG.md` under `### Deprecated` and `### Removed`.
- Quarterly sweep with `/refactor-cleaner` (knip, depcheck, ts-prune, Kotlin dead-code via detekt).

## Reviewer expectations

- Every changed line read. No rubber-stamping.
- At least one concrete improvement suggested OR an explicit "LGTM: nothing to add".
- Block on: missing tests, missing migration, missing changelog entry, unsigned commits, co-author trailers, license violations, security smells.
