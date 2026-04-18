import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { UserMenu } from './UserMenu';
import { DEV_USER } from '@/lib/auth/mockAuth';
import { authStore } from '@/lib/auth/AuthStore';
import { ThemeProvider } from '@/theme/ThemeProvider';
import en from '@/i18n/en.json';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="current-path">{location.pathname}</span>;
}

function renderMenu() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route
            path="/*"
            element={
              <>
                <UserMenu />
                <LocationProbe />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe('UserMenu', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.setUser(DEV_USER);
  });

  it('does not render when the user is signed out', () => {
    authStore.signOut();
    renderMenu();
    expect(screen.queryByTestId('user-menu-trigger')).not.toBeInTheDocument();
  });

  it('renders an accessible avatar trigger', () => {
    renderMenu();
    const trigger = screen.getByTestId('user-menu-trigger');
    expect(trigger).toHaveAttribute('aria-label', en['user.menu.open']);
  });

  it('shows the user full name + email inside the menu', async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId('user-menu-trigger'));
    const menu = await screen.findByRole('menu');
    expect(within(menu).getByText(DEV_USER.fullName)).toBeInTheDocument();
    expect(within(menu).getByText(DEV_USER.email)).toBeInTheDocument();
  });

  it('shows Profile, Settings, and Sign out items', async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId('user-menu-trigger'));
    const menu = await screen.findByRole('menu');
    expect(within(menu).getByText(en['user.menu.profile'])).toBeInTheDocument();
    expect(within(menu).getByText(en['user.menu.settings'])).toBeInTheDocument();
    expect(within(menu).getByText(en['user.menu.sign-out'])).toBeInTheDocument();
  });

  it('Sign out clears the AuthStore and navigates to /signin', async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId('user-menu-trigger'));
    await user.click(await screen.findByTestId('user-menu-sign-out'));
    expect(authStore.getSnapshot()).toBeNull();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/signin');
  });

  it('Escape closes the menu', async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByTestId('user-menu-trigger'));
    await screen.findByRole('menu');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('Tab focuses the trigger before menu is open', async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.tab();
    const trigger = screen.getByTestId('user-menu-trigger');
    expect(trigger).toHaveFocus();
  });
});
