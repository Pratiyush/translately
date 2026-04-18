---
title: Application shell
parent: Product
nav_order: 1
---

# Application shell

The application shell is the persistent chrome every authenticated route renders inside — the top bar, primary navigation, org switcher, and user menu. Route changes swap only the inner `<main>` region, so focus, scroll position, and transient state stay put.

Introduced by: [T115](https://github.com/Pratiyush/translately/issues/138) · Ships in `v0.1.0` · Source: [`webapp/src/components/shell/`](https://github.com/Pratiyush/translately/blob/master/webapp/src/components/shell/).

Related: [theming](theming.md), [authentication](auth.md), [webapp architecture](../architecture/webapp.md).

## Anatomy

```
┌──────────────────────────────────────────────────────────────────────┐
│  [✦ Translately]  [▾ Acme Corp]    Dashboard  Orgs  Projects   [☼] [◌]│  ← <header>  TopBar
└──────────────────────────────────────────────────────────────────────┘
│                                                                      │
│                         <Outlet />  (route content)                  │  ← <main id="main-content">
│                                                                      │
```

- **Left group** — brand link (returns to `/`), vertical divider, **OrgSwitcher**.
- **Center** — **NavLinks** (Dashboard, Orgs, Projects). Hidden below `md` breakpoint; the nav collapses in favour of the brand on small screens.
- **Right group** — **ThemeToggle**, **UserMenu**.

The `<header>` is the single [`banner`](https://www.w3.org/TR/wai-aria-1.2/#banner) landmark; the `<main>` is the single [`main`](https://www.w3.org/TR/wai-aria-1.2/#main) landmark with `tabIndex={-1}` so a "skip to content" link can focus it.

### Screenshot

<picture>
  <source srcset="../screenshots/dashboard-dark.png" media="(prefers-color-scheme: dark)">
  <img src="../screenshots/dashboard-light.png" alt="Authenticated app shell on the dashboard route: brand, org switcher, primary nav, theme toggle, and user avatar in the top bar." />
</picture>

## OrgSwitcher

Replaces the brand-only header on every page. Three states:

1. **No orgs** — the component collapses to a `+ Create organization` CTA that routes to `/orgs`. Shown for brand-new signups until they either create or accept an invite.
2. **One or more orgs, one active** — trigger shows the active org's name and a chevron. Click / Enter opens a dropdown menu listing **every** org alphabetically; the active one has a check icon, each row shows a right-aligned role badge (OWNER / ADMIN / MEMBER).
3. **Org selected** — clicking a row sets it active in-memory (`AuthStore.setActiveOrg`). Phase 1 deliberately does not reflect the active org into the URL; org-scoped routes land in Phase 3 (T306).

Keyboard + a11y:

- The trigger is a real `<button>` — Tab reaches it, Enter / Space open, Esc closes.
- Radix DropdownMenu handles ↑ / ↓ between items; Esc returns focus to the trigger.
- The trigger carries `aria-label="Open organization picker"` so screen readers announce its purpose regardless of the currently-visible org name.

## UserMenu

Avatar-triggered dropdown. The avatar is a Radix-wrapped component that falls back to two-letter initials when `user.avatarUrl` is absent.

Menu items:

- **Name + email header** — pure label, not selectable.
- **Profile** → `/profile`
- **Settings** → `/settings`
- **Sign out** — clears `AuthStore`, redirects to `/signin`.

Keyboard + a11y:

- Trigger carries `aria-label="Open user menu"`.
- Arrow keys move between items; Enter activates; Esc closes and returns focus to the avatar.
- Focus-visible rings use the design-system `--ring` token so they work in both themes.

## NavLinks

Three destinations: **Dashboard** (`/`), **Organizations** (`/orgs`), **Projects** (`/projects`). Each renders as a `<NavLink>` from React Router, which annotates the active entry with `aria-current="page"` for assistive tech. The active link also flips to the `--accent` background token.

Extending: add a new `NavItem` entry in [`NavLinks.tsx`](https://github.com/Pratiyush/translately/blob/master/webapp/src/components/shell/NavLinks.tsx). i18n keys go under `nav.*` in [`webapp/src/i18n/en.json`](https://github.com/Pratiyush/translately/blob/master/webapp/src/i18n/en.json).

## Route integration

```
router.tsx
├── /signin              (public, no shell)
└── RequireAuth
    └── AppShell
        ├── /             → DashboardRoute
        ├── /orgs         → OrgsRoute
        ├── /projects     → ProjectsRoute
        └── *             → NotFoundRoute
```

`RequireAuth` redirects unauthenticated requests to `/signin` while preserving the attempted location in `location.state.from` — the real sign-in page (T117) restores it after successful login.

## Keyboard shortcuts

| Key | Action |
|---|---|
| `Tab` / `Shift+Tab` | move focus through header controls (brand → OrgSwitcher → nav → theme → user menu) |
| `Enter` / `Space` on trigger | open the corresponding menu |
| `↑` / `↓` | navigate menu items |
| `Esc` | close the open menu, return focus to its trigger |
| `⌘K` / `Ctrl+K` | *reserved for the command palette (lands later in Phase 1)* |

## Accessibility checklist

- [x] Single `banner` and `main` landmarks.
- [x] Skip-to-content possible via `main-content` id (explicit link lands with the sign-in page in T117).
- [x] All icon-only buttons (brand, theme toggle, user menu) carry explicit `aria-label`s.
- [x] Focus-visible styling uses design-system tokens, preserved in light and dark.
- [x] Colour contrast ≥ 4.5 : 1 for text, ≥ 3 : 1 for large text and UI icons, verified with axe.
- [x] Keyboard-only walk-through is documented above and exercised by `App.test.tsx`'s axe run in both themes.

## Tested paths

From [`webapp/src/components/shell/*.test.tsx`](https://github.com/Pratiyush/translately/blob/master/webapp/src/components/shell/):

- `AppShell.test.tsx` — renders header + main, nests the outlet.
- `TopBar.test.tsx` — brand link present, nav labelled "Primary", theme toggle + user menu present.
- `OrgSwitcher.test.tsx` — empty state, one-org state, multi-org alphabetical sort, active mark, role badge, trigger a11y label, clicking a row calls `setActiveOrg`.
- `UserMenu.test.tsx` — initials fallback, profile / settings / sign-out navigation, sign-out clears store.
- `NavLinks.test.tsx` — active entry gets `aria-current="page"`.
- `App.test.tsx` — full app under `RequireAuth` with authenticated + unauthenticated branches; axe-clean in light and dark.

## Changelog

First introduced in [Unreleased](https://github.com/Pratiyush/translately/blob/master/CHANGELOG.md) (Phase 1). Ships with `v0.1.0`.
