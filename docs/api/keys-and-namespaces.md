---
title: Keys, namespaces, translations
parent: API reference
nav_order: 7
---

# Keys, namespaces, translations

Project-scoped CRUD for the localization data model. Every endpoint is `@Authenticated`; authorization runs in the service layer via `requireProjectAccess`, so non-members see `NOT_FOUND` (404) — the server never discloses whether a private project exists.

Introduced by T208 backend (closes nothing alone; paired with the T207+T208 webapp PR to close #48 + #49). All bodies are `application/json`.

Related: [scopes](scopes.md), [errors](errors.md), [organizations-and-projects](organizations-and-projects.md).

## Namespaces

`GET /api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces` — list every namespace in the project.

```http
200 OK
{
  "data": [
    { "id": "01HT...", "slug": "web", "name": "Web", "description": null, "createdAt": "2026-04-19T10:00:00Z" }
  ]
}
```

`POST /api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces` — create.

```http
{ "name": "Web", "slug": "web", "description": "Web app strings" }
```

- `slug` optional; if omitted it's derived from `name` (lowercase, non-alphanumeric → `-`).
- 409 `NAMESPACE_SLUG_TAKEN` if the slug collides inside the same project.
- 400 `VALIDATION_FAILED` if `name` is empty or >128 chars.

## Keys

`GET /api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys` — list.

Query params:

- `namespace=<slug>` — filter by namespace.
- `limit=<int>` (default 50, max 200), `offset=<int>` (default 0).

`POST /keys` — create.

```http
{
  "namespaceSlug": "web",
  "keyName": "settings.save",
  "description": "Button label on the settings panel"
}
```

Triggers an `Activity(actionType=CREATED)` row.

`GET /keys/{keyId}` — single key with translations + tags + recent activity.

`PATCH /keys/{keyId}` — rename, change description, change state, move namespace.

```http
{ "keyName": "settings.save.button", "state": "TRANSLATING" }
```

Writes either `UPDATED` or `STATE_CHANGED` Activity depending on which fields changed.

`DELETE /keys/{keyId}` — soft-delete (sets `softDeletedAt`). Idempotent. Writes `DELETED` Activity.

## Translations

`PUT /api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys/{keyId}/translations/{languageTag}` — upsert the translation for a specific language.

```http
{ "value": "Save", "state": "DRAFT" }
```

- `languageTag` must match a configured `ProjectLanguage` on the project.
- `state` is optional; if omitted and `value` is non-empty, state flips to `DRAFT`. Explicit `APPROVED` requires reviewer scope (enforced at the resource).
- Writes `TRANSLATED` Activity.
- ICU validation of `value` is deferred — wire-up with T203 is a follow-up on this endpoint.

## Error codes specific to this surface

| Code | HTTP | When |
|---|---|---|
| `NOT_FOUND` | 404 | Target project / key / namespace doesn't exist, or caller is not a member |
| `VALIDATION_FAILED` | 400 | Missing required field, bad field length, unknown state enum value |
| `NAMESPACE_SLUG_TAKEN` | 409 | Namespace slug collision inside this project |
| `KEY_NAME_TAKEN` | 409 | `(namespace, keyName)` collision inside this project |
| `LANGUAGE_NOT_CONFIGURED` | 409 | `languageTag` on a translation upsert is not in the project's configured languages |
| `UNAUTHENTICATED` | 401 | No bearer credential on the request |

## What's NOT here yet

- **Tag resource.** Backend CRUD for tags lands with the webapp PR that needs it.
- **Search & filter.** Free-text + tag-intersection search is the dedicated FTS path — see T206 / #47 (the architecture page at `docs/architecture/search.md` lands alongside that PR).
- **Activity timeline endpoint.** The Activity rows are written but no `GET /keys/{keyId}/activity` is exposed yet; deferred with #46 post-MVP.

## Changelog

First shipped in [Unreleased] under T208 backend.
