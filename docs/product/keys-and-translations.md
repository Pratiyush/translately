---
title: Keys, namespaces, and the translation editor
parent: Product
nav_order: 6
permalink: /product/keys-and-translations.html
---

# Keys, namespaces, and the translation editor

Every project has three first-class concerns: **keys** (the message IDs your app ships), **namespaces** (how those keys are grouped), and **translations** (one value per (key, language) pair). The project detail route surfaces all three.

Shipped under T207 (table UX, editor) + T208 (key / namespace CRUD) in Phase 2. Pairs with the backend reference at [API → Keys and namespaces](../api/keys-and-namespaces.md).

## Route

- `/orgs/:orgSlug/projects/:projectSlug` — project detail with **Keys** / **Namespaces** / **Settings** tabs.
- Project tiles under `/orgs/:orgSlug` (Projects tab) and `/projects` link here.

Every page lives inside the authenticated shell. A visitor without a session is bounced through [`RequireAuth`](app-shell.md#requireauth) to `/signin`.

## Keys tab — the translation table

The Keys tab is the primary translator surface.

### Layout

- **Sticky first column** with the key name + namespace slug, so the key stays visible while you scroll the translation column on narrow viewports.
- **State badge** per row: `NEW`, `TRANSLATING`, `REVIEW`, `DONE`, `ARCHIVED` (the whole-key state; ADR 0002 documents the 5-state machine).
- **Editor cell** per row — a resizing textarea holding the base-language translation (full multi-language grid ships in a later milestone; Phase 2 ships single-column editing).
- **Action column** with a delete button (soft-delete, confirm dialog).

### Namespace filter

A `<select>` above the table scopes the list to a single namespace. Selecting "All namespaces" clears the filter. Creating a namespace via the Namespaces tab adds it to the filter immediately.

### Autosave editor

- Typing marks the cell **dirty** (status line says "Unsaved — blur or ⌘↵ to save").
- **Blur** or **⌘ Enter** (Windows: **Ctrl Enter**) saves via `PUT /keys/{keyId}/translations/{languageTag}`.
- **Escape** reverts to the server value without saving.
- Save outcome shows inline: "Saving…" → "Saved" or the localised error text (`INVALID_ICU_TEMPLATE`, `VALIDATION_FAILED`, etc. — see the [error catalogue](../api/errors.md)).

### Create a key

Click **New key**. The dialog asks for:

- **Key name** — 1..255 chars, matching `^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$` (letters, digits, `. _ -` as separators).
- **Namespace** — picked from the project's namespaces; defaults to the first entry.
- **Description** — optional translator-facing hint shown in activity logs (and future translator workspaces).

Submitting invalidates the keys query; the new row appears in the table immediately.

### Delete a key

The trash button opens a confirm dialog. Delete is a **soft-delete**: the row is tagged `soft_deleted_at`, removed from listings, and recoverable via the API. Translations are preserved on the row so nothing is lost when the key is restored.

## Namespaces tab

The Namespaces tab lists every namespace in the project with its name + slug + description. **New namespace** opens a dialog with:

- **Name** — 1..128 chars.
- **URL slug** — optional; derived from the name when blank. Must be unique inside the project (409 `NAMESPACE_SLUG_TAKEN` surfaces inline).
- **Description** — optional.

Rename + delete are on the Phase 3+ roadmap; MVP-scope is list + create because the key-create flow needs at least one namespace per project.

## Settings tab

Reserved for the project-settings resource landing with Phase 3. Currently a placeholder.

## Accessibility

- Textarea has a visually hidden label (`"Translation in {languageTag}"`) so screen readers announce it.
- State badges use colour + text, not colour alone.
- The table's sticky column is keyboard-accessible via tab navigation; cell-to-cell arrow-key navigation is deferred to the post-v0.2.0 polish ticket.
- Save status is `aria-live="polite"` so screen readers announce "Saved" without interrupting.

## Out of scope for v0.2.0

The following T207 polish items ship in a later ticket (flagged `post-mvp`):

- CodeMirror 6 ICU syntax highlighting in the editor cell.
- Full keyboard-grid navigation (arrow keys between cells, Tab to next row).
- Multi-language translation columns (render one cell per configured project language).
- Activity log panel per key (T209 — moved to Phase 7 alongside audit).
