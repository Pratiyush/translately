// Capture product-doc screenshots for every theme-sensitive page.
//
// Usage from the repo root:
//   pnpm --filter @translately/webapp dev           # starts on :5173
//   pnpm --filter @translately/webapp screenshots   # captures screenshots
//
// Or from the webapp/ directory: `pnpm screenshots`.
//
// Precondition: the webapp dev server must be running on http://localhost:5173
// (override with BASE_URL). A backend is optional — routes that need data
// but find no backend render their loading/empty states, which are
// themselves useful screenshots for a first-run experience.
//
// Output: `docs/product/screenshots/<page>-<theme>.png` (relative to repo
// root). Commit the generated PNGs alongside the prose that references
// them.

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Script lives at webapp/scripts/capture-screenshots.mjs; resolve the
// repo root relative to that so the script works regardless of cwd.
const __filename = fileURLToPath(import.meta.url);
const REPO_ROOT = resolve(dirname(__filename), '..', '..');
const OUT_DIR = resolve(REPO_ROOT, 'docs/product/screenshots');
mkdirSync(OUT_DIR, { recursive: true });

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';

// DEV user shape mirrors webapp/src/lib/auth/mockAuth.ts so the app
// shell renders realistic orgs + an active-org badge.
const DEV_USER = {
  id: 'user_01HQZZZZZZZZZZZZZZZZZZZZZZ',
  email: 'alice@example.com',
  fullName: 'Alice Example',
  orgs: [
    {
      id: 'org_01HQAAAA000000000000000000',
      slug: 'acme',
      name: 'Acme Corp',
      role: 'OWNER',
    },
    {
      id: 'org_01HQBBBB000000000000000000',
      slug: 'contoso',
      name: 'Contoso Ltd',
      role: 'MEMBER',
    },
  ],
  activeOrgId: 'org_01HQAAAA000000000000000000',
};

// Matches the storage shape AuthStore expects.
const AUTH_STORAGE_KEY = 'translately.mockUser';

const PAGES = [
  // Public auth surface
  { slug: 'signin', path: '/signin', seed: false },
  { slug: 'signup', path: '/signup', seed: false },
  { slug: 'forgot-password', path: '/forgot-password', seed: false },
  { slug: 'verify-email-pending', path: '/verify-email?token=demo-token', seed: false },

  // Authenticated surface — seed dev user into localStorage first
  { slug: 'dashboard', path: '/', seed: true },
  { slug: 'orgs', path: '/orgs', seed: true },
  { slug: 'projects', path: '/projects', seed: true },
];

const THEMES = /** @type {const} */ (['light', 'dark']);

async function captureThemed(page, { slug, path }, theme) {
  // Theme is applied via an init-script attached per entry, not via
  // post-load localStorage (which would miss the first paint).
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle', timeout: 15_000 });

  // Small settle delay — Radix portals and the webfont swap can redraw.
  await page.waitForTimeout(500);

  const out = `${OUT_DIR}/${slug}-${theme}.png`;
  await page.screenshot({ path: out, fullPage: false });
  console.log(`✓ ${slug} ${theme} → ${out}`);
}

const browser = await chromium.launch();
try {
  for (const entry of PAGES) {
    for (const theme of THEMES) {
      // A fresh context per (page × theme) so localStorage state is
      // deterministic. addInitScript seeds storage before any page
      // script runs, so the first paint already has the right theme +
      // auth state.
      const context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
        deviceScaleFactor: 2,
      });
      await context.addInitScript(
        ({ theme, authKey, user, seed }) => {
          try {
            localStorage.setItem('translately.theme', theme);
            if (seed) {
              localStorage.setItem(authKey, JSON.stringify(user));
              localStorage.setItem(
                'translately.activeOrgId',
                user.activeOrgId,
              );
            }
          } catch {
            // Some iframes throw on localStorage access in partitioned
            // storage contexts; ignore and let the page recover.
          }
        },
        { theme, authKey: AUTH_STORAGE_KEY, user: DEV_USER, seed: entry.seed },
      );
      const page = await context.newPage();
      await captureThemed(page, entry, theme);
      await context.close();
    }
  }
} finally {
  await browser.close();
}
