import { Sparkles } from 'lucide-react';
import type * as React from 'react';
import { Link } from 'react-router-dom';
import { t } from '@/i18n';

/**
 * Shared chrome around every auth page (sign-in, sign-up, verify email,
 * forgot / reset password). Renders outside the `AppShell` so the header
 * / sidebar never blink in for unauthenticated visitors.
 */
interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  footer?: React.ReactNode;
  children?: React.ReactNode;
  testId?: string;
}

export function AuthLayout({ title, subtitle, footer, children, testId }: AuthLayoutProps) {
  return (
    <main
      id="main-content"
      className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-8 sm:px-6"
      data-testid={testId}
    >
      <div className="w-full max-w-md space-y-6">
        <Link
          to="/"
          className="flex items-center justify-center gap-2 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          aria-label={t('brand.logo.label')}
        >
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Sparkles className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="text-xl font-semibold tracking-tight">{t('app.title')}</span>
        </Link>

        <div className="rounded-lg border border-border bg-card p-6 shadow-sm">
          <header className="space-y-1 pb-5">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
            {subtitle ? <p className="text-sm text-muted-foreground">{subtitle}</p> : null}
          </header>
          {children}
        </div>

        {footer ? <div className="text-center text-sm text-muted-foreground">{footer}</div> : null}
      </div>
    </main>
  );
}
