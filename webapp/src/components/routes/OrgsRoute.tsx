import { Building2, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { t } from '@/i18n';

/**
 * Organizations placeholder route. Carries the primary CTA for users who
 * land here from the OrgSwitcher's empty-state button.
 */
export function OrgsRoute() {
  return (
    <section className="space-y-6" data-testid="route-orgs">
      <div className="flex items-center gap-2 text-muted-foreground">
        <Building2 className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.orgs')}</span>
      </div>
      <div className="space-y-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('route.orgs.title')}</h1>
        <p className="max-w-2xl text-base text-muted-foreground">{t('route.orgs.body')}</p>
      </div>
      <div>
        <Button type="button">
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('route.orgs.create')}
        </Button>
      </div>
    </section>
  );
}
