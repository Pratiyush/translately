import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { describe, expect, it } from 'vitest';
import App from './App';
import en from './i18n/en.json';
import { ThemeProvider } from './theme/ThemeProvider';

function renderApp() {
  return render(
    <ThemeProvider>
      <App />
    </ThemeProvider>,
  );
}

describe('App shell — structure', () => {
  it('renders a single h1 with the app title', () => {
    renderApp();
    const h1 = screen.getByRole('heading', { level: 1 });
    expect(h1).toHaveTextContent(en['app.title']);
  });

  it('renders the tagline', () => {
    renderApp();
    expect(screen.getByText(en['app.tagline'])).toBeInTheDocument();
  });

  it('renders a header landmark with primary navigation', () => {
    renderApp();
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument();
  });

  it('renders a main landmark', () => {
    renderApp();
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('renders a footer landmark with licensing note', () => {
    renderApp();
    const footer = screen.getByRole('contentinfo');
    expect(footer).toHaveTextContent(/MIT/);
    expect(footer).toHaveTextContent(/Pratiyush/);
  });

  it('renders the docs CTA with the correct href', () => {
    renderApp();
    const docs = screen.getByRole('link', { name: new RegExp(en['app.cta.docs'], 'i') });
    expect(docs).toHaveAttribute('href', 'https://pratiyush.github.io/translately/');
  });

  it('renders GitHub CTAs pointing at the repo', () => {
    renderApp();
    const githubs = screen.getAllByRole('link', { name: new RegExp(en['app.cta.github'], 'i') });
    expect(githubs.length).toBeGreaterThanOrEqual(1);
    for (const g of githubs) {
      expect(g).toHaveAttribute('href', 'https://github.com/Pratiyush/translately');
    }
  });

  it('external links open in a new tab with noreferrer+noopener', () => {
    renderApp();
    const externalLinks = screen.getAllByRole('link').filter((a) => a.getAttribute('target') === '_blank');
    expect(externalLinks.length).toBeGreaterThan(0);
    for (const a of externalLinks) {
      const rel = a.getAttribute('rel') ?? '';
      expect(rel).toMatch(/noreferrer/);
      expect(rel).toMatch(/noopener/);
    }
  });

  it('renders the MVP summary card', () => {
    renderApp();
    const h2 = screen.getByRole('heading', { level: 2, name: en['meta.mvp.title'] });
    expect(h2).toBeInTheDocument();
    expect(screen.getByText(en['meta.mvp.body'])).toBeInTheDocument();
  });

  it('renders the Phase + Status metric cards', () => {
    renderApp();
    expect(screen.getByText(en['app.phase.current'])).toBeInTheDocument();
    expect(screen.getByText(en['app.status.prealpha'])).toBeInTheDocument();
  });
});

describe('App shell — interaction', () => {
  it('has a theme toggle inside the primary nav', () => {
    renderApp();
    const nav = screen.getByRole('navigation', { name: /primary/i });
    const toggle = within(nav).getByRole('button', { name: /theme/i });
    expect(toggle).toBeInTheDocument();
  });

  it('applies the .dark class after clicking the theme toggle once from light', async () => {
    const user = userEvent.setup();
    localStorage.setItem('translately.theme', 'light');
    renderApp();
    const nav = screen.getByRole('navigation', { name: /primary/i });
    const toggle = within(nav).getByRole('button', { name: /theme/i });
    await user.click(toggle);
    expect(document.documentElement).toHaveClass('dark');
  });
});

describe('App shell — accessibility', () => {
  it('has no axe violations in light mode', async () => {
    const { container } = renderApp();
    const results = await axe.run(container, { resultTypes: ['violations'] });
    expect(results.violations).toEqual([]);
  });

  it('has no axe violations in dark mode', async () => {
    localStorage.setItem('translately.theme', 'dark');
    const { container } = renderApp();
    const results = await axe.run(container, { resultTypes: ['violations'] });
    expect(results.violations).toEqual([]);
  });
});
