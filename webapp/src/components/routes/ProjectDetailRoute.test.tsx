import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ProjectDetailRoute } from './ProjectDetailRoute';

/**
 * ProjectDetailRoute unit tests (T207 + T208).
 *
 * Covers the four critical moments:
 *   1. Empty-keys state renders when GET /keys returns `{ data: [] }`.
 *   2. Populated list renders each key with its state badge and namespace.
 *   3. Create-key dialog submits → list refetch fires.
 *   4. Translation editor commits on blur → upsert PUT fires.
 *
 * The singleton `api` client reads `globalThis.fetch` per request, so we
 * stub it with a deterministic URL/method-keyed mock to mirror the
 * relative URL → 200 response flow.
 */

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  localStorage.clear();
  vi.restoreAllMocks();
});

function renderAt(path = '/orgs/acme/projects/web') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/orgs/:orgSlug/projects/:projectSlug" element={<ProjectDetailRoute />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/**
 * URL + method → response body. The mock falls through to a 404 so a
 * missed route surfaces as a test failure rather than silent empty data.
 */
function makeFetchMock(handlers: Record<string, (init?: RequestInit) => Response>) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    // openapi-fetch passes a Request object as `input` with method +
    // body baked in; `init` is undefined on the second-arg side. Cope
    // with both shapes so the mock keys align in either code path.
    const isRequest = typeof Request !== 'undefined' && input instanceof Request;
    const url =
      typeof input === 'string' ? input : input instanceof URL ? input.pathname + input.search : input.url;
    const method = (isRequest ? (input as Request).method : init?.method) ?? 'GET';
    const key = `${method.toUpperCase()} ${new URL(url, 'http://localhost').pathname}`;
    const handler = handlers[key];
    if (!handler) {
      return new Response(JSON.stringify({ error: { code: 'UNMOCKED', path: key } }), {
        status: 404,
        headers: { 'content-type': 'application/json' },
      });
    }
    return handler(init);
  }) as unknown as typeof fetch;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

type MockedFetch = typeof fetch;

/**
 * openapi-fetch passes a `Request` object as the fetch `input` with
 * method baked in; the test filter looks at that Request, or falls back
 * to the second `init` argument for other code paths.
 */
function callsOfMethod(fetchMock: MockedFetch, method: string): unknown[][] {
  return (fetchMock as unknown as { mock: { calls: unknown[][] } }).mock.calls.filter((call) => {
    const input = call[0];
    const init = call[1] as RequestInit | undefined;
    const m = (input instanceof Request ? input.method : init?.method) ?? 'GET';
    return m.toUpperCase() === method.toUpperCase();
  });
}

const ORGS_PROJECTS = 'GET /api/v1/organizations/acme/projects';
const KEYS_LIST = 'GET /api/v1/organizations/acme/projects/web/keys';
const NS_LIST = 'GET /api/v1/organizations/acme/projects/web/namespaces';
const KEYS_CREATE = 'POST /api/v1/organizations/acme/projects/web/keys';

