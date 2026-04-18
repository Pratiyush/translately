import { LogIn } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { seedDevUser } from '@/lib/auth/mockAuth';
import { t } from '@/i18n';

/**
 * Sign-in placeholder. The real credential flow ships with T117; this page
 * exists so the shell has somewhere to redirect unauthenticated visitors.
 * In dev bundles we also expose a "seed dev user" button so reviewers can
 * click into the shell without running a backend.
 */
export function SignInRoute() {
  const navigate = useNavigate();

  const handleSeed = () => {
    seedDevUser();
    navigate('/');
  };

  return (
    <section
      className="mx-auto flex min-h-[40vh] max-w-md flex-col items-center justify-center gap-5 text-center"
      data-testid="route-sign-in"
    >
      <LogIn className="h-10 w-10 text-muted-foreground" aria-hidden="true" />
      <h1 className="text-3xl font-bold tracking-tight">{t('route.sign-in.title')}</h1>
      <p className="text-base text-muted-foreground">{t('route.sign-in.body')}</p>
      {import.meta.env.DEV ? (
        <Button type="button" onClick={handleSeed} data-testid="seed-dev-user">
          {t('route.sign-in.seed')}
        </Button>
      ) : null}
    </section>
  );
}
