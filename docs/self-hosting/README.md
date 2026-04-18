---
title: Self-hosting
nav_order: 5
has_children: true
permalink: /self-hosting/
---

# Self-hosting

Operator documentation for running Translately on your own infrastructure. Start with [hardening](hardening.md) before exposing to the internet.

Per [CLAUDE.md rule #10](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md#hard-rules-non-negotiable), every PR that adds an env var, compose service, Helm value, migration, or backup concern updates this tree in the same PR.

## Pages

- [Hardening checklist](hardening.md) — production-readiness before first deploy.
- [Dev compose](dev-compose.md) — operator walkthrough of the root `docker-compose.yml` (Postgres 16, Redis 7, MinIO, Mailpit, optional Keycloak), plus the `compose-prod.yml` diff for production.
- [Runtime profiles](runtime-profiles.md) — the `%dev` / `%test` / `%prod` Quarkus profile split, required env vars per profile, and the GraalVM native-image build recipe.

*More pages land as Phase 1+ ships (env-var catalogue, backup / restore drill, observability, upgrade guide).*

## Quickstart

A runnable local stack is in the repo root `docker-compose.yml`:

```bash
git clone https://github.com/Pratiyush/translately
cd translately
docker compose up -d   # Postgres 16 + Redis 7 + MinIO + Mailpit (+ optional Keycloak)
./gradlew :backend:app:quarkusDev   # backend at :8080
pnpm --filter webapp dev             # webapp at :5173
```

For production see [`infra/compose-prod.yml`](https://github.com/Pratiyush/translately/blob/master/infra/compose-prod.yml) and the [hardening checklist](hardening.md). Helm chart lands with [T709](https://github.com/Pratiyush/translately/issues/93) in Phase 7.
