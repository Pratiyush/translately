import en from './en.json';

/**
 * Placeholder i18n wrapper. The real wiring (Phase 6) plugs @translately/web
 * against a self-hosted Translately project `translately-webapp`. Until then,
 * strings resolve from the committed `en.json` shipped with the webapp.
 *
 * Never hard-code user-visible English in components — always go through `t()`.
 */
type Key = keyof typeof en;

export function t(key: Key): string {
  return en[key] ?? key;
}
