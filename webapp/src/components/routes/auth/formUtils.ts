/**
 * Shared helpers for every auth form — error-code → human message mapping
 * + a small hook to surface mutation errors in an `aria-live` region.
 */
import { ApiRequestError } from '@/lib/api/client';
import { t } from '@/i18n';

/**
 * Map a backend `error.code` to a user-facing message. Unknown codes fall
 * back to the `error.message` string the server sent, so the UI stays
 * informative even for codes the frontend doesn't know about yet
 * (forward-compat per the API versioning rules).
 */
export function formatAuthError(err: unknown): string {
  if (err instanceof ApiRequestError) {
    const key = `auth.error.${err.error.code}`;
    const translated = t(key as 'auth.error.INVALID_CREDENTIALS');
    // If t() returned the key back unchanged, fall back to the server
    // message — this is the fallback path for unknown error codes.
    if (translated !== key) return translated;
    return err.error.message ?? err.error.code;
  }
  if (err instanceof Error) return err.message;
  return t('auth.error.GENERIC');
}

/**
 * Pull field-level validation errors out of a `VALIDATION_FAILED` envelope.
 * Returns `{ [fieldPath]: code }` so the form can attach per-field messages.
 */
export function extractFieldErrors(err: unknown): Record<string, string> {
  if (!(err instanceof ApiRequestError) || err.error.code !== 'VALIDATION_FAILED') return {};
  const details = err.error.details;
  if (!details || typeof details !== 'object') return {};
  const fields = (details as { fields?: Array<{ path?: string; code?: string }> }).fields;
  if (!Array.isArray(fields)) return {};
  const map: Record<string, string> = {};
  for (const f of fields) {
    if (typeof f.path === 'string' && typeof f.code === 'string') {
      // Strip the `body.` prefix the backend uses so form field names match.
      const key = f.path.startsWith('body.') ? f.path.slice(5) : f.path;
      map[key] = f.code;
    }
  }
  return map;
}
