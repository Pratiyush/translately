---
title: Quickstart
layout: default
nav_order: 2
permalink: /quickstart/
description: >-
  Ten-minute path from `docker compose up` to your first exported i18next
  JSON. Covers every Translately flow that shipped in v0.3.0.
---

# Quickstart — zero to first export in 10 minutes
{: .no_toc }

Everything below runs on a laptop. No cloud account, no API key, no paid tier. When you finish, you'll have a running Translately, a verified user, an organization, a project, a handful of keys with translations, and a downloaded `en.json` + `de.json`.

1. TOC
{: toc }

---

## What you need

- **Docker** (Desktop on macOS/Windows, or `docker` + `docker compose` on Linux). The stack brings up Postgres, Redis, MinIO, and Mailpit.
- **Java 21** + **Node 20** + **pnpm 9** if you're running the backend and webapp from source. Skip those if you use the packaged images instead — see [Skip the build](#skip-the-build) below.
- **10 minutes** of hands-on time. Builds run in the background.

---

## 1. Clone and bring up the stack

```bash
git clone https://github.com/Pratiyush/translately
cd translately
docker compose up -d
```

You should see four healthy services:

```bash
docker compose ps
# NAME               IMAGE                    STATUS (healthy)
# translately-pg     postgres:16-alpine       Up
# translately-redis  redis:7-alpine           Up
# translately-minio  minio/minio              Up
# translately-mail   axllent/mailpit          Up
```

Full walkthrough of what each service does and how to swap credentials is in [Self-hosting → Dev compose]({{ '/self-hosting/dev-compose/' | relative_url }}).

## 2. Start the backend

```bash
./gradlew :backend:app:quarkusDev
```

Flyway applies four migrations on first boot (`V1__auth_and_orgs.sql` through `V4__keys_fts_trigram.sql`); you should see:

```
INFO  [org.fly.cor.int.lic.VersionPrinter] Flyway OSS 10.x by Redgate
INFO  [org.fly.cor.int.com.DbMigrate] Successfully applied 4 migrations
INFO  [io.quarkus] Listening on: http://localhost:8080
```

Health check:

```bash
curl -s http://localhost:8080/q/health | jq .
# { "status": "UP", ... }
```

API is at `http://localhost:8080/api/v1/*`. OpenAPI schema at `http://localhost:8080/q/openapi`. Dev UI at `http://localhost:8080/q/dev-ui/`.

## 3. Start the webapp

In a second terminal:

```bash
pnpm --filter webapp install       # one-time
pnpm --filter webapp dev
```

Webapp is at **`http://localhost:5173`**. It hits the backend via the Vite dev proxy, so CORS is already configured.

## 4. Sign up + verify email

