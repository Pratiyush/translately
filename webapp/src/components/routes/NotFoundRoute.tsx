import { AlertTriangle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { t } from '@/i18n';

/**
 * Generic 404 target. Linked to from the router's catch-all route.
 */
export function NotFoundRoute() {
  return (
    <section
      className="flex min-h-[40vh] flex-col items-center justify-center gap-4 text-center"
      data-testid="route-not-found"
    >
      <AlertTriangle className="h-10 w-10 text-muted-foreground" aria-hidden="true" />
      <h1 className="text-3xl font-bold tracking-tight">{t('route.not-found.title')}</h1>
      <p className="max-w-md text-base text-muted-foreground">{t('route.not-found.body')}</p>
      <Button asChild>
        <Link to="/">{t('route.not-found.back')}</Link>
      </Button>
    </section>
  );
}
