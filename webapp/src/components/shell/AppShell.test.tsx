import { render, screen } from '@testing-library/react';
import * as React from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppShell } from './AppShell';
import { DEV_USER } from '@/lib/auth/mockAuth';
import { authStore } from '@/lib/auth/AuthStore';
import { ThemeProvider } from '@/theme/ThemeProvider';

function renderShellWithChild(child: React.ReactNode, initialPath = '/') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<div data-testid="route-child">{child}</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe('AppShell', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.setUser(DEV_USER);
  });

  it('renders a single banner landmark', () => {
    renderShellWithChild('hello');
    const banners = screen.getAllByRole('banner');
    expect(banners).toHaveLength(1);
  });

  it('renders a single main landmark with tabIndex -1 for skip-link support', () => {
    renderShellWithChild('hello');
    const main = screen.getByRole('main');
    expect(main).toBeInTheDocument();
    expect(main).toHaveAttribute('tabindex', '-1');
  });

  it('passes children through the <Outlet/>', () => {
    renderShellWithChild('child-rendered');
    expect(screen.getByTestId('route-child')).toHaveTextContent('child-rendered');
  });
});
