# docs/

The `docs/` directory is the source for the [GitHub Pages site](https://pratiyush.github.io/translately/). The [`pages.yml`](../.github/workflows/pages.yml) workflow deploys `docs/` verbatim on every push to `master`.

## Structure

- [`index.html`](index.html) — landing page (hero, roadmap, quickstart)
- `getting-started/` — onboarding guides (added in Phase 1)
- `self-hosting/` — operator guides incl. `hardening.md` (Phase 1)
- `api/openapi.json` — auto-generated OpenAPI spec (Phase 1)
- `sdks/` — per-SDK guides (Phase 5+)
- `migration/` — migration guides from other localization platforms (Phase 7)

## Local preview

```bash
# Any static server works
python3 -m http.server -d docs 8000
# http://localhost:8000/
```

## Writing style

- Open source, technical, direct. No marketing fluff.
- All code blocks tested and copy-paste runnable.
- Every page links to the relevant `CHANGELOG.md` version that introduced the feature.
- Accessibility: semantic HTML, keyboard-nav friendly, respects `prefers-color-scheme`.
