import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ThemeToggle } from './ThemeToggle';
import { ThemeProvider, useTheme } from '@/theme/ThemeProvider';

function Harness({ onRender }: { onRender: (ctx: ReturnType<typeof useTheme>) => void }) {
  const ctx = useTheme();
  onRender(ctx);
  return <ThemeToggle />;
}

describe('ThemeToggle', () => {
  it('renders a button with an accessible name', () => {
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );
    const btn = screen.getByRole('button', { name: /theme/i });
    expect(btn).toBeInTheDocument();
    expect(btn.getAttribute('aria-label')).toBeTruthy();
  });

  it('cycles light → dark → system → light on click', async () => {
    const user = userEvent.setup();
    localStorage.setItem('translately.theme', 'light');
    let latest: ReturnType<typeof useTheme> | null = null;
    render(
      <ThemeProvider>
        <Harness
          onRender={(ctx) => {
            latest = ctx;
          }}
        />
      </ThemeProvider>,
    );
    expect(latest!.theme).toBe('light');
    await user.click(screen.getByRole('button'));
    expect(latest!.theme).toBe('dark');
    await user.click(screen.getByRole('button'));
    expect(latest!.theme).toBe('system');
    await user.click(screen.getByRole('button'));
    expect(latest!.theme).toBe('light');
  });

  it('is keyboard-reachable via Tab + Enter', async () => {
    const user = userEvent.setup();
    localStorage.setItem('translately.theme', 'light');
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );
    await user.tab();
    expect(screen.getByRole('button')).toHaveFocus();
    await user.keyboard('{Enter}');
    // After Enter: theme advanced to dark -> html has .dark class
    expect(document.documentElement).toHaveClass('dark');
  });
});
