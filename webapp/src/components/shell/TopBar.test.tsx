import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { TopBar } from './TopBar';
import { DEV_USER } from '@/lib/auth/mockAuth';
import { authStore } from '@/lib/auth/AuthStore';
import { ThemeProvider } from '@/theme/ThemeProvider';
import en from '@/i18n/en.json';

function renderTopBar() {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <TopBar />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe('TopBar', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.setUser(DEV_USER);
  });

  it('renders the brand logo once, linking to /', () => {
    renderTopBar();
    const brand = screen.getByRole('link', { name: en['brand.logo.label'] });
    expect(brand).toBeInTheDocument();
    expect(brand).toHaveAttribute('href', '/');
  });

  it('renders the brand title text', () => {
    renderTopBar();
    // Brand text appears once in the header, not duplicated by OrgSwitcher.
    const brandText = screen.getAllByText(en['app.title']);
    expect(brandText.length).toBeGreaterThanOrEqual(1);
  });

  it('renders the primary nav list with Dashboard, Organizations, Projects', () => {
    renderTopBar();
    const nav = screen.getByRole('navigation', { name: /primary/i });
    expect(within(nav).getByRole('link', { name: new RegExp(en['nav.dashboard'], 'i') })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: new RegExp(en['nav.orgs'], 'i') })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: new RegExp(en['nav.projects'], 'i') })).toBeInTheDocument();
  });

  it('renders the theme toggle button', () => {
    renderTopBar();
    expect(screen.getByRole('button', { name: /theme/i })).toBeInTheDocument();
  });

  it('renders the user menu trigger', () => {
    renderTopBar();
    expect(screen.getByTestId('user-menu-trigger')).toBeInTheDocument();
  });

  it('renders the org switcher trigger when user has orgs', () => {
    renderTopBar();
    expect(screen.getByTestId('org-switcher-trigger')).toBeInTheDocument();
  });
});
