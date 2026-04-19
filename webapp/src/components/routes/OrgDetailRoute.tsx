import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Building2, Plus, Trash2 } from 'lucide-react';
import * as React from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatApiError } from '@/lib/api/errors';
import {
  useChangeMemberRole,
  useCreateProject,
  useOrg,
  useOrgMembers,
  useOrgProjects,
  useRemoveMember,
  useRenameOrg,
  type Member,
  type OrgRole,
  type Project,
} from '@/lib/api/orgs';
import { useAuth } from '@/lib/auth/useAuth';
import { t } from '@/i18n';

type Tab = 'projects' | 'members' | 'settings';

/**
 * `/orgs/:orgSlug` — tabs for Projects / Members / Settings (T118+T119).
 *
 * URL drives the active tab via the `?tab=` query param so direct-links
 * to a specific view work (e.g. sharing an invite email with the Members
 * tab preselected later).
 */
export function OrgDetailRoute() {
  const { orgSlug } = useParams<{ orgSlug: string }>();
  const [activeTab, setActiveTab] = React.useState<Tab>('projects');

  const orgQuery = useOrg(orgSlug ?? null);

  if (!orgSlug) {
    return (
      <section className="space-y-3" data-testid="route-org-detail-missing">
        <p className="text-sm text-muted-foreground">{t('route.orgDetail.notFound')}</p>
        <BackLink />
      </section>
    );
  }

  return (
    <section className="space-y-6" data-testid="route-org-detail">
      <BackLink />
      <header className="space-y-1">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Building2 className="h-5 w-5" aria-hidden="true" />
          <span className="text-xs font-medium uppercase tracking-wide">{t('nav.orgs')}</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight">{orgQuery.data?.name ?? orgSlug}</h1>
        <p className="font-mono text-xs text-muted-foreground">/{orgSlug}</p>
      </header>

      <TabSwitcher active={activeTab} onChange={setActiveTab} />

      {activeTab === 'projects' ? <ProjectsPanel orgSlug={orgSlug} /> : null}
      {activeTab === 'members' ? <MembersPanel orgSlug={orgSlug} /> : null}
      {activeTab === 'settings' ? (
        <SettingsPanel orgSlug={orgSlug} currentName={orgQuery.data?.name ?? ''} />
      ) : null}
    </section>
  );
}

function BackLink() {
  return (
    <Link
      to="/orgs"
      className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      <ArrowLeft className="h-4 w-4" aria-hidden="true" />
      {t('route.orgDetail.back')}
    </Link>
  );
}

