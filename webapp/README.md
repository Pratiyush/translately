# webapp/

Translately webapp — **React + Vite + TypeScript + Tailwind + shadcn primitives**.

## Common tasks

```bash
pnpm install                                   # once
pnpm --filter @translately/webapp dev          # Vite dev server on :5173
pnpm --filter @translately/webapp build        # production bundle to dist/
pnpm --filter @translately/webapp test         # Vitest + Testing Library + axe
pnpm --filter @translately/webapp lint         # eslint + prettier --check
pnpm --filter @translately/webapp typecheck    # tsc --noEmit
```

The Vite dev server proxies `/api/*` and `/q/*` to `http://localhost:8080` (the Quarkus backend from `./gradlew :backend:app:quarkusDev`).

## Layout

```
webapp/
├── public/                # served verbatim at /*
├── index.html             # app shell + theme pre-paint
├── src/
│   ├── components/
│   │   └── ui/            # shadcn primitives (owned, not vendored; ESLint-ignored)
│   ├── theme/
│   │   ├── tokens.css     # --background / --foreground / etc. for :root and .dark
│   │   └── ThemeProvider.tsx
│   ├── lib/utils.ts       # `cn()` for class merging
│   ├── i18n/              # dogfood strings (Phase 6 wires @translately/web SDK)
│   └── test/setup.ts      # Vitest + jest-dom setup
├── eslint.config.mjs
├── tailwind.config.ts
├── vite.config.ts
└── vitest.config.ts
```

## Theming

Tokens are HSL channel triples referenced as `hsl(var(--name))`. Never hard-code hex in components. Theme toggle cycles `light → dark → system`; `system` resolves via `prefers-color-scheme`. Flash-of-incorrect-theme is avoided by the inline script in `index.html`.

## i18n

The webapp dogfoods Translately for its own strings. Today `t(key)` resolves against the committed `src/i18n/en.json`. When the JS SDK lands in Phase 5, this wrapper flips to call the self-hosted Translately project `translately-webapp`.

**Never hard-code user-visible English in components.** Every string goes through `t()`.

## shadcn primitives

Primitives under `src/components/ui/**` are _owned_ copies, not a vendored library. Keep the canonical shadcn shape; tweak via `buttonVariants` or `cva` groups. ESLint ignores this folder to preserve shape.

## Accessibility

- Every interactive element has an accessible name.
- `axe-core` runs in `App.test.tsx`; 0 violations required.
- Focus rings always visible (`:focus-visible { outline: 2px solid … }`).
- Animations respect `prefers-reduced-motion`.

Every UI PR verifies **light AND dark mode** plus keyboard navigation before merge — part of the 14-point checklist in `.github/PULL_REQUEST_TEMPLATE.md`.
