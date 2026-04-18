import { Building2, Check, ChevronDown, Plus } from 'lucide-react';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/lib/auth/useAuth';
import { t } from '@/i18n';
import { cn } from '@/lib/utils';
import type { OrganizationRole } from '@/lib/auth/AuthStore';

/**
 * OrgSwitcher — renders either the empty-state CTA (when the user belongs to
 * zero orgs) or a dropdown trigger whose label mirrors the active org.
 *
 * Active-org selection is stored in-memory via `AuthStore.setActiveOrg`. We
 * deliberately do not reflect the active org into the URL: the shell's
 * route structure is org-independent at this phase; Phase 3 (T306) introduces
 * `/{orgSlug}/...` scoped routes and will own the URL-state contract then.
 *
 * Radix DropdownMenu handles arrow-key navigation and Esc-to-close natively.
 * The trigger is a real `<button>` so focus rings and Enter/Space behave.
 */

function roleLabelKey(role: OrganizationRole) {
  return ({ OWNER: 'org.role.OWNER', ADMIN: 'org.role.ADMIN', MEMBER: 'org.role.MEMBER' } as const)[role];
}

export function OrgSwitcher() {
  const { user, activeOrg, setActiveOrg } = useAuth();
  const navigate = useNavigate();

  // Sort orgs alphabetically so the dropdown is stable regardless of seed
  // order. The active org is kept in its sorted slot; a check icon marks it.
  const sortedOrgs = React.useMemo(() => {
    const list = user?.orgs ?? [];
    return [...list].sort((a, b) => a.name.localeCompare(b.name));
  }, [user?.orgs]);

  if (!user || sortedOrgs.length === 0) {
    return (
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => navigate('/orgs')}
        className="text-muted-foreground"
      >
        <Plus className="h-4 w-4" aria-hidden="true" />
        {t('org.switcher.none')}
      </Button>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-label={t('org.switcher.open')}
          className="gap-2"
          data-testid="org-switcher-trigger"
        >
          <Building2 className="h-4 w-4" aria-hidden="true" />
          <span className="truncate max-w-[10rem]">{activeOrg?.name}</span>
          <ChevronDown className="h-4 w-4 opacity-70" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-[14rem]">
        <DropdownMenuLabel>{t('org.switcher.label')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {sortedOrgs.map((org) => {
          const isActive = org.id === activeOrg?.id;
          return (
            <DropdownMenuItem
              key={org.id}
              onSelect={() => setActiveOrg(org.id)}
              className="flex items-center justify-between gap-4"
              data-testid={`org-option-${org.slug}`}
            >
              <span className="flex items-center gap-2">
                <Check className={cn('h-4 w-4', isActive ? 'opacity-100' : 'opacity-0')} aria-hidden="true" />
                <span className="truncate">{org.name}</span>
              </span>
              <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                {t(roleLabelKey(org.role))}
              </span>
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
