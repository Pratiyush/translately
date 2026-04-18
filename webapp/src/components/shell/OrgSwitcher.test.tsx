import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrgSwitcher } from './OrgSwitcher';
import { DEV_USER } from '@/lib/auth/mockAuth';
import { authStore, type AuthUser } from '@/lib/auth/AuthStore';
import { ThemeProvider } from '@/theme/ThemeProvider';
import en from '@/i18n/en.json';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="current-path">{location.pathname}</span>;
}

function renderSwitcher(initialPath = '/') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route
            path="/*"
            element={
              <>
                <OrgSwitcher />
                <LocationProbe />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe('OrgSwitcher — empty state', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.signOut();
  });

  it('renders the "create your first org" CTA when no user is present', () => {
    renderSwitcher();
    const btn = screen.getByRole('button', { name: new RegExp(en['org.switcher.none'], 'i') });
    expect(btn).toBeInTheDocument();
  });

  it('renders the empty-state CTA when user has zero orgs', () => {
    const userNoOrgs: AuthUser = { ...DEV_USER, orgs: [], activeOrgId: null };
    authStore.setUser(userNoOrgs);
    renderSwitcher();
    expect(
      screen.getByRole('button', { name: new RegExp(en['org.switcher.none'], 'i') }),
    ).toBeInTheDocument();
  });

  it('navigates to /orgs when the empty-state CTA is clicked', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByRole('button', { name: new RegExp(en['org.switcher.none'], 'i') }));
    expect(screen.getByTestId('current-path')).toHaveTextContent('/orgs');
  });
});

describe('OrgSwitcher — populated state', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.setUser(DEV_USER);
  });

  it('labels the trigger with the active org name', () => {
    renderSwitcher();
    const trigger = screen.getByTestId('org-switcher-trigger');
    // DEV_USER active is "Acme Corp"
    expect(trigger).toHaveTextContent(/acme corp/i);
  });

  it('trigger carries an accessible name', () => {
    renderSwitcher();
    const trigger = screen.getByTestId('org-switcher-trigger');
    expect(trigger).toHaveAttribute('aria-label', en['org.switcher.open']);
  });

  it('opens a menu with one item per org, sorted alphabetically', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId('org-switcher-trigger'));
    const menu = await screen.findByRole('menu');
    const items = within(menu).getAllByRole('menuitem');
    // Alphabetical: Acme Corp then Contoso Ltd.
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent(/acme corp/i);
    expect(items[1]).toHaveTextContent(/contoso ltd/i);
  });

  it('shows the role pill for every option', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId('org-switcher-trigger'));
    const menu = await screen.findByRole('menu');
    // Owner for Acme, Member for Contoso (from DEV_USER)
    expect(within(menu).getByText(en['org.role.OWNER'])).toBeInTheDocument();
    expect(within(menu).getByText(en['org.role.MEMBER'])).toBeInTheDocument();
  });

  it('arrow keys navigate between items and Enter selects', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId('org-switcher-trigger'));
    // First arrow-down focuses the first option.
    await user.keyboard('{ArrowDown}');
    await user.keyboard('{ArrowDown}'); // move to second (Contoso)
    await user.keyboard('{Enter}');
    // Selection updates the AuthStore.
    expect(authStore.getSnapshot()?.activeOrgId).toBe('org_01HQBBBB000000000000000000');
  });

  it('Escape closes the open menu', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId('org-switcher-trigger'));
    await screen.findByRole('menu');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('clicking a non-active org updates the store and the trigger label', async () => {
    const user = userEvent.setup();
    renderSwitcher();
    await user.click(screen.getByTestId('org-switcher-trigger'));
    const menu = await screen.findByRole('menu');
    const contoso = within(menu).getByTestId('org-option-contoso');
    await user.click(contoso);
    expect(authStore.getSnapshot()?.activeOrgId).toBe('org_01HQBBBB000000000000000000');
    // After selection the trigger label now reflects the new active org.
    expect(screen.getByTestId('org-switcher-trigger')).toHaveTextContent(/contoso ltd/i);
  });
});
