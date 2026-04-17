# Translately

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) ![Status](https://img.shields.io/badge/status-pre--alpha-red.svg) ![Phase](https://img.shields.io/badge/phase-0%20bootstrap-blue.svg)

> Open-source localization & translation management platform — **bring your own AI key, all premium features free**.

Translately is a self-hosted localization and translation management platform, built on **Quarkus + Kotlin** with a polished **React + shadcn/ui** webapp. Every feature is MIT — no paywall, no license key, no gated enterprise tier. Tasks, Branching, SSO, SAML, LDAP, Webhooks, Glossaries, custom S3 storage, granular permissions, and audit logs all ship free.

## Differentiators

- **Bring-your-own AI key.** AI features are entirely opt-in. The platform never ships with model credentials. Configure Anthropic Claude, OpenAI, OpenAI-compatible (Codex, Ollama, local LLMs), DeepL, Google Translate, or AWS Translate — per project.
- **No paywalled premium tier.** Every feature is MIT, including SSO, SAML, LDAP, Tasks, Branching, Webhooks, Glossaries, custom storage, granular permissions, and audit logs.
- **Quarkus.** ~1s JVM startup, ~50ms native, lower memory than Spring. Friendlier to self-hosters on small VPS.
- **God-level UI.** Light + dark themes, ⌘K command palette, full keyboard nav, WCAG 2.1 AA.
- **Open standards.** ICU MessageFormat, CLDR plurals, OpenAPI 3, Conventional Commits, Keep-a-Changelog.

## Status

Pre-alpha. Bootstrap phase. See [`_progress.md`](_progress.md) for live phase status and [`tasks.md`](tasks.md) for the task ledger.

## Roadmap

| Phase | Tag | Theme |
|---|---|---|
| 0 | v0.0.1 | Bootstrap — repo, CI, docker-compose |
| 1 | v0.1.0 | Auth + Org/Project skeleton |
| 2 | v0.2.0 | Keys + Translations + ICU |
| 3 | v0.3.0 | JSON import/export |
| 4 | v0.4.0 | AI/MT (BYOK) + Translation Memory |
| 5 | v0.5.0 | Screenshots + In-context editor + JS SDK |
| 6 | v0.6.0 | Webhooks + CDN + CLI + Glossaries |
| 7 | v1.0.0 | Tasks + Branching + SSO/SAML/LDAP + Audit + Polish |

## Architecture

```
backend (Quarkus + Kotlin)   ⇄   PostgreSQL 16
                              ⇄   Redis 7
                              ⇄   MinIO / S3
                              ⇄   SMTP (Mailpit dev)
                              ⇄   Optional Keycloak (OIDC/SAML)
                              ⇄   Optional LDAP

webapp (React + Vite + shadcn/ui)   ⇄   backend HTTP API
sdks/js (browser + node)            ⇄   backend HTTP API + in-context editor
cli (node)                          ⇄   backend HTTP API
```

## Getting started (Phase 0)

```bash
# clone
git clone https://github.com/Pratiyush/translately.git
cd translately

# spin up infra (postgres, redis, minio, mailpit)
docker compose up -d

# backend
./gradlew :backend:app:quarkusDev

# webapp (separate terminal)
cd webapp && pnpm install && pnpm dev
```

## Documentation

- [Architecture overview](.kiro/steering/architecture.md)
- [API conventions](.kiro/steering/api-conventions.md)
- [UI conventions](.kiro/steering/ui-conventions.md)
- [Contributing](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security policy](SECURITY.md)
- [Release notes](RELEASE-NOTES.md)
- [Changelog](CHANGELOG.md)

## License

MIT — see [LICENSE](LICENSE).

Made with care by Pratiyush.