function TabSwitcher({ active, onChange }: { active: Tab; onChange: (tab: Tab) => void }) {
  const tabs: Array<{ id: Tab; label: string }> = [
    { id: 'projects', label: t('route.orgDetail.tabs.projects') },
    { id: 'members', label: t('route.orgDetail.tabs.members') },
    { id: 'settings', label: t('route.orgDetail.tabs.settings') },
  ];
  return (
    <div role="tablist" className="flex gap-1 border-b border-border">
      {tabs.map((tab) => {
        const selected = active === tab.id;
        return (
          <button
            type="button"
            key={tab.id}
            role="tab"
            aria-selected={selected}
            data-testid={`org-tab-${tab.id}`}
            onClick={() => onChange(tab.id)}
            className={
              'relative px-4 py-2 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2' +
              (selected
                ? ' border-b-2 border-foreground text-foreground'
                : ' text-muted-foreground hover:text-foreground')
            }
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Projects panel
// ---------------------------------------------------------------------------

function ProjectsPanel({ orgSlug }: { orgSlug: string }) {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const query = useOrgProjects(orgSlug);
  return (
    <div className="space-y-4" role="tabpanel" data-testid="org-panel-projects">
      <div className="flex justify-end">
        <Button type="button" onClick={() => setDialogOpen(true)} data-testid="projects-create-button">
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('route.orgDetail.createProject')}
        </Button>
      </div>
      <ProjectList query={query} />
      <CreateProjectDialog open={dialogOpen} onOpenChange={setDialogOpen} orgSlug={orgSlug} />
    </div>
  );
}

function ProjectList({ query }: { query: ReturnType<typeof useOrgProjects> }) {
  if (query.isPending) {
    return <p className="text-sm text-muted-foreground">{t('route.orgDetail.projects.loading')}</p>;
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
        <h2 className="text-base font-semibold">{t('route.orgDetail.projects.empty.title')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('route.orgDetail.projects.empty.body')}</p>
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
  const { orgSlug } = useParams<{ orgSlug: string }>();
  return (
    <li className="rounded-lg border border-border bg-card" data-testid={`project-item-${project.slug}`}>
      <Link
        to={orgSlug ? `/orgs/${orgSlug}/projects/${project.slug}` : '#'}
        className="block rounded-lg p-4 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 hover:bg-accent"
      >
        <h3 className="truncate text-base font-semibold">{project.name}</h3>
        <p className="mt-1 truncate font-mono text-xs text-muted-foreground">/{project.slug}</p>
        {project.description ? (
          <p className="mt-2 text-sm text-muted-foreground line-clamp-2">{project.description}</p>
        ) : null}
        <p className="mt-2 text-xs text-muted-foreground">
          {t('route.orgDetail.projects.baseLanguage', { tag: project.baseLanguageTag })}
        </p>
      </Link>
    </li>
  );
}

const projectSchema = z.object({
  name: z.string().min(1, { message: 'REQUIRED' }).max(128, { message: 'TOO_LONG' }),
  slug: z
    .string()
    .max(64, { message: 'TOO_LONG' })
    .regex(/^$|^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/i, { message: 'INVALID' })
    .optional(),
  description: z.string().max(1024, { message: 'TOO_LONG' }).optional(),
  baseLanguageTag: z
    .string()
    .max(32, { message: 'TOO_LONG' })
    .regex(/^$|^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/, { message: 'INVALID' })
    .optional(),
});
type ProjectInput = z.infer<typeof projectSchema>;

function CreateProjectDialog({
  open,
  onOpenChange,
  orgSlug,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgSlug: string;
}) {
  const form = useForm<ProjectInput>({
    resolver: zodResolver(projectSchema),
    defaultValues: { name: '', slug: '', description: '', baseLanguageTag: 'en' },
  });
  const mutation = useCreateProject({
    onSuccess: () => {
      form.reset();
      onOpenChange(false);
    },
  });
  const onSubmit = form.handleSubmit((values) => {
    mutation.mutate({
      orgSlug,
      name: values.name.trim(),
      slug: values.slug?.trim() || undefined,
      description: values.description?.trim() || undefined,
      baseLanguageTag: values.baseLanguageTag?.trim() || undefined,
    });
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) {
          form.reset();
          mutation.reset();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('route.projects.dialog.title')}</DialogTitle>
          <DialogDescription>{t('route.projects.dialog.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="projects-create-form">
          <div className="space-y-1.5">
            <Label htmlFor="project-name">{t('route.projects.dialog.name')}</Label>
            <Input id="project-name" autoFocus autoComplete="off" {...form.register('name')} />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.name.message ?? 'REQUIRED'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="project-slug">{t('route.projects.dialog.slug')}</Label>
            <Input id="project-slug" autoComplete="off" {...form.register('slug')} />
            <p className="text-xs text-muted-foreground">{t('route.projects.dialog.slug.hint')}</p>
            {form.formState.errors.slug ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.slug.message ?? 'INVALID'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="project-description">{t('route.projects.dialog.descriptionField')}</Label>
            <Input id="project-description" autoComplete="off" {...form.register('description')} />
            <p className="text-xs text-muted-foreground">{t('route.projects.dialog.descriptionHint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="project-base-language">{t('route.projects.dialog.baseLanguage')}</Label>
            <Input id="project-base-language" autoComplete="off" {...form.register('baseLanguageTag')} />
            <p className="text-xs text-muted-foreground">{t('route.projects.dialog.baseLanguageHint')}</p>
            {form.formState.errors.baseLanguageTag ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.baseLanguageTag.message ?? 'INVALID'}`)}
              </p>
            ) : null}
          </div>
          {mutation.isError ? (
            <p
              className="rounded-md border border-destructive bg-destructive/5 p-2 text-xs text-destructive"
              role="alert"
            >
              {formatApiError(mutation.error)}
            </p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              {t('route.projects.dialog.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('route.projects.dialog.submitting') : t('route.projects.dialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Members panel
// ---------------------------------------------------------------------------

function MembersPanel({ orgSlug }: { orgSlug: string }) {
  const { user } = useAuth();
  const query = useOrgMembers(orgSlug);
  if (query.isPending) {
    return (
      <p className="text-sm text-muted-foreground" role="tabpanel">
        {t('route.orgDetail.members.loading')}
      </p>
    );
  }
  if (query.isError) {
    return (
      <p className="text-sm text-destructive" role="alert">
        {formatApiError(query.error)}
      </p>
    );
  }
  const members = query.data ?? [];
  return (
    <div role="tabpanel" data-testid="org-panel-members" className="space-y-2">
      <ul className="divide-y divide-border rounded-lg border border-border" data-testid="members-list">
        {members.map((m) => (
          <MemberRow key={m.userId} member={m} orgSlug={orgSlug} isSelf={user?.id === m.userId} />
        ))}
      </ul>
    </div>
  );
}

function MemberRow({ member, orgSlug, isSelf }: { member: Member; orgSlug: string; isSelf: boolean }) {
  const [removeOpen, setRemoveOpen] = React.useState(false);
  const change = useChangeMemberRole();
  const remove = useRemoveMember({ onSuccess: () => setRemoveOpen(false) });
  const currentRole = change.variables?.role ?? member.role;
  const error = change.error ?? remove.error;

  return (
    <li
      className="flex flex-col gap-2 p-3 sm:flex-row sm:items-center sm:justify-between"
      data-testid={`member-row-${member.userId}`}
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">
          {member.fullName || member.email}{' '}
          {isSelf ? (
            <span className="text-xs text-muted-foreground">{t('route.orgDetail.members.you')}</span>
          ) : null}
        </p>
        <p className="truncate text-xs text-muted-foreground">{member.email}</p>
        {member.joinedAt == null ? (
          <span className="mt-1 inline-block rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
            {t('route.orgDetail.members.pending')}
          </span>
        ) : null}
      </div>
      <div className="flex items-center gap-2">
        <label className="sr-only" htmlFor={`role-${member.userId}`}>
          {t('route.orgDetail.members.roleLabel', { email: member.email })}
        </label>
        <select
          id={`role-${member.userId}`}
          value={currentRole}
          disabled={change.isPending || remove.isPending}
          onChange={(e) => change.mutate({ orgSlug, userId: member.userId, role: e.target.value as OrgRole })}
          className="h-9 rounded-md border border-input bg-background px-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
          data-testid={`member-role-${member.userId}`}
        >
          <option value="OWNER">{t('org.role.OWNER')}</option>
          <option value="ADMIN">{t('org.role.ADMIN')}</option>
          <option value="MEMBER">{t('org.role.MEMBER')}</option>
        </select>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setRemoveOpen(true)}
          aria-label={t('route.orgDetail.members.remove')}
          disabled={remove.isPending}
          data-testid={`member-remove-${member.userId}`}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      {error ? (
        <p className="sm:ml-auto sm:basis-full text-xs text-destructive" role="alert">
          {formatApiError(error)}
        </p>
      ) : null}
      <RemoveMemberDialog
        open={removeOpen}
        onOpenChange={setRemoveOpen}
        onConfirm={() => remove.mutate({ orgSlug, userId: member.userId })}
        isSubmitting={remove.isPending}
        email={member.email}
      />
    </li>
  );
}

function RemoveMemberDialog({
  open,
  onOpenChange,
  onConfirm,
  isSubmitting,
  email,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  isSubmitting: boolean;
  email: string;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('route.orgDetail.members.removeConfirm')}</DialogTitle>
          <DialogDescription>
            <span className="block">{email}</span>
            <span className="mt-2 block">{t('route.orgDetail.members.removeHint')}</span>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            {t('route.orgs.dialog.cancel')}
          </Button>
          <Button type="button" onClick={onConfirm} disabled={isSubmitting}>
            {t('route.orgDetail.members.removeSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Settings panel
// ---------------------------------------------------------------------------

const renameSchema = z.object({
  name: z.string().min(1, { message: 'REQUIRED' }).max(128, { message: 'TOO_LONG' }),
});
type RenameInput = z.infer<typeof renameSchema>;

function SettingsPanel({ orgSlug, currentName }: { orgSlug: string; currentName: string }) {
  const form = useForm<RenameInput>({
    resolver: zodResolver(renameSchema),
    defaultValues: { name: currentName },
  });
  React.useEffect(() => {
    if (currentName && !form.getValues('name')) form.reset({ name: currentName });
  }, [currentName, form]);
  const mutation = useRenameOrg();
  const onSubmit = form.handleSubmit((values) =>
    mutation.mutate({ slug: orgSlug, name: values.name.trim() }),
  );
  return (
    <form role="tabpanel" data-testid="org-panel-settings" className="max-w-xl space-y-4" onSubmit={onSubmit}>
      <div className="space-y-1.5">
        <Label htmlFor="org-rename">{t('route.orgDetail.settings.rename')}</Label>
        <Input id="org-rename" {...form.register('name')} />
        {form.formState.errors.name ? (
          <p className="text-xs text-destructive" role="alert">
            {t(`auth.field.${form.formState.errors.name.message ?? 'REQUIRED'}`)}
          </p>
        ) : null}
      </div>
      {mutation.isError ? (
        <p
          className="rounded-md border border-destructive bg-destructive/5 p-2 text-xs text-destructive"
          role="alert"
        >
          {formatApiError(mutation.error)}
        </p>
      ) : null}
      <div>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending
            ? t('route.orgDetail.settings.renameSaving')
            : t('route.orgDetail.settings.renameSubmit')}
        </Button>
      </div>
    </form>
  );
}
