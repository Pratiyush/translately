---
description: Cut a signed release tag — bump version, finalize CHANGELOG + RELEASE-NOTES, create signed tag, push
allowed-tools:
  - Bash
  - Read
  - Edit
argument-hint: "<version>  # e.g. 0.1.0"
---

Cut Translately release **v$1**.

Preconditions to verify:

1. On `master`, working tree clean, up-to-date with `origin/master`.
2. All CI checks on `origin/master` are green.
3. GPG key `46AF5131F4540993C2C17E129BDF8BAF8DBBDF08` is present and signing works (`echo test | gpg --clearsign --batch --pinentry-mode=loopback`).

Steps:

1. Update `gradle.properties` → `translately.version=$1`.
2. Edit `CHANGELOG.md` — move `## [Unreleased]` content to `## [$1] — $(date +%Y-%m-%d)`. Keep the link-reference block at the bottom updated (`[$1]: ...releases/tag/v$1`).
3. Edit `RELEASE-NOTES.md` — prepend a new narrative section under `## v$1 — Phase N: <theme>` covering: headline, what's new, migration notes, known limitations, what's next.
4. Commit (GPG-signed): `chore(release): v$1`.
5. Tag (GPG-signed): `git tag -s v$1 -m "v$1 — Phase N: <theme>"`.
6. Push: `git push origin master --follow-tags`.
7. Confirm `release.yml` workflow fires via `gh run watch`.
8. Verify the GitHub Release appears and is marked as prerelease for any `0.x` tag.
9. Announce in the weekly log inside `_progress.md`.

Do **not** use `--no-verify` or `--amend`. If a hook fails, fix and commit anew.
