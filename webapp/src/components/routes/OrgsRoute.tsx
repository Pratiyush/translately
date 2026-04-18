import { zodResolver } from '@hookform/resolvers/zod';
import { Building2, Plus } from 'lucide-react';
import * as React from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
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
import { useCreateOrg, useOrgs, type Org, type OrgRole } from '@/lib/api/orgs';
import { t } from '@/i18n';

/**
 * /orgs — real listing of organizations the caller belongs to (T118).
 *
 * GET backs the list via TanStack Query. The Create dialog posts
 * `{ name, slug? }`; the 201 response is invalidated back into the
 * list + the OrgSwitcher state (which re-reads the user's orgs).
 *
 * Empty state nudges the user to create their first org; every row
 * is a link into the org-detail route so Projects / Members / Settings
 * land naturally.
 */
export function OrgsRoute() {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const list = useOrgs();

  return (
    <section className="space-y-6" data-testid="route-orgs">
      <div className="flex items-center gap-2 text-muted-foreground">
        <Building2 className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.orgs')}</span>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold tracking-tight">{t('route.orgs.title')}</h1>
          <p className="max-w-2xl text-base text-muted-foreground">{t('route.orgs.body')}</p>
        </div>
        <Button type="button" onClick={() => setDialogOpen(true)} data-testid="orgs-create-button">
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('route.orgs.create')}
        </Button>
      </div>

      <OrgsList query={list} />

      <CreateOrgDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </section>
  );
}

function OrgsList({ query }: { query: ReturnType<typeof useOrgs> }) {
  if (query.isPending) {
    return (
      <p className="text-sm text-muted-foreground" role="status">
        {t('route.orgs.loading')}
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
  const orgs = query.data ?? [];
  if (orgs.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center">
        <h2 className="text-base font-semibold">{t('route.orgs.empty.title')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('route.orgs.empty.body')}</p>
      </div>
    );
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2" data-testid="orgs-list">
      {orgs.map((org) => (
        <OrgListItem key={org.id} org={org} />
      ))}
    </ul>
  );
}

function OrgListItem({ org }: { org: Org }) {
  return (
    <li className="rounded-lg border border-border bg-card p-4 transition hover:border-ring">
      <Link
        to={`/orgs/${org.slug}`}
        className="block focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
        data-testid={`orgs-item-${org.slug}`}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate text-base font-semibold">{org.name}</h3>
            <p className="mt-1 truncate font-mono text-xs text-muted-foreground">/{org.slug}</p>
          </div>
          <RoleBadge role={org.callerRole} />
        </div>
      </Link>
    </li>
  );
}

function RoleBadge({ role }: { role: OrgRole }) {
  return (
    <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
      {t(`org.role.${role}`)}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Create dialog
// ---------------------------------------------------------------------------

const createSchema = z.object({
  name: z.string().min(1, { message: 'REQUIRED' }).max(128, { message: 'TOO_LONG' }),
  slug: z
    .string()
    .max(64, { message: 'TOO_LONG' })
    .regex(/^$|^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/i, { message: 'INVALID' })
    .optional(),
});
type CreateInput = z.infer<typeof createSchema>;

function CreateOrgDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const form = useForm<CreateInput>({
    resolver: zodResolver(createSchema),
    defaultValues: { name: '', slug: '' },
  });

  const mutation = useCreateOrg({
    onSuccess: () => {
      form.reset();
      onOpenChange(false);
    },
  });

  const onSubmit = form.handleSubmit((values) => {
    mutation.mutate({ name: values.name.trim(), slug: values.slug?.trim() || undefined });
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
          <DialogTitle>{t('route.orgs.dialog.title')}</DialogTitle>
          <DialogDescription>{t('route.orgs.dialog.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="orgs-create-form">
          <div className="space-y-1.5">
            <Label htmlFor="org-name">{t('route.orgs.dialog.name')}</Label>
            <Input
              id="org-name"
              autoFocus
              autoComplete="off"
              aria-invalid={Boolean(form.formState.errors.name) || undefined}
              {...form.register('name')}
            />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.name.message ?? 'REQUIRED'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="org-slug">{t('route.orgs.dialog.slug')}</Label>
            <Input
              id="org-slug"
              autoComplete="off"
              aria-invalid={Boolean(form.formState.errors.slug) || undefined}
              {...form.register('slug')}
            />
            <p className="text-xs text-muted-foreground">{t('route.orgs.dialog.slug.hint')}</p>
            {form.formState.errors.slug ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.slug.message ?? 'INVALID'}`)}
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
              {t('route.orgs.dialog.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending} data-testid="orgs-create-submit">
              {mutation.isPending ? t('route.orgs.dialog.submitting') : t('route.orgs.dialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
