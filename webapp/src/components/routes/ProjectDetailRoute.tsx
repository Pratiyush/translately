import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, FolderKanban, Plus, Trash2 } from 'lucide-react';
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
  useCreateKey,
  useCreateNamespace,
  useDeleteKey,
  useKey,
  useKeys,
  useNamespaces,
  useUpsertTranslation,
  type Key,
  type Namespace,
  type TranslationCell,
  type TranslationState,
} from '@/lib/api/keys';
import { useOrgProjects } from '@/lib/api/orgs';
import { t } from '@/i18n';

type Tab = 'keys' | 'namespaces' | 'settings';

/**
 * `/orgs/:orgSlug/projects/:projectSlug` — project detail with keys,
 * namespaces, and settings tabs. Closes #48 (translation table UX) and
 * #49 (key create/edit/delete) for MVP.
 *
 * MVP scope: sticky key column + per-cell autosave textarea + ICU error
 * surface. CodeMirror 6 ICU syntax highlighting and keyboard-grid
 * navigation are deferred to a post-v0.2.0 polish ticket — documented
 * in the PR body so reviewers have the whole picture.
 */
export function ProjectDetailRoute() {
  const { orgSlug, projectSlug } = useParams<{ orgSlug: string; projectSlug: string }>();
  const [activeTab, setActiveTab] = React.useState<Tab>('keys');

  if (!orgSlug || !projectSlug) {
    return (
      <section className="space-y-3" data-testid="route-project-detail-missing">
        <p className="text-sm text-muted-foreground">{t('route.projectDetail.notFound')}</p>
        <BackLink orgSlug={orgSlug} />
      </section>
    );
  }

  return (
    <section className="space-y-6" data-testid="route-project-detail">
      <BackLink orgSlug={orgSlug} />
      <ProjectHeader orgSlug={orgSlug} projectSlug={projectSlug} />
      <TabSwitcher active={activeTab} onChange={setActiveTab} />
      {activeTab === 'keys' ? <KeysPanel orgSlug={orgSlug} projectSlug={projectSlug} /> : null}
      {activeTab === 'namespaces' ? <NamespacesPanel orgSlug={orgSlug} projectSlug={projectSlug} /> : null}
      {activeTab === 'settings' ? <SettingsPanel /> : null}
    </section>
  );
}

function BackLink({ orgSlug }: { orgSlug: string | undefined }) {
  return (
    <Link
      to={orgSlug ? `/orgs/${orgSlug}` : '/orgs'}
      className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      <ArrowLeft className="h-4 w-4" aria-hidden="true" />
      {t('route.projectDetail.back')}
    </Link>
  );
}

function ProjectHeader({ orgSlug, projectSlug }: { orgSlug: string; projectSlug: string }) {
  // Reuse the existing org-scoped listing; MVP resolves the title by
  // matching `projectSlug` rather than hitting a separate GET endpoint.
  const projects = useOrgProjects(orgSlug);
  const project = projects.data?.find((p) => p.slug === projectSlug);
  return (
    <header className="space-y-1">
      <div className="flex items-center gap-2 text-muted-foreground">
        <FolderKanban className="h-5 w-5" aria-hidden="true" />
        <span className="text-xs font-medium uppercase tracking-wide">{t('nav.projects')}</span>
      </div>
      <h1 className="text-3xl font-bold tracking-tight">{project?.name ?? projectSlug}</h1>
      <p className="font-mono text-xs text-muted-foreground">
        /{orgSlug}/{projectSlug}
        {project ? (
          <span className="ml-2 text-muted-foreground">
            {t('route.projectDetail.baseLanguage', { tag: project.baseLanguageTag })}
          </span>
        ) : null}
      </p>
    </header>
  );
}

