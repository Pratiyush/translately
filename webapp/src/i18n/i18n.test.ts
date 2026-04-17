import { describe, expect, it } from 'vitest';
import en from './en.json';
import { t } from './index';

describe('t()', () => {
  it('returns the English string for a known key', () => {
    expect(t('app.title')).toBe('Translately');
  });

  it('returns the taglne verbatim from en.json', () => {
    expect(t('app.tagline')).toBe(en['app.tagline']);
  });

  it('falls back to the key for a missing translation', () => {
    // Cast via unknown so we can assert the fallback path — never in production.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((t as any)('missing.key')).toBe('missing.key');
  });

  it('covers every key declared in en.json', () => {
    const keys = Object.keys(en) as Array<keyof typeof en>;
    expect(keys.length).toBeGreaterThan(0);
    for (const key of keys) {
      expect(t(key)).toBe(en[key]);
    }
  });

  it('never returns an empty string for any declared key', () => {
    for (const [key, value] of Object.entries(en)) {
      expect(value, `i18n key ${key} is empty`).not.toBe('');
    }
  });
});
