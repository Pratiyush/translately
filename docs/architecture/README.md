---
title: Architecture
nav_order: 4
has_children: true
permalink: /architecture/
---

# Architecture docs

High-level architecture of the Translately platform. The canonical steering doc is [`.kiro/steering/architecture.md`](https://github.com/Pratiyush/translately/blob/master/.kiro/steering/architecture.md); this tree expands on it with diagrams, module maps, and Architecture Decision Records (ADRs).

Per [CLAUDE.md rule #10](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md), every PR that introduces a non-trivial technical decision lands an ADR here in the same PR.

## Overview

- [Module map](modules.md) — Gradle module graph, dependencies, ownership.
- [Data model](data-model.md) — ER diagram, entity relationships, indexing.
- [Request lifecycle](request-lifecycle.md) — from HTTP → filter stack → controller → service → data layer.
- [Multi-tenancy](multi-tenancy.md) — `TenantContext`, `TenantRequestFilter`, row-level isolation.
- [Authentication](auth.md) — JWT vs. OIDC vs. LDAP, token formats, refresh rotation.
- [Authorization](authorization.md) — scopes, roles, `@RequiresScope`, per-resource permissions.
- [Crypto](crypto.md) — envelope encryption for BYOK secrets, key rotation, at-rest protections.
- [Webapp shell](webapp.md) — route tree, state stores, TanStack Query boundaries.

## ADRs

Architecture Decision Records live under [`decisions/`](decisions/). Every non-trivial technical choice (library swap, auth strategy, storage layout, algorithm, performance trade-off) gets an ADR. They're immutable once accepted — supersede rather than edit.

- [ADR index](decisions/README.md)
- [ADR template (MADR)](decisions/_template.md)

## Diagram conventions

- **Mermaid** in `.md` files — renders natively on GitHub + Pages.
- **Exported PNGs** under `diagrams/` for the llms-full.txt ingestion (text-only LLMs can't parse `mermaid` blocks).
- **Source alongside rendered** — keep the `.mmd` next to the `.png` so diagrams are reproducible.
