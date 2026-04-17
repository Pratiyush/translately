# Release notes

Long-form release narratives. For raw diffs and per-PR detail, see [CHANGELOG.md](CHANGELOG.md).

---

## v0.0.1 — Phase 0: Bootstrap

**Released:** 2026-04-16
**Status:** prerelease

### What's in the box

This is the seed commit for Translately — an open-source, MIT-licensed localization platform with bring-your-own-key AI. Nothing runs yet; this release exists to nail down the structure, conventions, and tooling before any business logic lands.

- Multi-module Gradle Kotlin DSL backend (Quarkus + Kotlin + Java 21).
- Webapp scaffold (React + Vite + TypeScript + Tailwind + shadcn/ui).
- Local infrastructure via `docker-compose up`: PostgreSQL 16, Redis 7, MinIO, Mailpit.
- CI workflows for backend, webapp, e2e, security scans, link checks, docs deploy, release pipeline.
- Kiro steering files documenting architecture, API conventions, UI conventions, and contribution rules.
- Read-only third-party reference sources under `_reference/` (gitignored) for design inspiration only.

### Migration notes

None — first release.

### Known limitations

- No working features yet. The next release (v0.1.0) brings auth + organizations + projects.
- Backend and webapp build and boot, but only return placeholder responses.

### What's next

Phase 1 → v0.1.0: Email+password signup, JWT, optional Keycloak OIDC profile, Google OAuth, organizations, projects, languages, API keys, project member RBAC, and a webapp shell with login/signup/org-picker/project-list.
