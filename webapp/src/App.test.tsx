import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import axe from 'axe-core';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppRoutes } from './router';
import en from './i18n/en.json';
import { DEV_USER } from './lib/auth/mockAuth';
import { authStore } from './lib/auth/AuthStore';
import { ThemeProvider } from './theme/ThemeProvider';

/**
 * Integration-style tests for the router + shell combination. We render the
 * app-routes module inside a `MemoryRouter` so we can assert what's visible
 * at a given URL without touching `window.history`.
 */

function renderAt(initialPath: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[initialPath]}>
          <AppRoutes />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

function seedAuth() {
  authStore.setUser(DEV_USER);
}

describe('App routing — authenticated', () => {
  beforeEach(() => {
    localStorage.clear();
    seedAuth();
  });

  it('renders the dashboard at /', () => {
    renderAt('/');
    expect(screen.getByTestId('route-dashboard')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(en['route.dashboard.title']);
  });

  it('renders the orgs placeholder at /orgs', () => {
    renderAt('/orgs');
    expect(screen.getByTestId('route-orgs')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(en['route.orgs.title']);
  });

  it('renders the projects placeholder at /projects', () => {
    renderAt('/projects');
    expect(screen.getByTestId('route-projects')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(en['route.projects.title']);
  });

  it('renders the 404 for unknown paths', () => {
    renderAt('/this-path-does-not-exist');
    expect(screen.getByTestId('route-not-found')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(en['route.not-found.title']);
  });

  it('mounts the shell TopBar on every known route', () => {
    renderAt('/projects');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
  });
});

describe('App routing — unauthenticated', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.signOut();
  });

  it('redirects / to /signin when no user is present', () => {
    renderAt('/');
    expect(screen.getByTestId('route-sign-in')).toBeInTheDocument();
  });

  it('renders the sign-in route directly', () => {
    renderAt('/signin');
    expect(screen.getByTestId('route-sign-in')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(en['route.sign-in.title']);
  });
});

describe('App accessibility', () => {
  beforeEach(() => {
    localStorage.clear();
    seedAuth();
  });

  it('has no axe violations in light mode', async () => {
    localStorage.setItem('translately.theme', 'light');
    const { container } = renderAt('/');
    const results = await axe.run(container, { resultTypes: ['violations'] });
    expect(results.violations).toEqual([]);
  });

  it('has no axe violations in dark mode', async () => {
    localStorage.setItem('translately.theme', 'dark');
    const { container } = renderAt('/');
    const results = await axe.run(container, { resultTypes: ['violations'] });
    expect(results.violations).toEqual([]);
  });
});
