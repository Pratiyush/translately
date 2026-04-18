import { describe, expect, it, vi } from 'vitest';
import { ApiRequestError, createApiClient, unwrap } from './client';

const baseUrl = 'https://api.test.example';

/**
 * Minimal fetch stub — vitest-friendly, returns a canned Response shape
 * (status + JSON body).
 */
function stubFetch(...responses: Array<{ status: number; body?: unknown }>): typeof fetch {
  let i = 0;
  return vi.fn(async () => {
    const canned = responses[Math.min(i, responses.length - 1)]!;
    i += 1;
    return new Response(canned.body === undefined ? null : JSON.stringify(canned.body), {
      status: canned.status,
      headers: { 'content-type': 'application/json' },
    });
  }) as unknown as typeof fetch;
}

describe('createApiClient', () => {
  it('attaches a Bearer token from the provider on every request', async () => {
    const calls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      // openapi-fetch passes a Request object as `input`; headers ride there,
      // not on `init`. Cover both shapes so any future refactor still fires.
      const headers = input instanceof Request ? input.headers : new Headers(init?.headers);
      calls.push(headers.get('authorization') ?? '');
      return new Response('{}', { status: 200, headers: { 'content-type': 'application/json' } });
    }) as unknown as typeof fetch;

    const client = createApiClient({ baseUrl, fetchImpl, bearerToken: () => 'tok-123' });
    await client.GET('/');
    await client.GET('/');

    expect(calls).toEqual(['Bearer tok-123', 'Bearer tok-123']);
  });

  it('omits the Authorization header when no token provider is set', async () => {
    let captured = '';
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const headers = input instanceof Request ? input.headers : new Headers(init?.headers);
      captured = headers.get('authorization') ?? '';
      return new Response('{}', { status: 200, headers: { 'content-type': 'application/json' } });
    }) as unknown as typeof fetch;

    const client = createApiClient({ baseUrl, fetchImpl });
    await client.GET('/');

    expect(captured).toBe('');
  });
});

describe('unwrap()', () => {
  it('returns data on 2xx', async () => {
    const client = createApiClient({
      baseUrl,
      fetchImpl: stubFetch({
        status: 200,
        body: {
          name: 'Translately',
          version: '0.1.0',
          docs: '/docs',
          health: '/q/health',
          openapi: '/q/openapi',
          swagger: '/q/swagger-ui',
        },
      }),
    });
    const result = await client.GET('/');
    const data = unwrap(result);
    expect(data).toMatchObject({ name: 'Translately', version: '0.1.0' });
  });

  it('throws ApiRequestError with the error envelope on 4xx', async () => {
    const client = createApiClient({
      baseUrl,
      fetchImpl: stubFetch({
        status: 400,
        body: {
          error: {
            code: 'VALIDATION_FAILED',
            message: 'body.email is required',
            details: { fields: [{ path: 'body.email', code: 'REQUIRED' }] },
          },
        },
      }),
    });
    const result = await client.POST('/api/v1/auth/login', {
      body: { email: '', password: '' },
    });
    expect(() => unwrap(result)).toThrow(ApiRequestError);

    try {
      unwrap(result);
    } catch (e) {
      const err = e as ApiRequestError;
      expect(err.status).toBe(400);
      expect(err.error.code).toBe('VALIDATION_FAILED');
      expect(err.error.message).toContain('body.email');
    }
  });

  it('throws on 5xx even when the response body is empty', async () => {
    const client = createApiClient({ baseUrl, fetchImpl: stubFetch({ status: 503 }) });
    const result = await client.GET('/');
    try {
      unwrap(result);
      expect.unreachable('should have thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiRequestError);
      expect((e as ApiRequestError).status).toBe(503);
    }
  });
});
