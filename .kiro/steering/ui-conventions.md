# UI conventions — always-loaded steering

The webapp is how most people meet Translately. "God-level UI" is a concrete bar, not a vibe. Every UI PR must clear this file.

## Stack

- **React 18+** · **Vite** · **TypeScript strict**.
- **Tailwind** + **shadcn/ui** (ownable copy-in components, not a vendored lib) + **Radix** primitives underneath.
- **TanStack Query** for server state. **React Router** for routing. **React Hook Form** + **Zod** for forms.
- **Lucide React** for icons — **no other icon set**. Ever.
- **Framer Motion** for animation. Respect `prefers-reduced-motion` at all call sites.
- **CodeMirror 6** with a custom ICU mode for translation editor cells.
- **cmdk** (or equivalent) for the `⌘K` command palette.

## Theming

- Tokens defined **once** in `webapp/src/theme/tokens.css` for `:root` and `.dark`.
- Colors referenced as CSS variables (`hsl(var(--primary))`), never hard-coded hex in components.
- Typography: **Inter** for UI, **JetBrains Mono** for code and key names.
- Theme toggle: **system / light / dark**. Persisted per user in localStorage; synced to profile on login.
- Every component verified in both light AND dark mode before merge. PR template has a checkbox.

## Iconography

- Lucide only. Import from `lucide-react`, no custom SVG imports mixing in.
- Icons paired with text in buttons unless the action is universally understood (close `X`, search magnifier). Icon-only buttons must have `aria-label`.

## Motion

- Durations: **200ms** for page/panel transitions, **100ms** for micro-interactions.
- Easing: `cubic-bezier(0.22, 1, 0.36, 1)` (default) or Framer's `easeOut`.
- **Respect `prefers-reduced-motion`**. When true, animations collapse to instant state changes; no opacity/scale transitions.

## Layout & density

- Three density modes — **compact / comfortable / spacious** — saved per user.
- Base unit: 4px grid. Only multiples of 4 in `p-*` / `m-*` / gap.
- Maximum content width at `7xl` (1280px) for long-form pages; editor pages use full viewport.

## Empty states

- Never blank. Every empty list renders:
  1. A short, friendly explanation of what goes here.
  2. A primary action (e.g., "Create your first key").
  3. A link to the relevant docs page.
  4. An illustration (Lucide or a project-owned SVG) — never a stock placeholder.

## Loading

- **Skeleton loaders** for list and table views. Skeleton shape matches the final layout.
- **Optimistic updates** via TanStack Query mutations for anything the user is likely to do more than once (toggle, rename, translate).
- **Spinner of last resort** for operations >300ms with no progress signal.

## Feedback (toasts)

- Toast on every successful mutation. Dismiss after 4s by default; sticky for errors.
- Errors show the `error.code` translated via webapp i18n; `details` shown only in a collapsible "Details" section.
- Destructive actions confirm via an AlertDialog, not a toast-after.

## Keyboard

- **Every action reachable without a mouse.**
- Focus rings always visible (no `outline: none`). Use `focus-visible:` for keyboard-only rings.
- `Esc` closes any modal / dialog / command palette.
- Shortcuts listed in `⌘K` under "Shortcuts".
- Translation table: arrow keys move cells, `Enter` edits, `Esc` cancels, `Cmd/Ctrl+S` saves, `Cmd/Ctrl+K` opens command palette.

## Command palette (⌘K)

- Fuzzy-matches **navigation, actions, settings, and recent items**.
- Grouped: *Navigate · Create · Actions · Settings · Recent*.
- Shortcuts shown inline with chips.
- Opens with `⌘K` (Mac) / `Ctrl+K` (Win/Linux). Configurable per user.

## Accessibility

- **WCAG 2.1 AA minimum.** Text contrast ≥4.5:1, large text ≥3:1.
- All interactive elements have an accessible name (text, `aria-label`, `aria-labelledby`).
- `axe-core` assertion in every Vitest test that renders a page; 0 violations required.
- Semantic HTML first: `<button>` over `<div role="button">`.
- Forms: labels associated with inputs; errors announced via `aria-describedby` + `aria-invalid`.

## Forms

- React Hook Form + Zod resolver.
- Inline errors below the field; no toast for field validation.
- Submit button disabled while pending; spinner inside the button, not overlaying the form.

## Tables

- Virtualized (`@tanstack/react-virtual`) for >200 rows.
- Sticky header; sticky first column on translation tables.
- Column resize + reorder saved per user per view.

## i18n (dogfooding)

- The webapp dogfoods Translately for its own UI strings. Every UI string PR also updates the `translately-webapp` project via the CLI (or CI script once available).
- Strings live in `webapp/src/i18n/en.json`; runtime resolution via a thin wrapper around the Translately JS SDK.
- Never hard-code user-visible English outside `en.json`.

## Performance budget

- Lighthouse on the webapp: **Performance ≥90, Accessibility ≥95, Best Practices ≥95, SEO ≥90**.
- Bundle budget: initial JS ≤250 KB gzipped; CSS ≤50 KB gzipped. Code-split per route.
- Image assets served as WebP/AVIF with PNG fallback; explicit width/height on every `<img>`.

## PR checklist (UI additions)

- [ ] Verified in light AND dark mode.
- [ ] Verified with keyboard only (no mouse).
- [ ] `axe` shows 0 violations in the component's Vitest test.
- [ ] Respects `prefers-reduced-motion`.
- [ ] Strings in `webapp/src/i18n/en.json`; no hard-coded English in components.
- [ ] Lucide icons only; no mixed icon sets.
- [ ] Lighthouse scores did not regress below the budget.
- [ ] Screenshots (light + dark) attached to the PR.
