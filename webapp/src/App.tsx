import { Github, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ThemeToggle } from '@/components/ThemeToggle';
import { t } from '@/i18n';

/**
 * Phase 0 webapp shell. Proves the build/runtime pipeline works end-to-end:
 * React 18 + TypeScript strict + Tailwind + shadcn primitives + light/dark
 * toggle + Lucide icons + keyboard-first focus rings. Actual product UI
 * (org switcher, project list, translation table) lands in Phase 1+.
 */
export default function App(): JSX.Element {
  return (
    <div className="flex min-h-full flex-col">
      <header className="flex items-center justify-between border-b border-border px-6 py-4">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Sparkles className="h-4 w-4" aria-hidden="true" />
          </span>
          <span className="text-lg font-semibold tracking-tight">{t('app.title')}</span>
        </div>
        <nav className="flex items-center gap-2" aria-label="Primary">
          <ThemeToggle />
          <Button variant="outline" size="sm" asChild>
            <a href="https://github.com/Pratiyush/translately" target="_blank" rel="noreferrer noopener">
              <Github className="h-4 w-4" aria-hidden="true" />
              {t('app.cta.github')}
            </a>
          </Button>
        </nav>
      </header>

      <main className="flex-1 px-6 py-10">
        <section className="mx-auto max-w-3xl space-y-8">
          <div className="space-y-3 text-center">
            <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">{t('app.title')}</h1>
            <p className="mx-auto max-w-2xl text-base text-muted-foreground sm:text-lg">{t('app.tagline')}</p>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-3">
            <Button asChild>
              <a href="https://pratiyush.github.io/translately/">{t('app.cta.docs')}</a>
            </Button>
            <Button variant="outline" asChild>
              <a href="https://github.com/Pratiyush/translately" target="_blank" rel="noreferrer noopener">
                <Github className="h-4 w-4" aria-hidden="true" />
                {t('app.cta.github')}
              </a>
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <dl className="rounded-lg border border-border bg-card p-5">
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t('app.phase.label')}
              </dt>
              <dd className="mt-1 text-base font-semibold text-foreground">{t('app.phase.current')}</dd>
            </dl>
            <dl className="rounded-lg border border-border bg-card p-5">
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t('app.status.label')}
              </dt>
              <dd className="mt-1 text-base font-semibold text-foreground">{t('app.status.prealpha')}</dd>
            </dl>
          </div>

          <article className="rounded-lg border border-border bg-accent/50 p-5">
            <h2 className="text-sm font-semibold text-foreground">{t('meta.mvp.title')}</h2>
            <p className="mt-2 text-sm text-muted-foreground">{t('meta.mvp.body')}</p>
          </article>
        </section>
      </main>

      <footer className="border-t border-border px-6 py-4 text-center text-xs text-muted-foreground">
        MIT · Made with care by Pratiyush
      </footer>
    </div>
  );
}