1. Open `http://localhost:5173` → you'll be redirected to `/signin`.
2. Click **Create an account** → fill in name, email (anything — it's a dev inbox), password (12+ chars).
3. Check [Mailpit](http://localhost:8025). A verification email is waiting. Click the link.
4. You're back on `/signin`. Enter the credentials you just set.

Alternative for reviewers: click **Seed dev user** on the sign-in page. Only present in DEV builds; stamps a verified user straight into `localStorage` without needing mail.

More detail: [Product → Authentication]({{ '/product/auth/' | relative_url }}).

## 5. Create an organization + project

First-time users land on `/orgs` with an empty state.

1. Click **Create organization** → pick a display name (e.g. "Acme Corp"). The slug (`acme`) is auto-derived.
2. You're taken to `/orgs/acme`. Click **Create project** on the Projects tab.
3. Name it "Web app", set `baseLanguageTag` to `en`. Slug auto-derives to `web-app`.

The project detail page opens at `/orgs/acme/projects/web-app`. Three tabs: Keys, Namespaces, Settings.

More detail: [Product → Organizations and projects]({{ '/product/organizations-and-projects/' | relative_url }}).

## 6. Import your first translations

New projects ship with one namespace (`default`). Skip creating more unless you want to group keys by feature.

1. On the **Keys** tab, click **Import**.
2. Paste this i18next JSON payload:

    ```json
    {
      "nav.signIn": "Sign in",
      "nav.signOut": "Sign out",
      "welcome.title": "Welcome back, {name}",
      "welcome.body": "You have {count, plural, one {# new message} other {# new messages}}."
    }
    ```

3. Keep defaults: language `en`, namespace `default`, mode `Merge`.
4. Click **Run import**.

Result panel shows `Imported 4 — 4 created, 0 updated, 0 skipped, 0 failed`. Close the dialog; the four keys now render in the table with state badge **New**.

The ICU expression in `welcome.body` passes validation because it declares an `other` branch for the `plural` argument. If you had written `{count, plural, one {# message}}`, the import would have landed that row in `errors[]` with code `INVALID_ICU_TEMPLATE` and kept the other three clean rows. Try it.

More detail: [Product → Imports and exports]({{ '/product/imports-and-exports/' | relative_url }}).

## 7. Translate a cell

Click into any value in the Translation column. Edit it. Blur (Tab out or click elsewhere) or press **⌘↵** (macOS) / **Ctrl↵** (Win/Linux) to save. The state badge flips from **Empty** → **Draft**.

Inline errors show under the textarea if the ICU fails to parse. Press **Escape** to revert to the server value.

## 8. Export back out

Click **Export** on the Keys tab.

1. Language: `en`. Shape: `FLAT`. Leave namespace + tags + minState blank.
2. Click **Download**. Browser saves `web-app-en-flat.json`:

    ```json
    {
      "nav.signIn": "Sign in",
      "nav.signOut": "Sign out",
      "welcome.title": "Welcome back, {name}",
      "welcome.body": "You have {count, plural, one {# new message} other {# new messages}}."
    }
    ```

Switch shape to `NESTED` for:

```json
{
  "nav": { "signIn": "Sign in", "signOut": "Sign out" },
  "welcome": { "title": "Welcome back, {name}", "body": "..." }
}
```

Both shapes round-trip cleanly — you can import the exported file back in without any information loss.

## 9. Where the data lives

- Postgres (`translately-pg`) holds every table — users, orgs, projects, namespaces, keys, translations, activity.
- Flyway migrations are in [`backend/data/src/main/resources/db/migration/`](https://github.com/Pratiyush/translately/tree/master/backend/data/src/main/resources/db/migration).
- MinIO holds screenshots once Phase 5 lands. Empty today.
- Redis caches nothing critical yet — Phase 4+ uses it for rate limits and AI-suggestion memoisation.

Drop the stack (`docker compose down -v` wipes volumes) to reset to a clean slate.

---

## Skip the build

If you don't want to compile anything:

```bash
docker compose up -d
# Pull the backend + webapp images
docker run -d --name translately-backend --network host \
  ghcr.io/pratiyush/translately-backend:v0.3.0
docker run -d --name translately-webapp --network host -p 5173:8080 \
  ghcr.io/pratiyush/translately-webapp:v0.3.0
```

Images are published automatically on every signed release tag. See [`release.yml`](https://github.com/Pratiyush/translately/blob/master/.github/workflows/release.yml).

---

## Next steps

- **Configure CI**: hook Translately into your repo's build. Create an API key with `imports.write` + `exports.read`, add it to your CI secrets, call `POST /imports/json` on every merge — see [`api/imports-and-exports.md`]({{ '/api/imports-and-exports/' | relative_url }}).
- **Harden before you expose**: read the [hardening checklist]({{ '/self-hosting/hardening/' | relative_url }}). Generate your own JWT keypair, Argon2 password, crypto master key.
- **Add an operator**: invite a teammate via the Org → Members tab (role change only — invite-by-email lands with Phase 7 SSO).
- **Wait for AI**: Phase 4 (v0.4.0) adds bring-your-own-key translation suggestions. The platform works end-to-end without it; AI is additive.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend fails on boot with `Flyway migration checksum mismatch` | You rolled back a branch that had a migration. | `docker compose down -v` to wipe; `docker compose up -d`; restart backend. |
| Import returns `404 Language not found` | Project has target languages configured and you tried to import a different tag. | Either import into one of the configured tags or remove the `ProjectLanguage` row (no UI yet — hit the DB). |
| Editor shows `INVALID_ICU_TEMPLATE` on a value you think is clean | ICU is picky about `{` / `}` / apostrophes. | Wrap literal braces as `'{{'` / `'}}'`. See [Architecture → ICU validation]({{ '/architecture/icu-validation/' | relative_url }}). |
| Webapp shows "Couldn't load organizations" on `/orgs` | Backend not running or token expired. | Check `http://localhost:8080/q/health`; sign out + sign back in. |

Full error catalogue: [`api/errors.md`]({{ '/api/errors/' | relative_url }}).
