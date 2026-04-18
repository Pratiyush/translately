---
title: Screenshots
parent: Product
nav_order: 99
---

# Screenshots

Every user-visible page in Translately ships with a light-mode and a dark-mode screenshot committed to `docs/product/screenshots/`. The images are embedded in the matching product page via a `<picture>` element that uses `prefers-color-scheme` to switch variants, so the docs site (and any third-party renderer that honours the media query) picks the theme that matches the reader's OS.

Per [CLAUDE.md rule #7](https://github.com/Pratiyush/translately/blob/master/CLAUDE.md), every UI change must be verified in both themes; committing both captures with the change is the enforcement mechanism.

## How to regenerate

1. Start the webapp dev server in one terminal:

   ```sh
   pnpm --filter @translately/webapp dev
   ```

2. Run the capture script from another terminal:

   ```sh
   pnpm --filter @translately/webapp screenshots
   ```

   The script writes PNGs to `docs/product/screenshots/<page>-<light|dark>.png` (resolved relative to the repo root regardless of cwd).

3. Review the diff and commit both variants of every changed page. Don't land a single-theme update — the reader switches OS theme and the `<picture>` fallback flips silently.

The script (`webapp/scripts/capture-screenshots.mjs`) uses Playwright's headless Chromium at a 1440×900 viewport with a 2x device-scale for retina-sharp output. It seeds `localStorage` with a deterministic dev user + the chosen theme via `addInitScript`, so the first paint renders with the intended theme and authenticated content — no post-load flicker.

## What gets captured today

| Page | Path | Screenshot base name |
|---|---|---|
| Sign in | `/signin` | `signin` |
| Sign up | `/signup` | `signup` |
| Forgot password | `/forgot-password` | `forgot-password` |
| Verify email (pending) | `/verify-email?token=demo-token` | `verify-email-pending` |
| Dashboard (shell) | `/` | `dashboard` |
| Organizations | `/orgs` | `orgs` |
| Projects | `/projects` | `projects` |

Adding a new page? Append it to the `PAGES` array in `capture-screenshots.mjs` with a stable `slug`, then re-run. Never rename an existing slug without updating every `<picture>` reference — the image paths are load-bearing for the live site.

## Dependency note

`playwright` is a devDependency of `@translately/webapp`. The first run downloads the chromium browser into `~/Library/Caches/ms-playwright` (~200 MB); subsequent runs are cached. The screenshot generator isn't in the CI critical path — it's a developer tool, run locally when shipping UI changes.
