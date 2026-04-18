import { FolderKanban } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { formatApiError } from '@/lib/api/errors';
import { useOrgProjects, type Project } from '@/lib/api/orgs';
import { useAuth } from '@/lib/auth/useAuth';
import { t } from '@/i18n';

/**
 * `/projects` — tenant-scoped project list (T119).
 *
 * Follows the active org selected in the header `OrgSwitcher`. When the
 * user has no active org (freshly signed up, zero memberships) we show
 * an explainer pointing at `/orgs`.
 *
 * Project creation lives on the `/orgs/:slug` Projects tab; this page
 * is an at-a-glance index of what the current tenant already has.
 */
export function ProjectsRoute() {
  const { activeOrg } = useAuth();
  return (
    <section className="space-y-6" data-testid="route-projects">
      <div className="flex items-center gap-2 text-muted-foreground">
        <FolderKanban className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.projects')}</span>
      </div>
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">{t('route.projects.title')}</h1>
        {activeOrg ? (
          <p className="max-w-2xl text-base text-muted-foreground">
            {t('route.projects.body.active', { org: activeOrg.name })}
          </p>
        ) : (
          <p className="max-w-2xl text-base text-muted-foreground">{t('route.projects.body')}</p>
        )}
      </div>
      {activeOrg ? <ActiveOrgProjects orgSlug={activeOrg.slug} /> : <NoOrgCta />}
    </section>
  );
}

function NoOrgCta() {
  return (
    <div className="rounded-lg border border-dashed border-border p-8 text-center">
      <h2 className="text-base font-semibold">{t('route.projects.noOrg.title')}</h2>
      <p className="mx-auto mt-1 max-w-md text-sm text-muted-foreground">{t('route.projects.noOrg.body')}</p>
      <Button asChild className="mt-4">
        <Link to="/orgs">{t('route.projects.noOrg.cta')}</Link>
      </Button>
    </div>
  );
}

function ActiveOrgProjects({ orgSlug }: { orgSlug: string }) {
  const query = useOrgProjects(orgSlug);
  if (query.isPending) {
    return <p className="text-sm text-muted-foreground">{t('route.projects.loading')}</p>;
  }
  if (query.isError) {
    return (
      <p className="text-sm text-destructive" role="alert">
        {formatApiError(query.error)}
      </p>
    );
  }
  const projects = query.data ?? [];
  if (projects.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center">
        <h2 className="text-base font-semibold">{t('route.projects.empty.title')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('route.projects.empty.body')}</p>
        <Button asChild className="mt-4">
          <Link to={`/orgs/${orgSlug}`}>{t('route.orgDetail.createProject')}</Link>
        </Button>
      </div>
    );
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2" data-testid="projects-list">
      {projects.map((p) => (
        <ProjectListItem key={p.id} project={p} />
      ))}
    </ul>
  );
}

function ProjectListItem({ project }: { project: Project }) {
  return (
    <li className="rounded-lg border border-border bg-card p-4" data-testid={`project-item-${project.slug}`}>
      <h3 className="truncate text-base font-semibold">{project.name}</h3>
      <p className="mt-1 truncate font-mono text-xs text-muted-foreground">/{project.slug}</p>
      {project.description ? (
        <p className="mt-2 text-sm text-muted-foreground line-clamp-2">{project.description}</p>
      ) : null}
    </li>
  );
}