function TabSwitcher({ active, onChange }: { active: Tab; onChange: (tab: Tab) => void }) {
  const tabs: Array<{ id: Tab; label: string }> = [
    { id: 'keys', label: t('route.projectDetail.tabs.keys') },
    { id: 'namespaces', label: t('route.projectDetail.tabs.namespaces') },
    { id: 'settings', label: t('route.projectDetail.tabs.settings') },
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
            data-testid={`project-tab-${tab.id}`}
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
// Keys panel — the heart of PR-C2
// ---------------------------------------------------------------------------

function KeysPanel({ orgSlug, projectSlug }: { orgSlug: string; projectSlug: string }) {
  const [nsFilter, setNsFilter] = React.useState<string>('');
  const [createOpen, setCreateOpen] = React.useState(false);
  const namespaces = useNamespaces({ orgSlug, projectSlug });
  const keys = useKeys({ orgSlug, projectSlug, namespaceSlug: nsFilter || null });

  return (
    <div role="tabpanel" data-testid="project-panel-keys" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <NamespaceFilter value={nsFilter} onChange={setNsFilter} options={namespaces.data ?? []} />
        <Button type="button" onClick={() => setCreateOpen(true)} data-testid="keys-create-button">
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('route.projectDetail.keys.create')}
        </Button>
      </div>
      <KeysTable orgSlug={orgSlug} projectSlug={projectSlug} keys={keys} />
      <CreateKeyDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        orgSlug={orgSlug}
        projectSlug={projectSlug}
        namespaces={namespaces.data ?? []}
      />
    </div>
  );
}

function NamespaceFilter({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (slug: string) => void;
  options: Namespace[];
}) {
  return (
    <div className="flex items-center gap-2">
      <label className="text-xs font-medium text-muted-foreground" htmlFor="namespace-filter">
        {t('route.projectDetail.keys.filterLabel')}
      </label>
      <select
        id="namespace-filter"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid="namespace-filter"
        className="h-9 rounded-md border border-input bg-background px-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
      >
        <option value="">{t('route.projectDetail.keys.filterAll')}</option>
        {options.map((ns) => (
          <option key={ns.id} value={ns.slug}>
            {ns.name} (/{ns.slug})
          </option>
        ))}
      </select>
    </div>
  );
}

function KeysTable({
  orgSlug,
  projectSlug,
  keys,
}: {
  orgSlug: string;
  projectSlug: string;
  keys: ReturnType<typeof useKeys>;
}) {
  if (keys.isPending) {
    return <p className="text-sm text-muted-foreground">{t('route.projectDetail.keys.loading')}</p>;
  }
  if (keys.isError) {
    return (
      <p className="text-sm text-destructive" role="alert">
        {formatApiError(keys.error)}
      </p>
    );
  }
  const rows = keys.data ?? [];
  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center">
        <h2 className="text-base font-semibold">{t('route.projectDetail.keys.empty.title')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('route.projectDetail.keys.empty.body')}</p>
      </div>
    );
  }
  return (
    <div className="relative overflow-x-auto rounded-lg border border-border" data-testid="keys-table-scroll">
      <table className="w-full text-sm" data-testid="keys-table">
        <thead className="border-b border-border bg-muted/50 text-left">
          <tr>
            <th scope="col" className="sticky left-0 z-10 bg-muted/50 p-3 font-semibold">
              {t('route.projectDetail.keys.column.key')}
            </th>
            <th scope="col" className="p-3 font-semibold">
              {t('route.projectDetail.keys.column.state')}
            </th>
            <th scope="col" className="p-3 font-semibold">
              {t('route.projectDetail.keys.column.translation')}
            </th>
            <th scope="col" className="p-3 font-semibold">
              <span className="sr-only">{t('route.projectDetail.keys.column.actions')}</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((key) => (
            <KeyRow key={key.id} orgSlug={orgSlug} projectSlug={projectSlug} keyRow={key} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function KeyRow({ orgSlug, projectSlug, keyRow }: { orgSlug: string; projectSlug: string; keyRow: Key }) {
  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const details = useKey({ orgSlug, projectSlug, keyId: keyRow.id });
  const remove = useDeleteKey({ onSuccess: () => setDeleteOpen(false) });
  const base = details.data?.translations[0];
  return (
    <tr data-testid={`key-row-${keyRow.keyName}`}>
      <th
        scope="row"
        className="sticky left-0 z-10 min-w-[240px] max-w-[320px] truncate bg-background p-3 text-left font-mono text-xs"
      >
        <span className="block truncate">{keyRow.keyName}</span>
        <span className="mt-1 block truncate text-[10px] text-muted-foreground">/{keyRow.namespaceSlug}</span>
      </th>
      <td className="p-3">
        <KeyStateBadge state={keyRow.state} />
      </td>
      <td className="min-w-[320px] p-3">
        {details.isPending ? (
          <span className="text-xs text-muted-foreground">{t('route.projectDetail.keys.loadingCell')}</span>
        ) : base ? (
          <TranslationEditorCell orgSlug={orgSlug} projectSlug={projectSlug} keyId={keyRow.id} cell={base} />
        ) : (
          <EmptyTranslationCta
            orgSlug={orgSlug}
            projectSlug={projectSlug}
            keyId={keyRow.id}
            languageTag={inferBaseLanguage(details.data?.translations ?? [])}
          />
        )}
      </td>
      <td className="p-3 text-right">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t('route.projectDetail.keys.delete')}
          onClick={() => setDeleteOpen(true)}
          data-testid={`key-delete-${keyRow.keyName}`}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
        <DeleteKeyDialog
          open={deleteOpen}
          onOpenChange={setDeleteOpen}
          onConfirm={() => remove.mutate({ orgSlug, projectSlug, keyId: keyRow.id })}
          isSubmitting={remove.isPending}
          error={remove.error}
          keyName={keyRow.keyName}
        />
      </td>
    </tr>
  );
}

function KeyStateBadge({ state }: { state: Key['state'] }) {
  const color =
    state === 'DONE'
      ? 'bg-green-500/10 text-green-700 dark:text-green-400 border-green-500/30'
      : state === 'REVIEW'
        ? 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30'
        : state === 'ARCHIVED'
          ? 'bg-muted text-muted-foreground border-border'
          : state === 'TRANSLATING'
            ? 'bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/30'
            : 'bg-muted text-muted-foreground border-border';
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${color}`}
      data-testid={`key-state-${state}`}
    >
      {t(`key.state.${state}`)}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Translation editor cell — autosave textarea, surfaces ICU errors
// ---------------------------------------------------------------------------

/** Pick which translation cell we'd edit first. MVP shows base language. */
function inferBaseLanguage(cells: TranslationCell[]): string {
  // No cells yet: fall back to 'en'. Project detail header confirms the
  // real base tag; this is the safer default until the backend exposes a
  // dedicated "languages" resource (Phase 3).
  const first = cells[0];
  return first ? first.languageTag : 'en';
}

function TranslationEditorCell({
  orgSlug,
  projectSlug,
  keyId,
  cell,
}: {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
  cell: TranslationCell;
}) {
  const [value, setValue] = React.useState(cell.value);
  const [dirty, setDirty] = React.useState(false);
  const mutation = useUpsertTranslation();
  // Refs pin the current value + dirty flag so the blur handler always
  // reads the latest state — onBlur event handlers captured at mount
  // hold stale closures when React batches the change+blur pair.
  const valueRef = React.useRef(value);
  const dirtyRef = React.useRef(dirty);
  valueRef.current = value;
  dirtyRef.current = dirty;

  // Sync the editor from the server after invalidations (e.g. another
  // tab saved the same cell, or the caller switched keys).
  React.useEffect(() => {
    if (!dirtyRef.current) setValue(cell.value);
  }, [cell.value]);

  const commit = React.useCallback(() => {
    if (!dirtyRef.current) return;
    setDirty(false);
    mutation.mutate({
      orgSlug,
      projectSlug,
      keyId,
      languageTag: cell.languageTag,
      value: valueRef.current,
    });
  }, [mutation, orgSlug, projectSlug, keyId, cell.languageTag]);

  return (
    <div className="space-y-1">
      <label className="sr-only" htmlFor={`tl-${keyId}-${cell.languageTag}`}>
        {t('route.projectDetail.keys.editorLabel', { tag: cell.languageTag })}
      </label>
      <textarea
        id={`tl-${keyId}-${cell.languageTag}`}
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          setDirty(true);
        }}
        onBlur={commit}
        onKeyDown={(e) => {
          // Cmd/Ctrl-Enter saves without blurring; escape reverts.
          if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
            e.preventDefault();
            commit();
          } else if (e.key === 'Escape') {
            setValue(cell.value);
            setDirty(false);
          }
        }}
        rows={Math.min(Math.max(1, value.split('\n').length), 6)}
        className="block w-full resize-y rounded-md border border-input bg-background p-2 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-ring"
        data-testid={`translation-editor-${keyId}-${cell.languageTag}`}
      />
      <div className="flex items-center justify-between text-[10px] text-muted-foreground">
        <span>
          <TranslationStateBadge state={cell.state} /> · {cell.languageTag}
        </span>
        <span aria-live="polite">
          {mutation.isPending ? (
            t('route.projectDetail.keys.editor.saving')
          ) : mutation.isError ? (
            <span className="text-destructive">{formatApiError(mutation.error)}</span>
          ) : dirty ? (
            t('route.projectDetail.keys.editor.dirty')
          ) : (
            t('route.projectDetail.keys.editor.saved')
          )}
        </span>
      </div>
    </div>
  );
}

function TranslationStateBadge({ state }: { state: TranslationState }) {
  return (
    <span
      className="inline-flex items-center rounded px-1 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground"
      data-testid={`translation-state-${state}`}
    >
      {t(`translation.state.${state}`)}
    </span>
  );
}

function EmptyTranslationCta({
  orgSlug,
  projectSlug,
  keyId,
  languageTag,
}: {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
  languageTag: string;
}) {
  // The backend creates the cell lazily on first PUT; we render an empty
  // editor to offer the exact same autosave UX as a populated row.
  const stub: TranslationCell = {
    id: `stub-${keyId}-${languageTag}`,
    languageTag,
    value: '',
    state: 'EMPTY',
    updatedAt: new Date().toISOString(),
  };
  return <TranslationEditorCell orgSlug={orgSlug} projectSlug={projectSlug} keyId={keyId} cell={stub} />;
}

// ---------------------------------------------------------------------------
// Create-key dialog
// ---------------------------------------------------------------------------

const keyNameRegex = /^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$/;

const keySchema = z.object({
  keyName: z
    .string()
    .min(1, { message: 'REQUIRED' })
    .max(255, { message: 'TOO_LONG' })
    .regex(keyNameRegex, { message: 'INVALID' }),
  namespaceSlug: z.string().min(1, { message: 'REQUIRED' }),
  description: z.string().max(1024, { message: 'TOO_LONG' }).optional(),
});
type KeyInput = z.infer<typeof keySchema>;

function CreateKeyDialog({
  open,
  onOpenChange,
  orgSlug,
  projectSlug,
  namespaces,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgSlug: string;
  projectSlug: string;
  namespaces: Namespace[];
}) {
  const form = useForm<KeyInput>({
    resolver: zodResolver(keySchema),
    defaultValues: {
      keyName: '',
      namespaceSlug: namespaces[0]?.slug ?? 'default',
      description: '',
    },
  });
  React.useEffect(() => {
    if (open && namespaces[0] && !form.getValues('namespaceSlug')) {
      form.setValue('namespaceSlug', namespaces[0].slug);
    }
  }, [open, namespaces, form]);
  const mutation = useCreateKey({
    onSuccess: () => {
      form.reset();
      onOpenChange(false);
    },
  });
  const onSubmit = form.handleSubmit((values) =>
    mutation.mutate({
      orgSlug,
      projectSlug,
      keyName: values.keyName.trim(),
      namespaceSlug: values.namespaceSlug,
      description: values.description?.trim() || undefined,
    }),
  );

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
          <DialogTitle>{t('route.projectDetail.keys.dialog.title')}</DialogTitle>
          <DialogDescription>{t('route.projectDetail.keys.dialog.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="keys-create-form">
          <div className="space-y-1.5">
            <Label htmlFor="key-name">{t('route.projectDetail.keys.dialog.keyName')}</Label>
            <Input id="key-name" autoFocus autoComplete="off" {...form.register('keyName')} />
            <p className="text-xs text-muted-foreground">
              {t('route.projectDetail.keys.dialog.keyNameHint')}
            </p>
            {form.formState.errors.keyName ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.keyName.message ?? 'REQUIRED'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="key-namespace">{t('route.projectDetail.keys.dialog.namespace')}</Label>
            <select
              id="key-namespace"
              {...form.register('namespaceSlug')}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              {namespaces.length === 0 ? <option value="default">default</option> : null}
              {namespaces.map((ns) => (
                <option key={ns.id} value={ns.slug}>
                  {ns.name} (/{ns.slug})
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="key-description">{t('route.projectDetail.keys.dialog.descriptionField')}</Label>
            <Input id="key-description" autoComplete="off" {...form.register('description')} />
            <p className="text-xs text-muted-foreground">
              {t('route.projectDetail.keys.dialog.descriptionHint')}
            </p>
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
              {t('route.projectDetail.keys.dialog.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending} data-testid="keys-create-submit">
              {mutation.isPending
                ? t('route.projectDetail.keys.dialog.submitting')
                : t('route.projectDetail.keys.dialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function DeleteKeyDialog({
  open,
  onOpenChange,
  onConfirm,
  isSubmitting,
  error,
  keyName,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  isSubmitting: boolean;
  error: unknown;
  keyName: string;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('route.projectDetail.keys.deleteConfirm')}</DialogTitle>
          <DialogDescription>
            <span className="block font-mono text-xs">{keyName}</span>
            <span className="mt-2 block">{t('route.projectDetail.keys.deleteHint')}</span>
          </DialogDescription>
        </DialogHeader>
        {error ? (
          <p className="text-xs text-destructive" role="alert">
            {formatApiError(error)}
          </p>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            {t('route.projectDetail.keys.dialog.cancel')}
          </Button>
          <Button type="button" onClick={onConfirm} disabled={isSubmitting}>
            {t('route.projectDetail.keys.deleteSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Namespaces panel
// ---------------------------------------------------------------------------

const namespaceSchema = z.object({
  name: z.string().min(1, { message: 'REQUIRED' }).max(128, { message: 'TOO_LONG' }),
  slug: z
    .string()
    .max(64, { message: 'TOO_LONG' })
    .regex(/^$|^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/i, { message: 'INVALID' })
    .optional(),
  description: z.string().max(1024, { message: 'TOO_LONG' }).optional(),
});
type NamespaceInput = z.infer<typeof namespaceSchema>;

function NamespacesPanel({ orgSlug, projectSlug }: { orgSlug: string; projectSlug: string }) {
  const query = useNamespaces({ orgSlug, projectSlug });
  const [open, setOpen] = React.useState(false);
  return (
    <div role="tabpanel" data-testid="project-panel-namespaces" className="space-y-4">
      <div className="flex justify-end">
        <Button type="button" onClick={() => setOpen(true)} data-testid="namespaces-create-button">
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('route.projectDetail.namespaces.create')}
        </Button>
      </div>
      <NamespaceList query={query} />
      <CreateNamespaceDialog open={open} onOpenChange={setOpen} orgSlug={orgSlug} projectSlug={projectSlug} />
    </div>
  );
}

function NamespaceList({ query }: { query: ReturnType<typeof useNamespaces> }) {
  if (query.isPending) {
    return <p className="text-sm text-muted-foreground">{t('route.projectDetail.namespaces.loading')}</p>;
  }
  if (query.isError) {
    return (
      <p className="text-sm text-destructive" role="alert">
        {formatApiError(query.error)}
      </p>
    );
  }
  const items = query.data ?? [];
  if (items.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center">
        <h2 className="text-base font-semibold">{t('route.projectDetail.namespaces.empty.title')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('route.projectDetail.namespaces.empty.body')}</p>
      </div>
    );
  }
  return (
    <ul className="divide-y divide-border rounded-lg border border-border" data-testid="namespaces-list">
      {items.map((ns) => (
        <li key={ns.id} className="p-3">
          <p className="truncate text-sm font-semibold">{ns.name}</p>
          <p className="truncate font-mono text-xs text-muted-foreground">/{ns.slug}</p>
          {ns.description ? <p className="mt-1 text-xs text-muted-foreground">{ns.description}</p> : null}
        </li>
      ))}
    </ul>
  );
}

function CreateNamespaceDialog({
  open,
  onOpenChange,
  orgSlug,
  projectSlug,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgSlug: string;
  projectSlug: string;
}) {
  const form = useForm<NamespaceInput>({
    resolver: zodResolver(namespaceSchema),
    defaultValues: { name: '', slug: '', description: '' },
  });
  const mutation = useCreateNamespace({
    onSuccess: () => {
      form.reset();
      onOpenChange(false);
    },
  });
  const onSubmit = form.handleSubmit((values) =>
    mutation.mutate({
      orgSlug,
      projectSlug,
      name: values.name.trim(),
      slug: values.slug?.trim() || undefined,
      description: values.description?.trim() || undefined,
    }),
  );

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
          <DialogTitle>{t('route.projectDetail.namespaces.dialog.title')}</DialogTitle>
          <DialogDescription>{t('route.projectDetail.namespaces.dialog.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="namespaces-create-form">
          <div className="space-y-1.5">
            <Label htmlFor="ns-name">{t('route.projectDetail.namespaces.dialog.name')}</Label>
            <Input id="ns-name" autoFocus autoComplete="off" {...form.register('name')} />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.name.message ?? 'REQUIRED'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ns-slug">{t('route.projectDetail.namespaces.dialog.slug')}</Label>
            <Input id="ns-slug" autoComplete="off" {...form.register('slug')} />
            <p className="text-xs text-muted-foreground">
              {t('route.projectDetail.namespaces.dialog.slugHint')}
            </p>
            {form.formState.errors.slug ? (
              <p className="text-xs text-destructive" role="alert">
                {t(`auth.field.${form.formState.errors.slug.message ?? 'INVALID'}`)}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ns-description">
              {t('route.projectDetail.namespaces.dialog.descriptionField')}
            </Label>
            <Input id="ns-description" autoComplete="off" {...form.register('description')} />
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
              {t('route.projectDetail.keys.dialog.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending
                ? t('route.projectDetail.namespaces.dialog.submitting')
                : t('route.projectDetail.namespaces.dialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Settings panel (placeholder — real settings land with the project-settings resource)
// ---------------------------------------------------------------------------

function SettingsPanel() {
  return (
    <div role="tabpanel" data-testid="project-panel-settings" className="space-y-2">
      <p className="text-sm text-muted-foreground">{t('route.projectDetail.settings.placeholder')}</p>
    </div>
  );
}
