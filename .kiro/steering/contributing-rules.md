# Contributing rules — always-loaded steering

Short, enforceable. Full onboarding text is in [CONTRIBUTING.md](../../CONTRIBUTING.md); this file is the quick-reference any agent or human reads before opening a PR.

## Golden rule — do not name the prior-art localization platform

Translately is a standalone product. The literal name of the upstream localization platform whose sources sit under `_reference/` must not appear in any committed file — code, docs, comments, commit messages, PR titles, issue bodies, changelogs, UI strings, tests. Reframe positioning on its own merits. Refer to the third-party sources under `_reference/` generically; do not name the upstream. The AGPL-compliance rule below is preserved without naming the source. The forbidden name is documented only in the out-of-repo memory file `feedback_no_competitor_name.md`.

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

## Repository metadata

Keeping the repo's discoverability surfaces curated is a CLAUDE.md rule (#11). Two things to maintain in lockstep with the stack.

### Repo topics

The GitHub Topics list is the first thing a browser sees on the repo page. The canonical set (15 topics, pinned here):

```
localization  translation-management  i18n  translation  self-hosted
open-source   mit-license             quarkus  kotlin     java
react         typescript              tailwindcss  icu-messageformat  byok
```

Set / update via the GitHub API:

```bash
gh api -X PUT repos/Pratiyush/translately/topics \
  -f 'names[]=localization' -f 'names[]=translation-management' \
  -f 'names[]=i18n' -f 'names[]=translation' \
  -f 'names[]=self-hosted' -f 'names[]=open-source' \
  -f 'names[]=mit-license' -f 'names[]=quarkus' \
  -f 'names[]=kotlin' -f 'names[]=java' \
  -f 'names[]=react' -f 'names[]=typescript' \
  -f 'names[]=tailwindcss' -f 'names[]=icu-messageformat' \
  -f 'names[]=byok'
```

**When to edit the list:**

- A new headline capability ships (e.g. add `openapi` alongside T113 going out, `jwt` if we ever front the auth story as a headline).
- A major stack swap (unlikely, but documented here so the review happens).
- Every signed tag — the release PR checks the topics list against this file.

GitHub caps at 20 topics; keep headroom. Prefer widely-used GitHub topic slugs (check via <https://github.com/topics>) over bespoke names so search finds us.

### Issue labels

The label taxonomy lives on GitHub (`gh label list`) and breaks into five axes — **every** open issue carries one label from each:

| Axis | Labels | Notes |
|---|---|---|
| **Type** | `type:feat`, `type:fix`, `type:chore`, `type:docs`, `type:test`, `type:refactor`, `type:perf`, `type:security`, `type:breaking` | Matches the Conventional Commits type set exactly. |
| **Scope** | `scope:backend`, `scope:webapp`, `scope:sdk-js`, `scope:cli`, `scope:infra`, `scope:docs` | Matches the Conventional Commits scope set exactly. |
| **Estimate** | `est:xs` ≤1h, `est:s` ≤4h, `est:m` ≤1d, `est:l` ≤3d, `est:xl` ≥1w | `xl` issues must be split before leaving READY. |
| **Phase milestone** | `Phase 0 — Bootstrap → v0.0.1` through `Phase 7 — … → v1.0.0`; `Deferred — post-v1.0.0 or not planned` | Milestone, not label — enforced by the issue-template defaults. |
| **Release lens** | `mvp` · `post-mvp` · `deferred` · `target` · `blocked` | `mvp` = Phases 0–3 (the first runnable product). `target` = in the active work queue toward the next signed tag. `blocked` = external dependency. |

Issue templates under `.github/ISSUE_TEMPLATE/` preset the first four; reviewers add `target` / `blocked` during triage.

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
- Block on: missing tests, missing migration, missing changelog entry, unsigned commits, co-author trailers, license violations, security smells, **`docs/` page out of sync with the product**.

## Keep GitHub Pages live

The canonical docs live at <https://pratiyush.github.io/translately/>, served from the repo's `docs/` directory by the `pages.yml` workflow (deploys on every push to `master`).

- **Same-PR rule.** When a user-visible feature, flow, config knob, or API surface changes, update the matching page under `docs/` in the **same** PR. The 14-point PR checklist treats out-of-date docs as a blocker.
- **Tag-gate rule.** Before pushing a signed `v0.X.Y` tag, skim the live Pages site and confirm it reflects the release contents; note any stale page in the release retrospective.
- **Phase end.** Each phase retrospective explicitly checks that the roadmap, quickstart, and any newly-shipped pages are reachable and current on the live site.
- **Empty pages rule.** A page that promises content we haven't shipped yet must carry a "placeholder — full content ships in vX.Y.Z" banner, not silently 404 or mislead.

## Every ticket ships its docs

The `docs/` tree has five surfaces; each PR updates the ones its change touches.

| Surface | Path | Update trigger |
|---|---|---|
| **Product** | `docs/product/` | Any user-visible feature, flow, or UI change. Include light + dark screenshots. |
| **Architecture** | `docs/architecture/` + ADRs under `docs/architecture/decisions/` | Module graph edit, library swap, storage/auth/perf trade-off. Non-trivial technical decisions get a new ADR (`NNN-title.md`). |
| **API** | `docs/api/` (+ regenerated `docs/api/openapi.json`) | New / changed / removed endpoint, scope, error code, rate-limit, or versioning rule. |
| **Self-hosting** | `docs/self-hosting/` | New env var, compose service, Helm value, migration, backup concern, or security hardening. |
| **LLM-ingestible** | `docs/llms.txt` (index) + `docs/llms-full.txt` (full corpus), per [llmstxt.org](https://llmstxt.org) | Regenerate **whenever** any other `docs/` page lands, so LLM consumers pull a coherent snapshot. |

Doc-missing PRs block merge. If a ticket slips docs mid-flight, open a `docs(docs): ...` follow-up issue in the **same milestone** and land it before the phase's signed tag. By v0.3.0 every MVP feature is fully documented; by v1.0.0 the tree is the canonical product source-of-truth.

### Ticket acceptance criteria

Every issue body carries a `## Docs` section listing which of the five surfaces it must update. Agents and humans check those boxes in the PR description before requesting review. A ticket whose acceptance criteria don't list any doc surface is itself a red flag — either the change is invisible to users and operators (rare) or the criteria are incomplete.
