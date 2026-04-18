/**
 * Placeholder API client. Task T120 replaces this thin `fetch` wrapper with
 * an openapi-typescript-generated client + TanStack Query integration bound
 * to the backend's OpenAPI document. Kept tiny and dependency-free so the
 * shell compiles against it today without committing to a shape.
 */

export interface ApiError {
  code: string;
  message?: string;
  details?: Record<string, unknown>;
}

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
}

export function createApiClient(options: ApiClientOptions = {}) {
  const baseUrl = options.baseUrl ?? '/api';
  const fetchImpl = options.fetchImpl ?? fetch.bind(globalThis);

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const res = await fetchImpl(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'content-type': 'application/json',
        ...(init.headers ?? {}),
      },
    });
    if (!res.ok) {
      let body: { error?: ApiError } = {};
      try {
        body = (await res.json()) as { error?: ApiError };
      } catch {
        // non-JSON error body — surface a generic code
      }
      throw new ApiRequestError(res.status, body.error ?? { code: `HTTP_${res.status}` });
    }
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }

  return {
    baseUrl,
    request,
    get: <T>(path: string, init?: RequestInit) => request<T>(path, { ...init, method: 'GET' }),
    post: <T>(path: string, body?: unknown, init?: RequestInit) =>
      request<T>(path, { ...init, method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
