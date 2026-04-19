---
title: Home
layout: default
nav_order: 1
description: >-
  Translately — open-source, MIT-licensed, self-hosted localization and
  translation management. Keys, translations, ICU validation, i18next
  JSON import/export shipping in v0.3.0.
permalink: /
---

# Translately
{: .fs-9 }

**The open-source, self-hosted translation management platform for teams that ship in more than one language.** MIT. Every feature free. Keys, translations, ICU validation, JSON import/export shipping today. Bring-your-own-key AI arrives in Phase 4 — the platform runs end-to-end without it.
{: .fs-5 .fw-300 }

[Quickstart]({{ '/quickstart/' | relative_url }}){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 .mr-2 }
[API reference]({{ '/api/' | relative_url }}){: .btn .fs-5 .mb-4 .mb-md-0 .mr-2 }
[GitHub](https://github.com/Pratiyush/translately){: .btn .fs-5 .mb-4 .mb-md-0 .mr-2 }
[LLM corpus]({{ '/llms-full.txt' | relative_url }}){: .btn .fs-5 .mb-4 .mb-md-0 }

---

## What ships today — v0.3.0 (MVP)

Translately v0.3.0 is the **end of the MVP**: Phases 0 through 3 are complete, the platform works end-to-end, every capability listed here is in `master` and running on a signed release.

| Capability | What it means for you |
|---|---|
| **Email + password auth** with verified accounts, refresh-token rotation, forgot-password + reset | A translator can sign up, verify email, and sign in — nothing else needed to start. |
| **Organizations, projects, members** with OWNER / ADMIN / MEMBER roles, last-owner protection, private-org semantics | Multi-tenant from day one. Each org scoped cleanly; non-members can't enumerate private orgs. |
| **Keys, namespaces, translations** with the full 5-state lifecycle (`EMPTY → DRAFT → TRANSLATED → REVIEW → APPROVED`) | Translators get a sticky-col table with per-cell autosave; admins get namespaces to group keys by feature. |
| **ICU MessageFormat validation** with CLDR plurals, line + col error reporting | Bad ICU is rejected at save time, not discovered in production. |
| **Postgres full-text + trigram key search** | Find a key in a 10k-key project without dragging Elasticsearch into your self-host stack. |
| **i18next JSON import + export** — flat and nested shapes, `KEEP` / `OVERWRITE` / `MERGE` conflict modes, per-row ICU validation | Paste your existing translations in, export them back out. One language per call; multi-language dumps as a scriptable GET. |
| **API keys + Personal Access Tokens** with scope intersection computed on every request | CI pipelines + scripts authenticate with machine credentials; revocation is instant. |
| **Light + dark + keyboard-first UI** — WCAG 2.1 AA, Radix Dialog + focus trap, `prefers-reduced-motion` respected | Accessible out of the box. No gated "enterprise a11y" SKU. |

All of it MIT. All of it free. No paywalled tier exists.

## Coming next — v0.4.0 (Phase 4)

- **Bring-your-own-key AI** — per-project OpenAI / Anthropic / Google / Azure / custom endpoints, envelope-encrypted at rest. **The platform runs end-to-end without this configured.**
- **Machine translation** via the same BYOK layer for non-generative providers.
- **Translation Memory** over `pgvector` + trigram.
- **Async Quartz + SSE** for the bulk-import paths that v0.3.0 ships sync.

[Full roadmap](#roadmap) below.

---

## Documentation by surface

| Surface | Start here |
|---|---|
| [Quickstart]({{ '/quickstart/' | relative_url }}) | 10-minute path from `docker compose up` to your first exported JSON. |
| [Product]({{ '/product/' | relative_url }}) | Walkthroughs of every user-visible flow — auth, orgs, keys table, editor, import wizard, export modal. |
| [API reference]({{ '/api/' | relative_url }}) | OpenAPI spec + scope matrix + error catalogue + rate-limit policy + the imports/exports + keys endpoints. |
| [Architecture]({{ '/architecture/' | relative_url }}) | Module map, data model (V1–V4), request lifecycle, multi-tenancy, crypto, ICU, search, ADRs. |
| [Self-hosting]({{ '/self-hosting/' | relative_url }}) | Runtime profiles, dev compose, hardening checklist. Everything an operator needs. |

Every PR ships its docs — see [CLAUDE.md rule #10](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md#hard-rules-non-negotiable). Stale docs are worse than missing docs.

## Why Translately

- **MIT, no gated tier.** SSO, SAML, LDAP, Tasks, Branching, Glossaries, Webhooks, CDN, custom storage, granular permissions, audit logs — all on the free shipping schedule. No "enterprise" upsell.
- **BYOK is the only AI shape.** Per-project encryption key, envelope-sealed at rest, zero Translately-owned API keys in the loop. If AI is off, every feature except AI suggestions still works.
- **Quarkus + Kotlin backend.** Fast boot, low memory, native-image friendly for the zero-cost deploy shape.
- **Translator-first UI.** Sticky column, autosave, ⌘+↵ commit, Escape revert, 5-state badges, inline ICU errors.

## Roadmap

Seven-phase plan. One signed minor-version tag per phase. **Phases 0–3 (MVP) are complete.**

| Phase | Tag | Theme | Status |
|---|---|---|---|
| 0 | `v0.0.1` | Bootstrap — CI, repo, scaffolding | ✅ shipped 2026-04-17 |
| 1 | `v0.1.0` | Auth + Org / Project + webapp shell | ✅ shipped 2026-04-18 |
| 2 | `v0.2.0` | Keys + Translations + ICU | ✅ shipped 2026-04-19 |
| 3 | `v0.3.0` | JSON import / export · **MVP** | ✅ shipped 2026-04-19 |
| 4 | `v0.4.0` | BYOK AI + MT + Translation Memory | next |
| 5 | `v0.5.0` | Screenshots + JS SDK + in-context editor | planned |
| 6 | `v0.6.0` | Webhooks + CDN + CLI + glossaries | planned |
| 7 | `v1.0.0` | Tasks + Branching + SSO / SAML / LDAP + audit | planned |

See the [CHANGELOG](https://github.com/Pratiyush/translately/blob/master/CHANGELOG.md) for per-PR detail and [RELEASE-NOTES](https://github.com/Pratiyush/translately/blob/master/RELEASE-NOTES.md) for the long-form narratives.

## Download

- [Full docs bundle (ZIP)]({{ '/downloads/translately-docs.zip' | relative_url }}) — every page under `docs/`, deterministic snapshot of the latest `master`.
- [LLM corpus (single file)]({{ '/llms-full.txt' | relative_url }}) — every `.md` concatenated with file-boundary markers, per [llmstxt.org](https://llmstxt.org). For Claude, Cursor, in-house assistants.
- [Link index]({{ '/llms.txt' | relative_url }}) — the short llms.txt discovery file.
- Container images: `ghcr.io/pratiyush/translately-backend:v0.3.0` + `ghcr.io/pratiyush/translately-webapp:v0.3.0` (published by `release.yml` on every signed tag).

## License

- **Outbound:** [MIT](https://github.com/Pratiyush/translately/blob/master/LICENSE). Use it, fork it, ship it, sell it — no strings.
- **Inbound:** [Contributor License Agreement](https://github.com/Pratiyush/translately/blob/master/CLA.md) (Apache-ICLA-adapted, copyright-license form — contributor retains ownership). Every PR carries a ticked CLA checkbox.
