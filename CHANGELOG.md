# Changelog

All notable changes to Translately are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0 (`0.x`) tags are marked as prereleases on GitHub.

## [Unreleased]

### Added
- _Phase 0 bootstrap in progress._

## [0.0.1] — 2026-04-16 — Phase 0: Bootstrap

### Added
- Repository scaffolding: top-level files (README, LICENSE, CHANGELOG, RELEASE-NOTES, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CODEOWNERS).
- `.github/` workflows skeleton (ci-backend, ci-webapp, ci-e2e, link-checker, codeql, release, pages, dependabot).
- `.kiro/steering/` always-loaded steering files (architecture, contributing-rules, api-conventions, ui-conventions).
- `CLAUDE.md`, `AGENTS.md`, `.claude/commands/` for AI-pair-programming context.
- Gradle Kotlin DSL multi-module skeleton (`backend/`).
- Webapp skeleton (Vite + React + TypeScript + Tailwind + shadcn/ui placeholder).
- `infra/` with `docker-compose.yml` (Postgres 16, Redis 7, MinIO, Mailpit) and Dockerfile placeholders.
- Read-only third-party reference sources under `_reference/` (gitignored).
- `tasks.md` and `_progress.md` Kiro-style trackers.
- MIT LICENSE.

[Unreleased]: https://github.com/Pratiyush/translately/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/Pratiyush/translately/releases/tag/v0.0.1
