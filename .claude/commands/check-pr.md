---
description: Pre-merge checklist review on the current PR — enforces the 14-point list in .github/PULL_REQUEST_TEMPLATE.md
allowed-tools:
  - Bash
  - Read
  - Grep
argument-hint: "[pr-number]  # defaults to current branch's open PR"
---

Review the pre-merge checklist against PR **$1** (or the PR for the current branch if no arg given).

Gather:

1. `gh pr view $1 --json title,body,commits,files,labels,mergeable,statusCheckRollup,baseRefName,headRefName` — understand the change set.
2. `gh pr diff $1` — read every changed line.
3. `gh pr checks $1` — confirm CI is green.
4. `gh pr view $1 --comments` — catch review feedback.

Validate the 14-point checklist:

- [ ] PR is **one intent** (no mixed concerns) — scan the diff for off-topic changes.
- [ ] All CI checks green — look at `statusCheckRollup`; any ❌ is a hard block.
- [ ] Linked issue via `Closes #N` — grep the PR body.
- [ ] Migrations forward-compatible — if any `V*.sql` under `backend/data/src/main/resources/db/migration/`, confirm no `DROP COLUMN` or narrowing.
- [ ] `CHANGELOG.md` Unreleased section updated — grep for the feature's fingerprint.
- [ ] Breaking changes: PR has `type:breaking` label AND body announces the break.
- [ ] No new dependency without justification — diff `package.json`, `gradle/libs.versions.toml`, `build.gradle.kts`.
- [ ] No AGPL/GPL license introduced.
- [ ] No dead `TODO`/`FIXME` added (`git diff ... | grep -E '^\+.*TODO|^\+.*FIXME'`).
- [ ] Light AND dark mode verified for any UI change — check screenshots in PR body.
- [ ] Accessibility (keyboard nav, ARIA, contrast ≥4.5:1) — look for axe test coverage.
- [ ] Security: no secrets in diff, no SQL string concatenation, validation at boundaries.
- [ ] No N+1 queries — look for repository methods called inside loops.
- [ ] Commits GPG-signed + authored by Pratiyush + NO `Co-authored-by:` AI trailers (`git log --format='%GK %an %s' ...`).

Output a grading: **READY TO MERGE** (all pass) or **BLOCKED** (list failing items with file+line refs).
