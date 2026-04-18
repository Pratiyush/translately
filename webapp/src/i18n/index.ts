import en from './en.json';

/**
 * Placeholder i18n wrapper. The real wiring (Phase 6) plugs @translately/web
 * against a self-hosted Translately project `translately-webapp`. Until then,
 * strings resolve from the committed `en.json` shipped with the webapp.
 *
 * Never hard-code user-visible English in components — always go through `t()`.
 */
type KnownKey = keyof typeof en;

/**
 * Translate `key`. If the key is unknown, the key itself is returned
 * verbatim so tests that construct keys dynamically (e.g. `auth.error.XX`)
 * can detect "no translation" via equality with the key.
 *
 * Pass `params` to interpolate `{name}` tokens in the resolved string.
 */
export function t(key: KnownKey | string, params?: Record<string, string | number>): string {
  const source = (en as Record<string, string>)[key as string] ?? key;
  if (!params) return source;
  return source.replace(/\{(\w+)\}/g, (match, name: string) => {
    const v = params[name];
    return v === undefined ? match : String(v);
  });
}
