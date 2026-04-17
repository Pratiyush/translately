---
description: Sync webapp UI strings back into the dogfooding project — verify `webapp/src/i18n/en.json` vs live Translately project `translately-webapp`
allowed-tools:
  - Bash
  - Read
  - Grep
---

The webapp dogfoods Translately for its own UI strings. This command verifies the two sides are in sync.

1. Read `webapp/src/i18n/en.json`. List keys.
2. If the Translately CLI is installed: `pnpm --filter @translately/cli -- pull --project translately-webapp --lang en --output /tmp/tr-en.json` and diff.
3. If the CLI is not yet built (Phase 6+), call the API directly with the project token: `curl -H "Authorization: ApiKey $TRANSLATELY_WEBAPP_KEY" https://translately.example/api/v1/projects/translately-webapp/export?lang=en`.
4. Report: keys added, keys removed, values differing. Suggest the PR delta (`push` direction) so the remote project matches the committed `en.json`.
5. Flag any hard-coded English in webapp components (grep for JSX string literals that aren't going through the i18n wrapper).

Read-only unless the caller explicitly asks to push. Dogfood strings are source-of-truth in `webapp/src/i18n/en.json`.
