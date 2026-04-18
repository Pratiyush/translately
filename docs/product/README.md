---
title: Product
nav_order: 2
has_children: true
permalink: /product/
---

# Product docs

User-facing documentation of every shipped feature. One page per user-visible flow, with light + dark screenshots and keyboard / a11y notes.

Per [CLAUDE.md rule #10](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md), every PR that changes a user-visible flow lands its matching page here in the same PR.

## Index

*Pages are added per ticket — see the per-phase back-fill milestones for the current list.*

- [Application shell](app-shell.md) — nav, org switcher, user menu, routing _(added by T115)_
- [Theming](theming.md) — light / dark / system toggle, tokens, persistence _(added by T114)_
- [Authentication](auth.md) — signup, email verify, login, password reset _(added by T103)_
- [API keys & PATs](api-keys-and-pats.md) — project-scoped API keys and user-scoped Personal Access Tokens _(added by T110)_

## Conventions

- **One page per user-visible capability.** Not per ticket — a single feature spanning multiple tickets gets one page that is updated in-place.
- **Screenshots in both themes.** Every screenshot is taken in light and dark modes. File names: `foo-light.png`, `foo-dark.png`.
- **Keyboard section.** Every page lists the keyboard shortcuts that touch the feature, plus any ARIA landmarks used.
- **Code blocks are runnable.** No pseudo-code. If a request body is shown, it works against the current API.
- **Link to the CHANGELOG entry** that first introduced the feature.
