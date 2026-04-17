# infra/

Deployment artefacts for Translately. Not shipped as part of the app binaries; used by operators and CI.

## Contents

```
infra/
├── README.md                        # this file
├── docker/
│   ├── backend.Dockerfile           # JVM container image (multi-arch)
│   ├── backend.native.Dockerfile    # GraalVM native-image container (release)
│   ├── webapp.Dockerfile            # nginx static image
│   └── nginx.conf                   # SPA routing + /api/* reverse proxy
├── postgres/
│   └── init/
│       └── 01-keycloak-db.sql       # creates `keycloak` DB on fresh dev volumes
├── keycloak/
│   └── realms/                      # dev realm imports (gitkeep placeholder)
├── compose-prod.yml                 # single-host production compose
├── .env.prod.example                # prod env template — copy to `.env.prod`
└── helm/                            # Phase 7: official Helm chart (placeholder)
```

## Dev (root `docker-compose.yml`)

The dev stack lives at the repo root, not here. Start it with `docker compose up -d` from the repo root. It brings up Postgres, Redis, MinIO (with auto-created buckets), and Mailpit. Keycloak is behind a `--profile keycloak` opt-in.

See the compose file comments for URLs and credentials.

## Production (`infra/compose-prod.yml`)

Single-host setup for small/medium self-hosters.

```bash
cp infra/.env.prod.example .env.prod
# ...edit secrets (JWT_SECRET, CRYPTO_MASTER_KEY, passwords)...
docker compose -f infra/compose-prod.yml --env-file .env.prod up -d
```

You are responsible for:

- Running behind HTTPS (Caddy / Traefik / nginx in front).
- Backups of the `postgres-data` and `minio-data` volumes.
- Monitoring (Prometheus scrape `/q/metrics`).
- Rotating `JWT_SECRET` and `CRYPTO_MASTER_KEY` according to your policy.
- Sending mail via a real SMTP provider (Mailpit is dev-only).

Need multi-node, autoscaling, HA Postgres, or external secrets? Wait for the **Helm chart** in Phase 7 or roll your own orchestration — `compose-prod.yml` is deliberately simple.

## Container images

| Image | Dockerfile | Notes |
|---|---|---|
| `ghcr.io/pratiyush/translately-backend:<version>` | `docker/backend.Dockerfile` | JVM fast-jar layout. Published by `release.yml` on tag push. |
| `ghcr.io/pratiyush/translately-backend:native-<version>` | `docker/backend.native.Dockerfile` | GraalVM native-image. Smaller, faster startup, slower build. |
| `ghcr.io/pratiyush/translately-webapp:<version>` | `docker/webapp.Dockerfile` | nginx static + SPA reverse proxy. |

All images are built multi-arch (`linux/amd64`, `linux/arm64`), signed with Cosign keyless, and shipped with SBOMs (Syft).

## Helm chart (Phase 7)

`infra/helm/` will hold the canonical chart for Kubernetes self-hosters. Empty placeholder until Phase 7 — use `compose-prod.yml` until then.
