---
title: Organizations, projects, members
parent: API reference
nav_order: 6
---

# Organizations, projects, and members

CRUD surface that v0.1.0 ships so the webapp's org/project pages have something to call. Every endpoint is `@Authenticated`; authorization is scoped to **organization membership** — non-members get `NOT_FOUND` (404) so the server never discloses whether a private org exists.

Introduced by T118 (orgs UI) + T119 (projects UI + member management) + the matching backend work. All bodies are `application/json`.

Related: [scopes](scopes.md), [errors](errors.md), [authentication endpoints](auth.md).

## Organizations

`GET /api/v1/organizations` — list every org the caller belongs to.

```http
200 OK
{
  "data": [
    {
      "id": "01HT...",
      "slug": "acme",
      "name": "Acme Corp",
      "callerRole": "OWNER",
      "createdAt": "2026-04-18T10:45:00Z"
    }
  ]
}
```

`POST /api/v1/organizations` — create a new org; the caller is added as OWNER.

```http
{ "name": "Acme Corp", "slug": "acme" }
```

- `slug` is optional; if omitted, we derive one from `name` (lowercase, non-alphanumeric → `-`, trimmed).
- 201 on success with the full body shown above.
- 409 `ORG_SLUG_TAKEN` if the slug is already in use — slugs are unique globally.
- 400 `VALIDATION_FAILED` if `name` is empty / >128 chars, or the derived slug is unusable.

`GET /api/v1/organizations/{orgSlug}` — single org (ULID or slug both accepted). 404 if you're not a member.

`PATCH /api/v1/organizations/{orgSlug}` — rename.

```http
{ "name": "Acme International" }
```

Returns the updated body. Other fields (billing, BYOK AI config) are not editable in v0.1.0.

## Members

`GET /api/v1/organizations/{orgSlug}/members` — list members. Any member can call.

```http
200 OK
{
  "data": [
    {
      "userId": "01HT...",
      "email": "alice@example.com",
      "fullName": "Alice Example",
      "role": "OWNER",
      "invitedAt": "2026-04-18T10:45:00Z",
      "joinedAt": "2026-04-18T10:45:00Z"
    }
  ]
}
```

`PATCH /api/v1/organizations/{orgSlug}/members/{userId}` — change a member's role. Caller must be OWNER or ADMIN.

```http
{ "role": "ADMIN" }
```

- 400 `VALIDATION_FAILED` (`body.role = INVALID`) if the role isn't one of `OWNER` / `ADMIN` / `MEMBER`.
- 409 `LAST_OWNER` if the change would leave the org with zero OWNERs.

`DELETE /api/v1/organizations/{orgSlug}/members/{userId}` — remove a member. Idempotent target (404 if they aren't a member).

- 409 `LAST_OWNER` if removing the target would leave the org with zero OWNERs.

### What about invites?

Explicitly **not in v0.1.0.** The invite-by-email + pending-acceptance lifecycle needs the token-email plumbing that SSO / SAML / LDAP (Phase 7) brings. Until then, members grow through self-serve org creation — each user makes their own org and runs solo, or an existing member promotes them via the PATCH endpoint once their `sub` is known.

## Projects

`GET /api/v1/organizations/{orgSlug}/projects` — list every project in the org.

```http
200 OK
{
  "data": [
    {
      "id": "01HT...",
      "slug": "marketing",
      "name": "Marketing site",
      "description": "Website copy",
      "baseLanguageTag": "en",
      "createdAt": "2026-04-18T10:45:00Z"
    }
  ]
}
```

`POST /api/v1/organizations/{orgSlug}/projects` — create.

```http
{
  "name": "Marketing site",
  "slug": "marketing",
  "description": "Website copy",
  "baseLanguageTag": "en"
}
```

- `slug` optional; derived from `name` when absent.
- `description` optional.
- `baseLanguageTag` defaults to `"en"` when absent.
- 409 `PROJECT_SLUG_TAKEN` if the slug collides within the same org (slugs are unique per org, not globally).

`GET /api/v1/organizations/{orgSlug}/projects/{projectSlug}` — single project.

`PATCH /api/v1/organizations/{orgSlug}/projects/{projectSlug}` — rename / edit description.

```http
{ "name": "Marketing Site 2.0", "description": null }
```

Pass `null` / empty string on `description` to clear it. The `baseLanguageTag` is immutable in v0.1.0 (Phase 2 adds a migration path).

## Error responses

All endpoints use the [uniform error envelope](errors.md). The codes specific to this surface:

| Code | HTTP | When |
|---|---|---|
| `NOT_FOUND` | 404 | Target org / project / member doesn't exist, or caller is not a member of the target org |
| `VALIDATION_FAILED` | 400 | Name empty / too long, slug unparseable, role unknown |
| `ORG_SLUG_TAKEN` | 409 | Slug already in use globally |
| `PROJECT_SLUG_TAKEN` | 409 | Slug already in use inside this org |
| `LAST_OWNER` | 409 | Membership change would orphan the org |
| `UNAUTHENTICATED` | 401 | No JWT on the request |

## Changelog

First shipped in [v0.1.0](../../CHANGELOG.md) (Phase 1 close-out).
