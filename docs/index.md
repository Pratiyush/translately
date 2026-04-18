---
title: Home
layout: default
nav_order: 1
description: >-
  Translately — open-source, MIT-licensed, self-hosted localization and
  translation management. Bring-your-own-key AI, no paywalled premium tier.
permalink: /
---

# Translately
{: .fs-9 }

Open-source localization & translation management. Self-hosted, MIT, zero paywalled tier. Bring-your-own-key AI.
{: .fs-5 .fw-300 }

[Download docs (ZIP)](downloads/translately-docs.zip){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 .mr-2 }
[LLM corpus](llms-full.txt){: .btn .fs-5 .mb-4 .mb-md-0 .mr-2 }
[GitHub](https://github.com/Pratiyush/translately){: .btn .fs-5 .mb-4 .mb-md-0 }

---

## Documentation by surface

| Surface | What's in it |
|---|---|
| [Product]({{ '/product/' | relative_url }}) | Walkthroughs of every user-visible flow — app shell, theming, authentication. |
| [API reference]({{ '/api/' | relative_url }}) | OpenAPI spec, scopes, error codes, rate limits, versioning, auth endpoints. |
| [Architecture]({{ '/architecture/' | relative_url }}) | Module map, data model, request lifecycle, multi-tenancy, crypto, ADRs. |
| [Self-hosting]({{ '/self-hosting/' | relative_url }}) | Hardening checklist, operator guides. |

Every PR ships its docs — see [CLAUDE.md rule #10](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md#hard-rules-non-negotiable) and the [contributing rules](https://github.com/Pratiyush/translately/blob/master/.kiro/steering/contributing-rules.md).

## Positioning

- **MIT, no gated tier.** Every capability — SSO, SAML, LDAP, Tasks, Branching, Glossaries, Webhooks, CDN, custom storage, granular permissions, audit logs — ships free.
- **Bring your own AI.** Per-project provider + key, envelope-encrypted at rest. The platform runs end-to-end with **zero AI configured**.
- **Quarkus + Kotlin backend.** Fast boot, low memory, native-image friendly for the zero-cost deploy shape.
- **ICU from day one.** Native ICU MessageFormat parsing, CLDR plurals, CodeMirror 6 editor with inline validation.
- **Keyboard-first UI.** Light + dark, ⌘K command palette, full keyboard nav, WCAG 2.1 AA.

## Roadmap

Twelve-week, seven-phase plan. One signed minor-version tag per phase.

| Phase | Tag | Theme |
|---|---|---|
| 0 | `v0.0.1` | Bootstrap — CI, repo, scaffolding _(shipped)_ |
| 1 | `v0.1.0` | Auth + Org / Project + webapp shell _(in progress)_ |
| 2 | `v0.2.0` | Keys + Translations + ICU |
| 3 | `v0.3.0` | JSON import / export → **first MVP** |
| 4 | `v0.4.0` | AI / MT (BYOK) + Translation Memory |
| 5 | `v0.5.0` | Screenshots + JS SDK + in-context editor |
| 6 | `v0.6.0` | Webhooks + CDN + CLI + glossaries |
| 7 | `v1.0.0` | Tasks + Branching + SSO / SAML / LDAP + audit |

See the [CHANGELOG](https://github.com/Pratiyush/translately/blob/master/CHANGELOG.md) for what's landed.

## Quickstart (local dev)

```bash
git clone https://github.com/Pratiyush/translately
cd translately
docker compose up -d              # Postgres 16 + Redis 7 + MinIO + Mailpit
./gradlew :backend:app:quarkusDev # backend at :8080
pnpm --filter webapp dev          # webapp at :5173
```

Self-hosters: start with the [hardening checklist]({{ '/self-hosting/hardening/' | relative_url }}) before exposing to the internet.

## Download

- [Full docs bundle (ZIP)](downloads/translately-docs.zip) — every page under `docs/`, deterministic snapshot of the latest `master`.
- [LLM corpus (single file)](llms-full.txt) — every `.md` concatenated with file-boundary markers, per [llmstxt.org](https://llmstxt.org). For Claude, Cursor, in-house assistants.
- [Link index](llms.txt) — the short llms.txt discovery file.
