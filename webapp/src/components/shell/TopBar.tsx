import { Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NavLinks } from './NavLinks';
import { OrgSwitcher } from './OrgSwitcher';
import { UserMenu } from './UserMenu';
import { ThemeToggle } from '@/components/ThemeToggle';
import { t } from '@/i18n';

/**
 * The app's one-and-only TopBar. Three regions:
 *   - Left:   brand + org switcher.
 *   - Center: primary navigation links.
 *   - Right:  theme toggle + user menu.
 *
 * The `<header>` element is the single `banner` landmark. The nav inside is
 * labelled "Primary" so assistive tech distinguishes it from any secondary
 * nav we add later (e.g., project sidebar).
 */
export function TopBar() {
  return (
    <header className="flex h-16 items-center justify-between border-b border-border bg-background px-4 sm:px-6">
      <div className="flex items-center gap-3">
        <Link
          to="/"
          className="inline-flex items-center gap-2 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          aria-label={t('brand.logo.label')}
        >
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Sparkles className="h-4 w-4" aria-hidden="true" />
          </span>
          <span className="text-lg font-semibold tracking-tight">{t('app.title')}</span>
        </Link>
        <span className="h-5 w-px bg-border" aria-hidden="true" />
        <OrgSwitcher />
      </div>

      <nav aria-label={t('nav.primary')} className="hidden md:block">
        <NavLinks />
      </nav>

      <div className="flex items-center gap-2">
        <ThemeToggle />
        <UserMenu />
      </div>
    </header>
  );
}
