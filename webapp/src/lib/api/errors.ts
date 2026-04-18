/**
 * Error-envelope → human string mapping used across the orgs / projects /
 * members UI. Mirrors the `error.code` catalogue in
 * `docs/api/organizations-and-projects.md`.
 *
 * We look up `api.error.{code}` in `en.json`; if nothing matches we fall
 * back to the envelope's `message`, then a generic "unknown" bucket.
 */
import { t } from '@/i18n';
import type { ApiRequestError } from './client';

export function formatApiError(error: unknown): string {
  if (!error) return t('api.error.UNKNOWN');
  if (isApiRequestError(error)) {
    const code = error.error.code;
    const key = `api.error.${code}`;
    const resolved = t(key);
    if (resolved !== key) return resolved;
    return error.error.message ?? t('api.error.UNKNOWN');
  }
  if (error instanceof Error) return error.message;
  return t('api.error.UNKNOWN');
}

function isApiRequestError(error: unknown): error is ApiRequestError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'error' in error &&
    typeof (error as ApiRequestError).error === 'object'
  );
}
