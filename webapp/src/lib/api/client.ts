/**
 * Typed API client (T120) — thin wrapper over `openapi-fetch` bound to the
 * auto-generated `types.gen.ts` (regenerated from `docs/api/openapi.json`
 * via `pnpm codegen`).
 *
 * Callers use one of two shapes:
 *
 *   1. Direct fetch: `api.POST("/api/v1/auth/login", { body: {...} })`
 *      → returns `{ data, error, response }`. Discriminate on `data` /
 *      `error` yourself — useful when you want to render a specific
 *      error code inline (e.g. "EMAIL_NOT_VERIFIED" banner).
 *
 *   2. Throwing helpers: `unwrap(await api.POST(...))`
 *      → returns `data` on success, throws [ApiRequestError] on failure.
 *      Plays well with TanStack Query's error channel.
 *
 * `createApiClient` is the factory the app boot wires once; call sites
 * import the `api` singleton exported below for everything else.
 */
import createClient, { type Middleware } from 'openapi-fetch';
import { authStore } from '@/lib/auth/AuthStore';
import type { paths } from './types.gen';

export type { paths } from './types.gen';

/** The project-wide REST error envelope (api-conventions.md). */
export interface ApiError {
  code: string;
  message?: string;
  details?: Record<string, unknown>;
  traceId?: string;
}

/**
 * Thrown by [unwrap] (and TanStack Query retries) when the API returns a
 * 4xx / 5xx response. Carries the full error envelope from the uniform
 * error body so the UI can branch on `code` rather than HTTP status.
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly error: ApiError;

  constructor(status: number, error: ApiError) {
    super(error.message ?? error.code);
    this.name = 'ApiRequestError';
    this.status = status;
    this.error = error;
  }
}

export interface ApiClientOptions {
  baseUrl?: string;
  fetchImpl?: typeof fetch;
  /** Optional token provider — returns an access JWT, invoked per request. */
  bearerToken?: () => string | null | undefined;
}

/**
 * Construct an openapi-fetch client pinned to the backend's OpenAPI schema.
 * The same singleton powers every call in the app; tests can construct
 * their own with a mocked [fetchImpl] for isolated assertions.
 */
export function createApiClient(options: ApiClientOptions = {}) {
  // openapi-fetch builds a `new URL(path, baseUrl)` per request; an empty
  // baseUrl throws on a bare `/api/...` path. Default to the current
  // origin when running in a DOM; tests that pass their own baseUrl win.
  const baseUrl = options.baseUrl ?? (typeof window !== 'undefined' ? window.location.origin : '');
  // Read `fetch` lazily per call rather than capturing at construction.
  // Tests that stub `globalThis.fetch` AFTER module load (the normal
  // vitest pattern) rely on this late-binding.
  const fetchImpl =
    options.fetchImpl ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init));

  const client = createClient<paths>({ baseUrl, fetch: fetchImpl });

  // Attach the bearer token (if any) to every outbound request.
  if (options.bearerToken) {
    const middleware: Middleware = {
      onRequest({ request }) {
        const token = options.bearerToken!();
        if (token) request.headers.set('authorization', `Bearer ${token}`);
        return request;
      },
    };
    client.use(middleware);
  }

  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;

/**
 * Success-or-throw helper for openapi-fetch results. Returns `data` when
 * the request succeeded; throws [ApiRequestError] carrying the structured
 * envelope otherwise.
 *
 * ```ts
 * const me = unwrap(await api.GET("/api/v1/users/me"));
 * ```
 */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined && result.error !== null) {
    const envelope = normaliseError(result.error);
    throw new ApiRequestError(result.response.status, envelope);
  }
  if (result.data === undefined) {
    // 204 No Content — we tolerate undefined; callers that need a value
    // shouldn't call unwrap on a void endpoint.
    return undefined as T;
  }
  return result.data;
}

/**
 * The default, app-wide client. The bearer-token provider reads the
 * current access token from [authStore] on every request, so a sign-in
 * or sign-out that mutates the store flows through without rebuilding
 * the client. Expired-token handling (silent refresh) lands later; for
 * now the server returns `TOKEN_EXPIRED` and the UI prompts for
 * re-login.
 */
export const api: ApiClient = createApiClient({
  bearerToken: () => authStore.getTokens()?.accessToken ?? null,
});

// ---------------------------------------------------------------------------
// internals
// ---------------------------------------------------------------------------

interface ErrorEnvelope {
  error: ApiError;
}

function isErrorEnvelope(value: unknown): value is ErrorEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'error' in value &&
    typeof (value as ErrorEnvelope).error === 'object'
  );
}

function normaliseError(raw: unknown): ApiError {
  if (isErrorEnvelope(raw)) return raw.error;
  if (typeof raw === 'object' && raw !== null) return raw as ApiError;
  return { code: 'UNKNOWN_ERROR', message: String(raw) };
}
