# Hardening checklist

> **Status:** placeholder. This page ships fully in Phase 1 (`v0.1.0`) once auth and real deployments land. If you need hardening guidance before then, the list below is an outline — treat it as advisory, not exhaustive.

## Outline (Phase 1 will flesh these out)

- **Reverse proxy + TLS** — terminate HTTPS at Caddy / Traefik / nginx; HSTS on; redirect :80 → :443.
- **Strong secrets** — 32-byte random `JWT_SECRET` and `CRYPTO_MASTER_KEY`; rotated per your policy.
- **Database** — PostgreSQL 16, `scram-sha-256` auth, TLS in transit, backups tested, restore drills quarterly.
- **Object storage** — S3/MinIO with per-project prefixes and signed URL expiry; no public buckets except CDN output.
- **SMTP** — auth required, TLS in transit, dedicated sender domain with SPF/DKIM/DMARC.
- **Rate limits** — Redis-backed sliding window per token; tune the defaults under `quarkus.http.limits` and the service-level caps documented in `api-conventions.md`.
- **Audit log retention** — keep at least 90 days; export to cold storage.
- **Container image scanning** — trust the Cosign keyless signatures attached to `ghcr.io/pratiyush/translately-*` images; pin tags in prod, never use `:latest`.
- **CSP / security headers** — nginx config in `infra/docker/nginx.conf` ships safe defaults; additions go here.
- **Backup / restore drill** — weekly automated backup + quarterly manual restore test.

## Reporting security issues

See [SECURITY.md](../../SECURITY.md). Do not file public issues for vulnerabilities — use GitHub Security Advisories or pratiyush1@gmail.com.
