import { LayoutDashboard, FolderKanban, Building2 } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { t } from '@/i18n';

/**
 * Primary in-shell navigation. Phase 1 ships three top-level destinations —
 * Dashboard, Organizations, Projects — which Phase 1+ tasks flesh out. Every
 * link is a `<NavLink>` so React Router can mark the active one without us
 * reaching for the router hook.
 */

interface NavItem {
  to: string;
  end?: boolean;
  labelKey: 'nav.dashboard' | 'nav.orgs' | 'nav.projects';
  Icon: typeof LayoutDashboard;
}

const items: readonly NavItem[] = [
  { to: '/', end: true, labelKey: 'nav.dashboard', Icon: LayoutDashboard },
  { to: '/orgs', labelKey: 'nav.orgs', Icon: Building2 },
  { to: '/projects', labelKey: 'nav.projects', Icon: FolderKanban },
] as const;

export function NavLinks() {
  return (
    <ul className="flex items-center gap-1" data-testid="nav-links">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn(
                'inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
                isActive
                  ? 'bg-accent text-accent-foreground'
                  : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground',
              )
            }
          >
            <item.Icon className="h-4 w-4" aria-hidden="true" />
            <span>{t(item.labelKey)}</span>
          </NavLink>
        </li>
      ))}
    </ul>
  );
}
