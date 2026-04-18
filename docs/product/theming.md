---
title: Theming — light / dark / system
parent: Product
nav_order: 2
---

# Theming — light / dark / system

Translately ships with three theme modes: **light**, **dark**, and **system**. Every screen and component is designed and tested in all three. The user's choice persists across reloads and cross-tab windows; while the mode is `system`, a live OS preference change flips the UI without a reload.

Introduced by: [T114](https://github.com/Pratiyush/translately/issues/137) · Ships in `v0.1.0` · Source: [`webapp/src/theme/ThemeProvider.tsx`](https://github.com/Pratiyush/translately/blob/master/webapp/src/theme/ThemeProvider.tsx), [`webapp/src/components/ThemeToggle.tsx`](https://github.com/Pratiyush/translately/blob/master/webapp/src/components/ThemeToggle.tsx).

Related: [application shell](app-shell.md), [webapp architecture](../architecture/webapp.md).

## Using it

The theme toggle is the sun / moon / monitor icon in the top bar, right-hand group. It cycles through the three modes on click:

```
Light  →  Dark  →  System  →  Light  …
 (☼)      (☾)       (◌)       (☼)
```

Hovering the button reveals an `aria-label` such as `Light theme — click for Dark theme`. Keyboard users reach it with Tab and activate it with Enter or Space.

Three ways your preference gets picked up:

1. **Explicit choice** — set light or dark. That choice stays until you change it or clear site data. Cross-tab: every other open Translately tab reflects your choice inside a hundred ms.
2. **System choice** — the shell follows your OS / browser `prefers-color-scheme`. Change your OS theme and the app flips live; no reload required.
3. **First visit** — defaults to system. No flash-of-light is shown before the preference is applied because the initial paint runs after the provider reads `localStorage`.

## Design tokens

All themed colours flow through design-system CSS custom properties declared on `:root` for light and overridden under `.dark` for dark. Components never reference hex or rgb values directly — always `hsl(var(--…))`.

Canonical tokens (from [`webapp/src/index.css`](https://github.com/Pratiyush/translately/blob/master/webapp/src/index.css)):

| Token | Role |
|---|---|
| `--background` / `--foreground` | Page background / primary text |
| `--card` / `--card-foreground` | Surface for cards and dropdown menus |
| `--popover` / `--popover-foreground` | Floating UI (tooltip, popover, menu) |
| `--primary` / `--primary-foreground` | Brand accent (buttons, links) |
| `--secondary` / `--secondary-foreground` | Secondary button / tag background |
| `--muted` / `--muted-foreground` | Subdued surface / secondary text |
| `--accent` / `--accent-foreground` | Active nav, selected row |
| `--destructive` / `--destructive-foreground` | Error, delete, revoke |
| `--border` / `--input` / `--ring` | Borders, form input border, focus ring |
| `--radius` | Component corner radius |

Adding a new colour role means adding **both** a light and a dark value (and a Tailwind `colors` entry that points at it). Never add a token you only define for one theme.

## Accessibility

- **Contrast** — every surface/foreground pair meets WCAG 2.1 AA: ≥ 4.5 : 1 for text, ≥ 3 : 1 for icons and UI chrome. Automated with `axe-core` in `App.test.tsx` under both themes.
- **Reduced motion** — any component that animates (dropdown open/close, theme transition) respects `prefers-reduced-motion`. Transitions collapse to 0 ms rather than being removed outright so focus handling stays correct.
- **Focus rings** — driven by `--ring`; always visible in both themes, always ≥ 3 : 1 against the focused element.
- **Forced-colors** — Tokens fall back to system colours under `forced-colors: active` (Windows High Contrast). Avoid burning colours into SVGs; use `currentColor` or token-referenced styles.

## Persistence

- `localStorage.getItem('translately.theme')` → `"light" | "dark" | "system"`.
- On mount, the provider reads the value. Invalid / absent → `"system"`.
- On `set`, it writes back immediately.
- `storage` events propagate the value to every other tab: a theme change in one tab updates siblings without a reload.

If `localStorage` throws (Safari private mode, some extensions), the provider silently falls back to `"system"` and no persistence is attempted — the UI still works.

## How light / dark actually switches

`ThemeProvider`:

1. Reads the saved preference on mount.
2. Resolves the effective mode (system → follows `matchMedia('(prefers-color-scheme: dark)')`).
3. Toggles a `dark` class on `<html>` — Tailwind's `dark:` variants do the rest.
4. Registers a `matchMedia` listener so OS-level changes are reflected while the user's preference is `system`.

The one unusual detail: the provider flips `<html class="dark">` rather than using `data-theme="dark"`. This plays nicely with Tailwind's default `darkMode: 'class'` configuration and with the `@media (prefers-color-scheme: …)` queries that the GitHub Pages site (`docs/index.html`) uses.

## Keyboard shortcuts

None dedicated yet. The toggle is reachable via Tab/Shift+Tab. A `⌘K`-discoverable "Theme: Light / Dark / System" command will come with the command palette later in Phase 1.

## Screenshots

*Light + dark captures of the top bar with the toggle in each of the three states land here when the first native screenshot pass runs; filenames will follow the convention `theming-{light|dark}-{state}.png`.*

## Extending

- **Add a colour role** — add both `--foo` and `--foo-foreground` on `:root` and `.dark`; wire into `tailwind.config.ts` under `theme.extend.colors`.
- **Add a new theme** (e.g. brand-branded dark blue) — add a new class name alongside `dark`, extend `ThemeProvider`'s `Theme` union, and add a token override block. Keep `"system"` as a pseudo-value, not a token.
- **Use a theme in a test** — wrap under `<ThemeProvider>`. See `ThemeProvider.test.tsx` for fake-localStorage + fake-matchMedia patterns.

## Changelog

First introduced in [Unreleased](https://github.com/Pratiyush/translately/blob/master/CHANGELOG.md) (Phase 1). Ships with `v0.1.0`.
