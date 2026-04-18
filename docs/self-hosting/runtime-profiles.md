---
title: Runtime profiles
parent: Self-hosting
nav_order: 3
---

# Runtime profiles

Translately's backend is a Quarkus application. Quarkus ships a built-in profile mechanism — `%dev`, `%test`, `%prod` — that lets a single [`application.yml`](https://github.com/Pratiyush/translately/blob/master/backend/app/src/main/resources/application.yml) carry three sets of config. Understanding which profile is active and what env vars it requires is the difference between a clean boot and a stack trace.

Companion pages: [dev compose](dev-compose.md) for the services those profiles talk to, [hardening checklist](hardening.md) for what to wire on top in production.

## What activates each profile

| Profile | How to activate | When Quarkus picks it |
|---|---|---|
| `%dev` | Default for `./gradlew quarkusDev`. Also selected by `-Dquarkus.profile=dev`. | Live-reload dev loop. |
| `%test` | Auto-activated when `@QuarkusTest` / `@QuarkusIntegrationTest` runs (the JUnit 5 extensions set it before any `@ApplicationScoped` bean wakes up). | Every backend test run — unit, integration, IT, coverage. |
| `%prod` | Default for packaged artefacts — the fast-jar (`java -jar quarkus-run.jar`), the native binary (`./translately`), and the official images (`ghcr.io/pratiyush/translately-backend:*`). Also selected by `-Dquarkus.profile=prod`. | Every `compose-prod.yml` / Helm / bare-metal deploy. |

No profile = "default" — the top-level config in `application.yml` that applies to all three. The `"%name":` blocks layer on top and **override** default values for that profile only.

Multiple profiles can be active at once (`-Dquarkus.profile=prod,oidc`). Phase 7 introduces a `%oidc` overlay profile for Keycloak-SSO testing; it stacks onto `%prod`.

## Default config (all profiles)

The top-level block in `application.yml` sets values that hold unless a profile overrides them:

- **`translately.crypto.master-key`** — 32-byte base64 key used to envelope-encrypt per-project BYOK secrets. The committed default (`AAECAwQFBg…`, bytes 0x00–0x1F) is a **clearly non-secret dev placeholder**. `%prod` overrides it; see below.
- **`translately.jwt`** — issuer `translately`, audience `translately-webapp`, access TTL 15m, refresh TTL 30 days.
- **`mp.jwt.verify.publickey.location`** and **`smallrye.jwt.sign.key.location`** — default to `classpath:/jwt-dev/{public,private}.pem`. Those dev keys ship in the JAR and are **not secrets**. `%prod` overrides with operator-supplied paths.
- **`quarkus.http`** — port 8080, host `0.0.0.0`, proactive auth on, CORS allowing `http://localhost:5173` and `:5173` (the Vite dev server). CORS tightens when you put the webapp behind the same origin in production (reverse-proxy setup).
- **`quarkus.datasource.db-kind: postgresql`**, dev-services disabled. Hibernate ORM and Flyway both default to `active: false` — Phase 0 has no entities or migrations yet. Each phase flips these on when it lands real data.
- **`quarkus.oidc.enabled: false`** — OIDC extension is on the classpath (for Phase 7 Keycloak/SAML) but disabled by default so `smallrye-jwt` cleanly owns `JsonWebToken` production.
- **`quarkus.security.ldap`** — inert placeholder values so the extension validates at runtime without talking to a real LDAP server. Real wiring ships with Phase 7.

## `%dev` overrides

Activated by `./gradlew :backend:app:quarkusDev`. Assumes the dev Docker Compose stack (Postgres + Redis + MinIO + Mailpit) is running at its default localhost ports — see [dev compose](dev-compose.md).

From `application.yml`:

```yaml
"%dev":
  quarkus:
    log:
      category:
        "io.translately":
          level: DEBUG
    datasource:
      username: translately
      password: translately
      jdbc:
        url: jdbc:postgresql://localhost:5432/translately
```

What this gives you:

- `io.translately.**` logs at `DEBUG` (everything else stays `INFO`).
- Postgres JDBC pinned at `localhost:5432` with the hardcoded dev creds from `docker-compose.yml`.

**Required env vars in dev:** none. Everything boots against the committed defaults. If you want to add your own AI keys for BYOK testing, configure them through the UI / API — they encrypt with the dev master key and land in Postgres.

## `%test` overrides

