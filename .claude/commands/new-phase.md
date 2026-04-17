---
description: Start work on a new phase — branch, update trackers, create Phase <N> milestone if missing
allowed-tools:
  - Bash
  - Read
  - Edit
argument-hint: "<phase-number>  # e.g. 1"
---

Start Phase **$1** of Translately. Do the following:

1. Read `tasks.md` and `_progress.md`. Identify every task row for Phase $1.
2. Verify the GitHub milestone `Phase $1` exists; if not, call out that it should be created via `gh api POST /repos/Pratiyush/translately/milestones`.
3. Print a short "Phase $1 kickoff" summary: target tag, theme, task list, est-bucket totals.
4. Branch naming hint: suggest `feat/<scope>/<short-desc>` for each task.
5. Remind the caller: small PRs, GPG-signed commits, Conventional Commits titles, light+dark+a11y verified for any UI work, no AGPL copy from `_reference/`.

Do not modify files yet. Return a briefing the caller can act on.
