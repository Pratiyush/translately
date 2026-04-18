import { LayoutDashboard } from 'lucide-react';
import { t } from '@/i18n';

/**
 * Default landing page inside the shell. Intentionally sparse — Phase 1+
 * tasks populate it with real content (recent projects, pending tasks, etc).
 */
export function DashboardRoute() {
  return (
    <section className="space-y-4" data-testid="route-dashboard">
      <div className="flex items-center gap-2 text-muted-foreground">
        <LayoutDashboard className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.dashboard')}</span>
      </div>
      <h1 className="text-3xl font-bold tracking-tight">{t('route.dashboard.title')}</h1>
      <p className="max-w-2xl text-base text-muted-foreground">{t('route.dashboard.body')}</p>
    </section>
  );
}