Auto-activated when JUnit 5 instantiates a class annotated `@QuarkusTest` or `@QuarkusIntegrationTest`. There are 13+ such classes under `backend/app/src/test/` (see `@QuarkusTest` call sites in the repo).

```yaml
"%test":
  quarkus:
    devservices:
      enabled: false
    datasource:
      devservices:
        enabled: false
    s3:
      devservices:
        enabled: false
    keycloak:
      devservices:
        enabled: false
```

Deliberate shape:

- **All Quarkus dev-services disabled.** Phase 0 tests don't need a running database — they exercise the service layer with mocks or in-memory substitutes. Tests that _do_ need Postgres (like the auth IT suite) opt in with `@QuarkusTestResource(PostgresAndMailpitResource::class)` to stand up Testcontainers explicitly. Tests that don't opt in stay fast.
- **Secrets fall back to the committed dev values.** JWT keypair from `classpath:/jwt-dev/*`, master key from the default placeholder. No env vars needed.

**Required env vars in test:** none. Testcontainers needs Docker running; that's the only external dependency.

## `%prod` overrides

Activated by the packaged artefacts (fast-jar, native binary, official container images) and by `-Dquarkus.profile=prod`. This is the profile you'll actually operate.

```yaml
"%prod":
  translately:
    crypto:
      master-key: ${TRANSLATELY_CRYPTO_MASTER_KEY}
  mp:
    jwt:
      verify:
        publickey:
          location: ${TRANSLATELY_JWT_PUBLIC_KEY_PATH}
  smallrye:
    jwt:
      sign:
        key:
          location: ${TRANSLATELY_JWT_PRIVATE_KEY_PATH}
  quarkus:
    log:
      level: INFO
    datasource:
      username: ${QUARKUS_DATASOURCE_USERNAME}
      password: ${QUARKUS_DATASOURCE_PASSWORD}
      jdbc:
        url: ${QUARKUS_DATASOURCE_JDBC_URL}
```

Every `${...}` is a **required** env var. Quarkus config resolution throws on unresolved expressions at boot, so a missing secret = the app fails to start rather than silently reusing a dev placeholder. This is intentional: it's far better to crash than to encrypt real customer data under the committed-to-Git placeholder key.

### Required env vars (`%prod`)

| Env var | Purpose | Example |
|---|---|---|
| `TRANSLATELY_CRYPTO_MASTER_KEY` | 32-byte base64 KEK for envelope-encrypting per-project BYOK secrets (AI API keys, PATs). | `openssl rand -base64 32` |
| `TRANSLATELY_JWT_PUBLIC_KEY_PATH` | Filesystem path (or `classpath:` URL) to the JWT verification public key (PEM). | `/etc/translately/jwt.pub.pem` |
| `TRANSLATELY_JWT_PRIVATE_KEY_PATH` | Filesystem path (or `classpath:` URL) to the JWT signing private key (PEM). | `/etc/translately/jwt.priv.pem` |
| `QUARKUS_DATASOURCE_USERNAME` | Postgres role. | `translately_app` |
| `QUARKUS_DATASOURCE_PASSWORD` | Postgres role password. | (secret) |
| `QUARKUS_DATASOURCE_JDBC_URL` | Full JDBC URL (host, port, DB, SSL mode). | `jdbc:postgresql://pg.internal:5432/translately?sslmode=require` |

Additional env vars consumed via the backend's service config (set in `infra/compose-prod.yml`, not in the `%prod` block of `application.yml` — they're read by the storage / mail / auth modules directly):

| Env var | Purpose |
|---|---|
| `QUARKUS_REDIS_HOSTS` | Redis connection string (e.g. `redis://redis:6379`). |
| `TRANSLATELY_STORAGE_S3_ENDPOINT` / `_REGION` / `_ACCESS_KEY` / `_SECRET_KEY` | S3-compatible object storage (MinIO, S3, GCS via S3 interop, R2, B2). |
| `TRANSLATELY_MAIL_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` / `_FROM` | Real SMTP credentials — **do not** run Mailpit in production. |
| `TRANSLATELY_AUTH_JWT_SECRET` | Legacy HS256 secret (alongside the RSA keypair paths). |

