---
title: Imports and exports
parent: Product
nav_order: 7
permalink: /product/imports-and-exports.html
---

# Imports and exports — i18next JSON

Moves translations in and out of Translately in the [i18next JSON format](https://www.i18next.com/misc/json-format) that most modern frontends already speak. Shipped under T301 (import backend), T302 (export backend), T304 (import wizard), and T305 (export modal) in Phase 3. Pairs with the [API reference](../api/imports-and-exports.md).

Lives on the **Keys** tab of the project detail route — two buttons in the top-right: **Import** and **Export**.

## Import wizard

Click **Import**. The wizard asks for:

- **Language tag** — BCP-47 (e.g. `en`, `de`, `fr-CA`). Must be configured on the project.
- **Namespace** — auto-created if it doesn't exist yet. Defaults to the namespace currently filtered in the table (or `default`).
- **Conflict mode** — one of:
  - **Merge** (default) — fill only blank cells. Existing non-blank values survive.
  - **Keep** — never touch an existing cell; only new keys get translations.
  - **Overwrite** — replace every existing value with the imported one.
- **JSON payload** — paste the file contents or drop a `.json` file. Flat and nested shapes are both accepted.

Click **Run import**. Every row runs through the [ICU validator](../architecture/icu-validation.md); invalid rows land in the result panel with a `[CODE] path — message` line so you can fix and re-import. Clean rows still commit — the result panel shows a summary: `Imported N — X created, Y updated, Z skipped, W failed`.

### Accepted payload shapes

Flat:

```json
{
  "nav.signIn": "Sign in",
  "nav.signOut": "Sign out"
}
```

Nested:

```json
{
  "nav": {
    "signIn": "Sign in",
    "signOut": "Sign out"
  }
}
```

Both produce the same (key name, value) pair stream. Non-string leaves are coerced to strings; arrays are rejected with a jq-style path (`nav.items`) so you can pinpoint the offender.

## Export modal

Click **Export**. The modal asks for:

- **Language tag** — which locale to dump.
- **Shape** — **Flat** (dotted keys) or **Nested** (tree).
- **Namespace** — optional. Blank exports every namespace.
- **Tags** — comma-separated. Keys must carry **every** listed tag (AND intersection).
- **Minimum state** — optional. Pick `APPROVED` for production dumps, blank to include drafts. States are totally ordered: `EMPTY < DRAFT < TRANSLATED < REVIEW < APPROVED`.

Click **Download**. The browser saves the file as `{projectSlug}-{languageTag}-{shape}.json`.

## Out of scope for v0.3.0

- Async + Quartz + SSE progress streaming for very large payloads (T303, Phase 4).
- Multi-language single-call import (current API is one language per request).
- Import preview / diff before commit — use the `Keep` mode to stage then review.
- XLIFF + gettext + Android + iOS formats — Phase 6 CLI adds more formats.