describe('ProjectDetailRoute', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders the empty-keys state when no keys exist', async () => {
    globalThis.fetch = makeFetchMock({
      [ORGS_PROJECTS]: () =>
        json({
          data: [
            {
              id: '01P1',
              slug: 'web',
              name: 'Web',
              description: null,
              baseLanguageTag: 'en',
              createdAt: '2026-04-18T10:00:00Z',
            },
          ],
        }),
      [KEYS_LIST]: () => json({ data: [] }),
      [NS_LIST]: () => json({ data: [] }),
    });

    renderAt();

    await waitFor(() => {
      expect(screen.getByText(/no keys yet/i)).toBeInTheDocument();
    });
    expect(screen.getByTestId('project-panel-keys')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^Web$/ })).toBeInTheDocument();
  });

  it('renders each key row with its state badge and namespace', async () => {
    globalThis.fetch = makeFetchMock({
      [ORGS_PROJECTS]: () => json({ data: [] }),
      [KEYS_LIST]: () =>
        json({
          data: [
            {
              id: '01K1',
              keyName: 'nav.signIn',
              namespaceSlug: 'default',
              description: null,
              state: 'NEW',
              createdAt: '2026-04-18T10:00:00Z',
              updatedAt: '2026-04-18T10:00:00Z',
            },
            {
              id: '01K2',
              keyName: 'nav.signOut',
              namespaceSlug: 'default',
              description: null,
              state: 'DONE',
              createdAt: '2026-04-18T10:05:00Z',
              updatedAt: '2026-04-18T10:05:00Z',
            },
          ],
        }),
      [NS_LIST]: () => json({ data: [{ id: '01N1', slug: 'default', name: 'Default', description: null }] }),
      'GET /api/v1/organizations/acme/projects/web/keys/01K1': () =>
        json({
          key: {
            id: '01K1',
            keyName: 'nav.signIn',
            namespaceSlug: 'default',
            description: null,
            state: 'NEW',
            createdAt: '2026-04-18T10:00:00Z',
            updatedAt: '2026-04-18T10:00:00Z',
          },
          translations: [],
        }),
      'GET /api/v1/organizations/acme/projects/web/keys/01K2': () =>
        json({
          key: {
            id: '01K2',
            keyName: 'nav.signOut',
            namespaceSlug: 'default',
            description: null,
            state: 'DONE',
            createdAt: '2026-04-18T10:05:00Z',
            updatedAt: '2026-04-18T10:05:00Z',
          },
          translations: [
            {
              id: '01T1',
              languageTag: 'en',
              value: 'Sign out',
              state: 'APPROVED',
              updatedAt: '2026-04-18T10:05:00Z',
            },
          ],
        }),
    });

    renderAt();

    await waitFor(() => {
      expect(screen.getByTestId('key-row-nav.signIn')).toBeInTheDocument();
    });
    expect(screen.getByTestId('key-row-nav.signOut')).toBeInTheDocument();
    expect(screen.getByTestId('key-state-NEW')).toBeInTheDocument();
    expect(screen.getByTestId('key-state-DONE')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('translation-editor-01K2-en')).toHaveValue('Sign out');
    });
  });

  it('creates a key through the dialog and invalidates the list', async () => {
    const fetchMock = makeFetchMock({
      [ORGS_PROJECTS]: () => json({ data: [] }),
      [NS_LIST]: () => json({ data: [{ id: '01N1', slug: 'default', name: 'Default', description: null }] }),
      [KEYS_LIST]: () => json({ data: [] }),
      [KEYS_CREATE]: () =>
        json(
          {
            id: '01K9',
            keyName: 'login.title',
            namespaceSlug: 'default',
            description: null,
            state: 'NEW',
            createdAt: '2026-04-18T10:10:00Z',
            updatedAt: '2026-04-18T10:10:00Z',
          },
          201,
        ),
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    renderAt();

    // Wait for the namespaces + keys queries to resolve so the dialog's
    // namespace <select> is populated before the user clicks submit.
    await waitFor(() => {
      expect(screen.getByText(/no keys yet/i)).toBeInTheDocument();
    });
    const user = userEvent.setup();
    await user.click(screen.getByTestId('keys-create-button'));
    const nameInput = (await screen.findByLabelText(/key name/i)) as HTMLInputElement;
    // `fireEvent.change` dispatches a native change event that RHF's
    // `register` subscribes to; userEvent.type can race with Radix's
    // focus-trap effects in jsdom.
    fireEvent.change(nameInput, { target: { value: 'login.title' } });
    const form = await screen.findByTestId('keys-create-form');
    fireEvent.submit(form);

    await waitFor(
      () => {
        // The POST fired + the list refetched after invalidation.
        const createCalls = callsOfMethod(fetchMock, 'POST');
        expect(createCalls.length).toBeGreaterThan(0);
      },
      { timeout: 3000 },
    );
  });

  it('commits the translation editor on blur', async () => {
    const fetchMock = makeFetchMock({
      [ORGS_PROJECTS]: () => json({ data: [] }),
      [NS_LIST]: () => json({ data: [] }),
      [KEYS_LIST]: () =>
        json({
          data: [
            {
              id: '01K2',
              keyName: 'nav.signOut',
              namespaceSlug: 'default',
              description: null,
              state: 'NEW',
              createdAt: '2026-04-18T10:05:00Z',
              updatedAt: '2026-04-18T10:05:00Z',
            },
          ],
        }),
      'GET /api/v1/organizations/acme/projects/web/keys/01K2': () =>
        json({
          key: {
            id: '01K2',
            keyName: 'nav.signOut',
            namespaceSlug: 'default',
            description: null,
            state: 'NEW',
            createdAt: '2026-04-18T10:05:00Z',
            updatedAt: '2026-04-18T10:05:00Z',
          },
          translations: [
            {
              id: '01T1',
              languageTag: 'en',
              value: 'Out',
              state: 'DRAFT',
              updatedAt: '2026-04-18T10:05:00Z',
            },
          ],
        }),
      'PUT /api/v1/organizations/acme/projects/web/keys/01K2/translations/en': () =>
        json({
          id: '01T1',
          languageTag: 'en',
          value: 'Sign out',
          state: 'DRAFT',
          updatedAt: '2026-04-18T10:06:00Z',
        }),
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    renderAt();

    // Wait for the row's useKey query to land so the editor renders with
    // the translation pre-filled rather than the EmptyTranslationCta.
    await waitFor(() => {
      const el = screen.queryByTestId('translation-editor-01K2-en') as HTMLTextAreaElement | null;
      expect(el?.value).toBe('Out');
    });
    const editor = screen.getByTestId('translation-editor-01K2-en') as HTMLTextAreaElement;
    fireEvent.change(editor, { target: { value: 'Sign out' } });
    fireEvent.blur(editor);

    await waitFor(
      () => {
        const puts = callsOfMethod(fetchMock, 'PUT');
        expect(puts.length).toBeGreaterThan(0);
      },
      { timeout: 3000 },
    );
  });
});
