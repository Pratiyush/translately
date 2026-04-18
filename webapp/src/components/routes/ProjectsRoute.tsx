import { FolderKanban } from 'lucide-react';
import { t } from '@/i18n';

/**
 * Projects placeholder route. The real project listing lands in T203+.
 */
export function ProjectsRoute() {
  return (
    <section className="space-y-4" data-testid="route-projects">
      <div className="flex items-center gap-2 text-muted-foreground">
        <FolderKanban className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.projects')}</span>
      </div>
      <h1 className="text-3xl font-bold tracking-tight">{t('route.projects.title')}</h1>
      <p className="max-w-2xl text-base text-muted-foreground">{t('route.projects.body')}</p>
    </section>
  );
}
