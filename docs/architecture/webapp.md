---
title: Webapp architecture
parent: Architecture
nav_order: 8
---

# Webapp architecture

Translately's webapp is a single-page React application built with Vite, Tailwind, shadcn/ui primitives, and TanStack Query. It renders against the REST API described in [`docs/api/`](../api/) and persists nothing server-side that the API itself doesn't already persist.

Introduced by: [T010](https://github.com/Pratiyush/translately/issues/10) (bootstrap), [T114](https://github.com/Pratiyush/translately/issues/24) (theme), [T115](https://github.com/Pratiyush/translately/issues/25) (app shell).

Related: [product app-shell](../product/app-shell.md), [product theming](../product/theming.md), [auth architecture](auth.md).

## Stack

| Layer | Choice | Why |
|---|---|---|
| Build | **Vite** | Fast dev server, ES modules, ubiquitous in modern React projects |
| UI | **React + TypeScript** | Standard; paired with `strict: true` tsconfig |
| Styling | **Tailwind + shadcn/ui primitives** | Token-driven theming without the maintenance tax of a CSS-in-JS runtime |
| Icons | **Lucide** (only) | One coherent set; bans ad-hoc icon imports from mixing libraries |
| Routing | **React Router** | Data loader story isn't needed yet; we use it for navigation + route gating |
| Data | **TanStack Query** | Caches API responses, deduplicates fetches, retries on the happy path |
| Forms | **React Hook Form + Zod** | Schema-first validation shared with backend where it makes sense |
| Editor | **CodeMirror 6** (Phase 2) | ICU MessageFormat syntax support |
| Motion | **Framer Motion** (respects `prefers-reduced-motion`) |
| Tests | **Vitest + Testing Library + axe** for unit/component; Playwright for E2E (Phase 3+) |

## Directory layout

```
webapp/src
├── App.tsx, main.tsx, router.tsx         entry points + route table
├── theme/
│   ├── ThemeProvider.tsx                 light/dark/system + persistence
│   └── ThemeProvider.test.tsx
├── components/
│   ├── shell/                            AppShell, TopBar, NavLinks,
│   │   ├── AppShell.tsx                  OrgSwitcher, UserMenu (+ tests)
│   │   ├── TopBar.tsx
│   │   ├── NavLinks.tsx
│   │   ├── OrgSwitcher.tsx
│   │   └── UserMenu.tsx
│   ├── routes/                           One file per top-level route
│   ├── ui/                               Owned shadcn/Radix primitives
│   │   (Avatar, Button, DropdownMenu, …)
│   └── ThemeToggle.tsx                   Shell-adjacent but not shell-owned
├── lib/
│   ├── auth/                             AuthStore + useAuth() hook
│   └── utils.ts                          cn() and friends
├── i18n/
│   ├── en.json                           Canonical English strings
│   └── index.ts                          t() helper
├── index.css                             Design-token declarations
└── tests/                                Test setup
```

## State model

The webapp intentionally keeps global state *small*. Three stores:

1. **`AuthStore`** — dependency-free external store implementing the `useSyncExternalStore` shape. Persists to `localStorage` and subscribes to cross-tab `storage` events so every open tab stays in sync. Holds `{ user, activeOrgId }` and nothing else.
2. **`ThemeProvider`** — React context; source of truth for `theme` (user-selected) and `resolved` (what's actually applied). See [product/theming](../product/theming.md) for the full flow.
3. **TanStack Query cache** — every API-backed thing. Components call `useQuery` / `useMutation`; the cache deduplicates, the retry policy is `retry: false` (fail fast, surface errors to the user).

Nothing else sits in global state. Route-local state stays in components; ephemeral UI state stays in React state; server state lives in the Query cache.

## Routing

[`router.tsx`](https://github.com/Pratiyush/translately/blob/master/webapp/src/router.tsx) declares:

- `/signin` — public, renders outside the shell.
- Everything else — inside `<RequireAuth><AppShell/></RequireAuth>`.

`RequireAuth` redirects to `/signin` preserving `location.state.from` so the real sign-in flow (T117) can return the user where they started.

Phase 3 introduces org-scoped routes (`/{orgSlug}/…`) — that migration is owned by T306. Until then the shell is org-agnostic and the active org is held only in `AuthStore`.

## Component philosophy

- **Own your primitives.** The `ui/` folder contains thin Radix wrappers (`Avatar`, `Button`, `DropdownMenu`). The webapp never imports `@radix-ui/*` outside this folder — keeping the API surface small and letting us swap behind the same public interface.
- **i18n by default.** No user-visible string is hard-coded in a component. Every label / aria-label / error message goes through `t('…')`; the canonical catalogue is `webapp/src/i18n/en.json`. Tests assert against the English rendering for readability.
- **Tokens, not colours.** All colour lives in `index.css` as HSL values behind `--*` custom properties. Components reach for Tailwind utility classes (`bg-background`, `text-foreground`) that resolve to `hsl(var(--…))` — theme switching is a `class="dark"` toggle on `<html>`.

## Build + test

```bash
pnpm --filter webapp dev         # Vite dev server
pnpm --filter webapp test        # Vitest
pnpm --filter webapp test:a11y   # axe assertions under light + dark
pnpm --filter webapp build       # production build
```

Unit + component tests live next to the files they test. Playwright E2E (Phase 3) lives under `webapp/e2e/`.

## API client

Phase 1 adds an **auto-generated** TypeScript client (T120) derived from the committed `docs/api/openapi.json`. Every network call uses those generated types; editing a controller regenerates the client as part of the same PR so types and runtime always match.

## Accessibility budget

- Every route passes axe-clean under both light and dark themes — the global `App.test.tsx` asserts both.
- Every icon-only control carries an explicit `aria-label`.
- Focus rings are visible against every surface colour.
- `prefers-reduced-motion` is honoured — we collapse transitions to 0 ms rather than removing transitions, so focus handling remains correct.
- `prefers-color-scheme` is honoured when the user's theme choice is `system`.
- Keyboard-only walk-throughs are part of the acceptance criteria on every user-facing ticket.

## Why this shape

- **Static hosting is viable.** The webapp is a dumb SPA — it needs only a CDN and the API. This matters for self-hosters who want to front everything behind nginx / Caddy / Traefik.
- **No server-side rendering.** We don't need SEO or cold-start latency: Translately is an authenticated tool. Avoiding SSR keeps the deploy story simple.
- **No Redux, no Zustand, no MobX.** The three-store model above covers every real requirement; adding a redux-style tool would be a dependency without a user.
- **Tailwind over CSS-in-JS.** Build-time class generation means zero-runtime styling; `class="dark"` toggling on `<html>` means the theme switch is a single-pass repaint.

See [`.kiro/steering/ui-conventions.md`](https://github.com/Pratiyush/translately/blob/master/.kiro/steering/ui-conventions.md) for the authoritative UI / accessibility steering rules.
