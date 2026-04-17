# Release notes

Long-form release narratives. For raw diffs and per-PR detail, see [CHANGELOG.md](CHANGELOG.md).

---

## v0.0.1 — Phase 0: Bootstrap

**Released:** 2026-04-17
**Status:** prerelease
**Tag:** `v0.0.1` (GPG-signed) · [Compare with master](https://github.com/Pratiyush/translately/compare/v0.0.1...master)

### What's in the box

This is the seed prerelease for Translately — an open-source, MIT-licensed, self-hosted localization and translation management platform with bring-your-own-key AI. No product features yet; v0.0.1 exists to nail down the structure, conventions, CI, and runtime pipeline before any business logic ships.

**What actually runs**

- **Backend.** Quarkus 3.17 on Kotlin / Java 21. `./gradlew :backend:app:quarkusDev` starts the app; probes respond at `/q/health/{live,ready,started}`, aggregate health at `/q/health`, OpenAPI at `/q/openapi`, Swagger UI at `/q/swagger-ui`. A `GET /` service-metadata endpoint returns name, version, and well-known paths.
- **Webapp.** `pnpm --filter @translately/webapp dev` boots a Vite + React + TypeScript + Tailwind app with a shadcn-style Button primitive, a working light/dark/system theme toggle (localStorage + OS reactivity), Lucide icons, and a dogfood `i18n.t()` wrapper backed by `en.json`.
- **Infra.** `docker compose up -d` starts Postgres 16, Redis 7, MinIO (with auto-created buckets), and Mailpit. Keycloak is available behind `--profile keycloak`.

**Quality gates**

- 6 backend `@QuarkusTest` assertions cover health + index + OpenAPI reachability.
- 55 webapp tests across 6 suites cover the `cn()` helper, i18n lookup, the Button primitive, the ThemeProvider + ThemeToggle, and App shell structure + interaction. **`axe-core` reports zero violations in light AND dark.**
- CI runs on every push + PR: `ci-backend` (Gradle build + ktlint + detekt + test + JaCoCo), `ci-webapp` (pnpm lint + test + build), `lychee` link-checker, `codeql` (java-kotlin + javascript-typescript + actions).

**Governance**

- Master branch protected: PR required, CODEOWNERS review, signed commits, linear history, no force push / deletions, conversation resolution.
- 97 GitHub issues seeded across 8 phase milestones with `type:*` / `scope:*` / `est:*` labels. MVP partition applied: 57 `mvp` (Phases 0–3), 37 `post-mvp` (Phases 4–7), 3 `deferred` (Figma plugin, MCP server, migration importer).
- Dependabot configured for Gradle, npm workspaces, GitHub Actions, Docker.

### Migration notes

None — first prerelease. Nothing to migrate from.

### Known limitations

- No product features. No auth, no organizations, no keys, no translations. Those land in v0.1.0 and v0.2.0.
- Backend depends on the Phase 7 LDAP extension at build time, so the default profile ships with inert LDAP placeholder config. Real wiring comes in Phase 7.
- Webapp does not yet dogfood against a live Translately project — `t()` resolves locally from `en.json` until the JS SDK lands in Phase 5.
- Release images (`ghcr.io/pratiyush/translately-backend`, `-webapp`) are NOT published for `v0.0.1`. The `release.yml` Docker job is gated to skip on this version because the fast-jar doesn't yet have product binary shape. Images start publishing at `v0.1.0`.

### What's next — Phase 1 → v0.1.0

- Email + password signup with email verification (via Mailpit in dev).
- JWT access + refresh token rotation (Smallrye JWT).
- Google OAuth; optional Keycloak OIDC profile.
- Entities: `User`, `Organization`, `OrganizationMember`, `Project`, `ProjectLanguage`, `ApiKey`, `Pat`.
- Permission scope enum + `@RequiresScope` annotation + org / project RBAC.
- Multi-tenant `TenantRequestFilter` + Hibernate filter per-request.
- `CryptoService` envelope encryption skeleton (for Phase 4 AI keys).
- OpenAPI published to `docs/api/openapi.json`; webapp API client auto-generated.
- Webapp app shell: nav, org switcher, user menu, ⌘K command palette, signup / login / org / project pages.
- `.github/workflows/ci-e2e.yml` spinning up the full docker-compose stack and running Playwright smoke tests.

Target: **2026-05-07**.
