import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authStore } from '@/lib/auth/AuthStore';
import { SignInRoute } from './SignInRoute';

/**
 * SignInRoute unit tests (T117). Exercise the critical credential flow:
 *   1. Valid submission stores tokens + user, triggers navigation.
 *   2. Backend `INVALID_CREDENTIALS` surfaces the localised error.
 *   3. Client-side Zod validation blocks an empty submission.
 *
 * The auth flow uses the app-singleton `api` client, which reads
 * `fetch.bind(globalThis)` at construction. We swap `globalThis.fetch`
 * with a stub for the duration of each test so no real HTTP leaves
 * jsdom.
 */

const ACCESS_TOKEN_PAYLOAD = btoa(
  JSON.stringify({
    sub: '01HT7F8KXN0GZJYQP3M5CRSBNW',
    upn: 'alice@example.com',
    scope: 'org.read projects.read',
    orgs: [],
    typ: 'access',
  }),
);
// Compact JWT header.payload.signature — signature doesn't matter here
// (no verification on the webapp); just needs three `.`-separated parts.
const FAKE_JWT = `eyJhbGciOiJSUzI1NiJ9.${ACCESS_TOKEN_PAYLOAD}.sig`;

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  localStorage.clear();
  vi.restoreAllMocks();
});

function renderSignIn() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/signin']}>
        <Routes>
          <Route path="/signin" element={<SignInRoute />} />
          <Route path="/" element={<div data-testid="route-home">home</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SignInRoute', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('submits the form, stores tokens, and redirects on success', async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            accessToken: FAKE_JWT,
            accessExpiresAt: '2026-04-18T11:00:00Z',
            refreshToken: 'refresh.jwt.sig',
            refreshExpiresAt: '2026-05-18T11:00:00Z',
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
    ) as typeof fetch;

    renderSignIn();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/email/i), 'alice@example.com');
    await user.type(screen.getByLabelText(/password/i), 'correcthorsestaple!');
    await user.click(screen.getByTestId('sign-in-submit'));

    await waitFor(() => {
      expect(screen.getByTestId('route-home')).toBeInTheDocument();
    });

    const stored = authStore.getTokens();
    expect(stored?.accessToken).toBe(FAKE_JWT);
    const user_ = authStore.getSnapshot();
    expect(user_?.email).toBe('alice@example.com');
  });

  it('shows the localised error when the server returns INVALID_CREDENTIALS', async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.' },
          }),
          { status: 401, headers: { 'content-type': 'application/json' } },
        ),
    ) as typeof fetch;

    renderSignIn();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/email/i), 'alice@example.com');
    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByTestId('sign-in-submit'));

    const err = await screen.findByTestId('sign-in-error');
    expect(err).toHaveTextContent(/incorrect/i);
    // No navigation happened.
    expect(screen.queryByTestId('route-home')).not.toBeInTheDocument();
  });

  it('blocks an empty submission with client-side validation (no fetch)', async () => {
    const fetchStub = vi.fn();
    globalThis.fetch = fetchStub as unknown as typeof fetch;

    renderSignIn();

    const user = userEvent.setup();
    await user.click(screen.getByTestId('sign-in-submit'));

    // Zod caught `email` as INVALID_EMAIL and `password` as REQUIRED; fetch
    // should never have been called.
    await waitFor(() => {
      expect(screen.getByText(/valid email address/i)).toBeInTheDocument();
    });
    expect(fetchStub).not.toHaveBeenCalled();
  });
});
