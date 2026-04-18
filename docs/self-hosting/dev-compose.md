---
title: Dev compose
parent: Self-hosting
nav_order: 2
---

# Dev compose

Walkthrough of the committed [`docker-compose.yml`](https://github.com/Pratiyush/translately/blob/master/docker-compose.yml) at the repo root. This stack is what contributors run locally when they work on the backend or webapp — it boots Postgres, Redis, MinIO, Mailpit, and (optionally) Keycloak in one command.

For the production counterpart, jump to [`compose-prod.yml` diff](#compose-prodyml-diff-for-production). For hardening before you expose anything to the internet, read the [hardening checklist](hardening.md) first.

## Quick reference

```bash
docker compose up -d                          # core services (Postgres, Redis, MinIO, Mailpit)
docker compose --profile keycloak up -d       # add Keycloak for OIDC testing
docker compose ps                             # list services + health
docker compose logs -f <service>              # tail logs
docker compose down                           # stop; named volumes preserved
docker compose down -v                        # stop AND wipe all data volumes
```

| Service | URL | Credentials | Purpose |
|---|---|---|---|
| Postgres | `postgres://translately:translately@localhost:5432/translately` | user `translately` / pass `translately` | Primary datastore |
| Redis | `redis://localhost:6379` | none (dev) | Cache, rate limits, sliding-window counters |
| MinIO API | `http://localhost:9000` | `translately` / `translately-dev` | S3-compatible object storage |
| MinIO console | `http://localhost:9001` | `translately` / `translately-dev` | Web UI for inspecting buckets |
| Mailpit | `http://localhost:8025` (web), `localhost:1025` (SMTP) | no auth | Dev mail sink — catches every outbound email |
| Keycloak | `http://localhost:8180` | `admin` / `admin` | Optional OIDC IdP (profile: `keycloak`) |

The stack uses Compose project name `translately`, so container names are deterministic (`translately-postgres`, `translately-redis`, etc.) — handy for `docker exec`.

## Service-by-service

### `postgres` — PostgreSQL 16

Image: `postgres:16-alpine`. Container: `translately-postgres`. Port `5432` bound to the host.

Environment:

| Variable | Dev value | Production analogue |
|---|---|---|
| `POSTGRES_USER` | `translately` | `POSTGRES_USER` (env-file supplied) |
| `POSTGRES_PASSWORD` | `translately` | `POSTGRES_PASSWORD` (required, no default) |
| `POSTGRES_DB` | `translately` | `POSTGRES_DB` |

Volumes:

- `postgres-data:/var/lib/postgresql/data` — named volume; survives `docker compose down`, wiped by `docker compose down -v`.
- `./infra/postgres/init:/docker-entrypoint-initdb.d:ro` — run-once init SQL. Ships [`01-keycloak-db.sql`](https://github.com/Pratiyush/translately/blob/master/infra/postgres/init/01-keycloak-db.sql), which idempotently creates the auxiliary `keycloak` database when the Keycloak profile is enabled.

Healthcheck: `pg_isready -U translately -d translately`, polled every 5s.

Production changes:

- Use a managed Postgres (AWS RDS, Cloud SQL, Crunchy) or at least TLS + `scram-sha-256` auth. The dev image ships with trust auth for `localhost`.
- Swap the dev password for a 32-byte random secret (`openssl rand -hex 20`).
- Scheduled backups + periodic restore drills (quarterly minimum).

### `redis` — Redis 7

Image: `redis:7-alpine`. Container: `translately-redis`. Port `6379` bound to the host.

Command overrides (applied via Compose `command:`):

- `--appendonly yes` — enables AOF persistence so cache state survives restart.
- `--maxmemory 256mb` — dev cap; production is `512mb` in `compose-prod.yml`.
- `--maxmemory-policy allkeys-lru` — evict least-recently-used keys when full.

Volumes: `redis-data:/data` — persists the AOF log.

Healthcheck: `redis-cli ping`.

Production changes:

- Increase `--maxmemory` to match real load (start at 512mb; tune from metrics).
- Add `--requirepass` and a TLS listener if Redis is network-reachable beyond the Compose network.
- Consider Redis Sentinel or a managed Redis for HA.

### `minio` + `minio-init` — S3-compatible object storage

**`minio`** (image: `minio/minio:latest`, container: `translately-minio`) runs the server on ports `9000` (API) and `9001` (console).

Environment:

| Variable | Dev value | Production analogue |
|---|---|---|
| `MINIO_ROOT_USER` | `translately` | `MINIO_ROOT_USER` (required) |
| `MINIO_ROOT_PASSWORD` | `translately-dev` | `MINIO_ROOT_PASSWORD` (required) |

Volume: `minio-data:/data`.

Healthcheck: HTTP `GET /minio/health/live`.

**`minio-init`** (image: `minio/mc:latest`, container: `translately-minio-init`) is a one-shot sidecar. It waits for MinIO to become healthy, then uses `mc` to create the buckets the backend expects and marks `translately-cdn` anonymous-readable:

```
mc mb --ignore-existing local/translately-screenshots
mc mb --ignore-existing local/translately-cdn
mc mb --ignore-existing local/translately-imports
mc anonymous set download local/translately-cdn
```

Bucket purposes:

- `translately-screenshots` — reviewer screenshot uploads for translation context.
- `translately-cdn` — published content bundles (public-read so browsers can fetch without signed URLs).
- `translately-imports` — staged source-file uploads before they're parsed into the TM.

The sidecar has `restart: "no"` and exits `0` after it finishes — `docker compose ps` will show it as `Exited (0)`, which is expected.

Production changes:

- Swap MinIO for the real thing (AWS S3, GCS via S3 interop, Cloudflare R2, Backblaze B2). The backend speaks the S3 API — set `TRANSLATELY_STORAGE_S3_ENDPOINT`, `_ACCESS_KEY`, `_SECRET_KEY`, `_REGION`.
- Apply lifecycle rules (expire screenshot uploads after N days).
- If staying on MinIO, enable TLS, swap the root password, and put it behind a reverse proxy.
- Only the CDN bucket should be anonymous-readable. Keep `-screenshots` and `-imports` private.

### `mailpit` — Dev SMTP sink

Image: `axllent/mailpit:latest`. Container: `translately-mailpit`. Ports `1025` (SMTP) and `8025` (web UI).

Environment:

| Variable | Value | Why |
|---|---|---|
| `MP_SMTP_AUTH_ACCEPT_ANY` | `true` | Accept any SMTP credentials — tests don't need real auth. |
| `MP_SMTP_AUTH_ALLOW_INSECURE` | `true` | Allow plaintext auth; dev only. |
| `MP_MAX_MESSAGES` | `500` | Ring-buffer cap on stored messages. |

Volume: `mailpit-data:/data`.

Healthcheck: `GET /livez`.

Every outbound email sent by the backend in dev lands in Mailpit — open `http://localhost:8025` to see signup verifications, password resets, webhook failure alerts, etc.

Production changes: **do not** ship Mailpit. Point `TRANSLATELY_MAIL_*` at a real SMTP provider (Amazon SES, Postmark, SendGrid, Mailgun) with auth + TLS + a dedicated sender domain (SPF, DKIM, DMARC).

### `keycloak` — Optional OIDC IdP

Image: `quay.io/keycloak/keycloak:25.0`. Container: `translately-keycloak`. Port `8180` bound to the host (maps to Keycloak's internal `8080`).

Activated only with `docker compose --profile keycloak up -d`. The core stack omits it so laptops aren't burning the extra RAM.

Environment:

| Variable | Value | Notes |
|---|---|---|
| `KEYCLOAK_ADMIN` / `_PASSWORD` | `admin` / `admin` | Master realm admin creds (dev only). |
| `KC_DB` | `postgres` | Reuse the stack's Postgres. |
| `KC_DB_URL` | `jdbc:postgresql://postgres:5432/keycloak` | The `keycloak` DB is created by [`infra/postgres/init/01-keycloak-db.sql`](https://github.com/Pratiyush/translately/blob/master/infra/postgres/init/01-keycloak-db.sql). |
| `KC_DB_USERNAME` / `_PASSWORD` | `translately` / `translately` | Shares the app Postgres role. |
| `KC_HOSTNAME_STRICT` | `false` | Disable strict hostname checks for localhost. |
| `KC_HTTP_ENABLED` | `true` | HTTP for dev; TLS required in prod. |

Volumes: `./infra/keycloak/realms:/opt/keycloak/data/import:ro` — seed realms/clients for testing (currently a placeholder).

Depends on Postgres being healthy (Keycloak needs its DB up).

Production changes: Keycloak gets its own deployment (not hobbled into a single Compose file). See the Phase 7 Helm chart.

## First-run smoke checks

After `docker compose up -d`, wait ~15 seconds for healthchecks, then:

```bash
# Everything should be Up (healthy)
docker compose ps

# Postgres
docker exec translately-postgres psql -U translately -d translately -c 'SELECT version();'

# Redis
docker exec translately-redis redis-cli ping   # -> PONG

# MinIO (from the host)
curl -fsS http://localhost:9000/minio/health/live && echo OK

# Buckets were created by minio-init
docker exec translately-minio mc ls local/   # (or open http://localhost:9001)

# Mailpit
curl -fsS http://localhost:8025/livez && echo OK

# Keycloak (only if --profile keycloak)
curl -fsS http://localhost:8180/realms/master/.well-known/openid-configuration | head -1
```

If the backend is also running, confirm it connects:

```bash
./gradlew :backend:app:quarkusDev             # in another terminal
curl -fsS http://localhost:8080/q/health/ready   # -> {"status":"UP", ...}
```

`docker compose logs -f postgres redis minio mailpit` is your friend when a healthcheck never flips to `healthy`.

## `compose-prod.yml` diff for production

[`infra/compose-prod.yml`](https://github.com/Pratiyush/translately/blob/master/infra/compose-prod.yml) is the single-host production variant. Same service set plus `backend` and `webapp`, but meaningfully tighter. Key differences:

| Aspect | Dev (`docker-compose.yml`) | Prod (`infra/compose-prod.yml`) |
|---|---|---|
| Backend + webapp | Run on the host (`./gradlew quarkusDev`, `pnpm dev`) | Containerised from `ghcr.io/pratiyush/translately-{backend,webapp}:${TRANSLATELY_VERSION}` |
| Postgres creds | Hardcoded `translately` / `translately` | `${POSTGRES_PASSWORD:?...}` — fails fast if the env-file is missing |
| MinIO creds | Hardcoded `translately` / `translately-dev` | `${MINIO_ROOT_USER:?...}` / `${MINIO_ROOT_PASSWORD:?...}` |
| Mailpit | Present | **Absent** — point `TRANSLATELY_MAIL_*` at real SMTP |
| Keycloak | Behind `--profile keycloak` | **Absent** — deploy separately |
| MinIO init sidecar | Creates buckets automatically | **Absent** — create buckets once on your real object store |
| Redis max memory | `256mb` | `512mb` |
| Backend port binding | n/a (runs on host) | `127.0.0.1:8080:8080` — bind-localhost, reverse-proxy in front |
| Webapp port binding | n/a (Vite on `:5173`) | `127.0.0.1:8081:8080` — bind-localhost, reverse-proxy in front |
| Required secrets | None (dev defaults) | `JWT_SECRET`, `CRYPTO_MASTER_KEY`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`, `SMTP_*` — all `:?required` |
| Healthchecks | Services only | Backend adds `/q/health/ready` probe |
| Volumes | Named dev volumes | Same layout; you own the backup policy |

Bring-up:

```bash
cp infra/.env.prod.example .env.prod
# edit secrets with `openssl rand -base64 32`
docker compose -f infra/compose-prod.yml --env-file .env.prod up -d
```

Read the [hardening checklist](hardening.md) before you expose the stack to the internet, and follow the [runtime profiles](runtime-profiles.md) page for the `%prod` env vars the backend itself needs.

For multi-node / HA / Kubernetes deployments, wait for the [Phase 7 Helm chart](https://github.com/Pratiyush/translately/blob/master/infra/helm/) or roll your own orchestration.

## Teardown and data wipe

```bash
# Stop services; keep data (DB, object store, AOF, Mailpit buffer)
docker compose down

# Stop AND wipe named volumes — guaranteed fresh start next `up`
docker compose down -v

# Remove everything the stack owns (images too)
docker compose down -v --rmi local

# Just reset Postgres without touching MinIO / Redis
docker compose rm -sfv postgres
docker volume rm translately_postgres-data
docker compose up -d postgres
```

`docker compose down -v` is the hammer for "my local DB is in a weird state" — it wipes `postgres-data`, `redis-data`, `minio-data`, and `mailpit-data`. The MinIO init sidecar reruns on the next `up` and recreates the buckets.
