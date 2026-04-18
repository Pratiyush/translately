import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OrgsRoute } from './OrgsRoute';

/**
 * OrgsRoute unit tests (T118).
 *
 * Verify the three critical moments:
 *   1. Empty state renders when GET /organizations returns `{ data: [] }`.
 *   2. A populated list renders each org and each row links to /orgs/{slug}.
 *   3. Create dialog submits → the UI re-fetches (so the list tests below
 *      reflect the new org).
 *
 * Each test stubs `globalThis.fetch` because the singleton `api` client
 * reads it lazily per request.
 */

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  localStorage.clear();
  vi.restoreAllMocks();
});

function renderOrgs() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/orgs']}>
        <Routes>
          <Route path="/orgs" element={<OrgsRoute />} />
          <Route path="/orgs/:orgSlug" element={<div data-testid="route-org-detail">detail</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('OrgsRoute', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('shows the empty state when the user belongs to zero orgs', async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(JSON.stringify({ data: [] }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
    ) as typeof fetch;

    renderOrgs();

    await waitFor(() => {
      expect(screen.getByText(/no organizations yet/i)).toBeInTheDocument();
    });
  });

  it('renders one card per org and links each to /orgs/{slug}', async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            data: [
              {
                id: '01HT1',
                slug: 'acme',
                name: 'Acme Corp',
                callerRole: 'OWNER',
                createdAt: '2026-04-18T10:00:00Z',
              },
              {
                id: '01HT2',
                slug: 'zed',
                name: 'Zed Inc',
                callerRole: 'MEMBER',
                createdAt: '2026-04-18T10:05:00Z',
              },
            ],
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
    ) as typeof fetch;

    renderOrgs();

    await waitFor(() => {
      expect(screen.getByTestId('orgs-item-acme')).toBeInTheDocument();
    });
    expect(screen.getByTestId('orgs-item-acme')).toHaveAttribute('href', '/orgs/acme');
    expect(screen.getByTestId('orgs-item-zed')).toHaveAttribute('href', '/orgs/zed');
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('Zed Inc')).toBeInTheDocument();
  });

  it('creates an org via the dialog and refetches the list', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: [] }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: '01HT3',
            slug: 'new-org',
            name: 'New Org',
            callerRole: 'OWNER',
            createdAt: '2026-04-18T10:10:00Z',
          }),
          { status: 201, headers: { 'content-type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: [
              {
                id: '01HT3',
                slug: 'new-org',
                name: 'New Org',
                callerRole: 'OWNER',
                createdAt: '2026-04-18T10:10:00Z',
              },
            ],
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      );
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    renderOrgs();

    await waitFor(() => {
      expect(screen.getByText(/no organizations yet/i)).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByTestId('orgs-create-button'));
    const nameInput = await screen.findByLabelText(/organization name/i);
    await user.type(nameInput, 'New Org');
    await user.click(screen.getByTestId('orgs-create-submit'));

    await waitFor(
      () => {
        expect(screen.getByTestId('orgs-item-new-org')).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