See [`infra/.env.prod.example`](https://github.com/Pratiyush/translately/blob/master/infra/.env.prod.example) for a copy-paste-ready template.

### Generating the secrets

```bash
# 32-byte base64 — TRANSLATELY_CRYPTO_MASTER_KEY and TRANSLATELY_AUTH_JWT_SECRET
openssl rand -base64 32

# Database / MinIO passwords (hex is fine)
openssl rand -hex 20

# RS256 JWT keypair for TRANSLATELY_JWT_{PRIVATE,PUBLIC}_KEY_PATH
openssl genrsa -out jwt.priv.pem 2048
openssl rsa -in jwt.priv.pem -pubout -out jwt.pub.pem
chmod 600 jwt.priv.pem
```

Mount the two PEMs into the container (read-only volume) and point the env vars at them.

### Rotation policy

- **`TRANSLATELY_CRYPTO_MASTER_KEY`** is the KEK. Rotating it requires re-encrypting every row that uses envelope encryption (DEKs) — rotation tooling lands with the Phase 3 secrets-rotation ticket. Until then, rotate only during planned downtime with a manual migration.
- **JWT keypair**: rotate on a schedule (quarterly is a reasonable default). Hot-swap by running two verification keys simultaneously — the `mp.jwt.verify.publickey.location` setting accepts a JWKS URL when you're ready for multi-key rotation.
- **Database / MinIO / SMTP creds**: whatever your secret manager prescribes.

Never commit any of these. `.env.prod` is `.gitignore`d; the committed `.env.prod.example` has only placeholder values.

## Boot-time log line

Quarkus prints the active profile during startup. A clean prod boot looks roughly like:

```
INFO  [io.quarkus] (main) translately 0.1.0 on JVM (profile: prod) started in 2.345s.
INFO  [io.quarkus] (main) Listening on: http://0.0.0.0:8080
INFO  [io.quarkus] (main) Profile prod activated.
INFO  [io.quarkus] (main) Installed features: [cdi, flyway, hibernate-orm, …]
```

If you see `profile: dev` on a production host, something is wrong — check that you booted the fast-jar (`java -jar quarkus-app/quarkus-run.jar`) or passed `-Dquarkus.profile=prod`.

## Native-image build

A GraalVM native-image recipe is committed at [`infra/docker/backend.native.Dockerfile`](https://github.com/Pratiyush/translately/blob/master/infra/docker/backend.native.Dockerfile). It produces a single static binary — smaller image, faster cold start, but a ~5–15 minute build that's memory-hungry.

Build locally:

```bash
# From the repo root
docker build -f infra/docker/backend.native.Dockerfile \
  -t translately-backend:native .
```

Or via Gradle directly (requires GraalVM 21 with `native-image` on the host):

```bash
./gradlew :backend:app:build \
  -Dquarkus.package.type=native \
  -Dquarkus.native.container-build=false \
  -x test --no-daemon --stacktrace
```

The `translately.quarkus-app` Gradle convention at [`buildSrc/src/main/kotlin/translately.quarkus-app.gradle.kts`](https://github.com/Pratiyush/translately/blob/master/buildSrc/src/main/kotlin/translately.quarkus-app.gradle.kts) wires the `io.quarkus` plugin that enables native builds. Library modules use the lighter `translately.quarkus-module` convention and are not runnable on their own.

The published `ghcr.io/pratiyush/translately-backend:native-<version>` image (built via `release.yml` in Phase 1+) is the drop-in replacement for the JVM image; swap the `image:` line in `compose-prod.yml` to adopt it.

> **TODO (doc gap):** the release pipeline in [`release.yml`](https://github.com/Pratiyush/translately/blob/master/.github/workflows/release.yml) currently publishes only the JVM fast-jar image (`infra/docker/backend.Dockerfile`). The `backend.native.Dockerfile` recipe is ready, but automated native-image publication lands with a follow-up ticket. Until then, operators who want the native image build it themselves.

## Troubleshooting quick hits

- **"Failed to resolve expression `${TRANSLATELY_CRYPTO_MASTER_KEY}`"** — you're booting `%prod` without the env var set. Export it (or populate `.env.prod`) and restart.
- **`psql: FATAL: role "translately" does not exist`** — dev profile is pointed at your prod database. Check `quarkus.profile` and `QUARKUS_DATASOURCE_JDBC_URL`.
- **Tests fail with "Connection refused" on 5432** — a test opted into `@QuarkusTestResource` but Docker isn't running, so Testcontainers can't start Postgres. Start Docker Desktop / `colima start`.
- **Logs are too noisy in dev / too quiet in prod** — `quarkus.log.category."io.translately".level` is `DEBUG` in `%dev` and `INFO` in the default (which `%prod` keeps). Override with `QUARKUS_LOG_CATEGORY__IO_TRANSLATELY__LEVEL=TRACE` for a deep dive without editing `application.yml`.
