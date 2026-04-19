import * as React from 'react';
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
import { useImportJson, type ConflictMode, type ImportResult } from '@/lib/api/imports';
import { t } from '@/i18n';

/**
 * T304 — i18next JSON import wizard. Four-step flow:
 *
 *   1. Upload — user pastes JSON or drops a file; live parse + shape detect.
 *   2. Configure — language tag + namespace + conflict mode.
 *   3. Run — POST /imports/json, show counts.
 *   4. Review — per-row errors (ICU, namespace, etc.).
 *
 * The server runs the entire import inside a single transaction, so the
 * wizard's "preview" step is essentially the configure panel. A full
 * preview (diff before commit) lands with T303 / Phase 4 async jobs.
 */
export function ImportWizard({
  open,
  onOpenChange,
  orgSlug,
  projectSlug,
  defaultNamespaceSlug,
  defaultLanguageTag,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgSlug: string;
  projectSlug: string;
  defaultNamespaceSlug?: string;
  defaultLanguageTag?: string;
}) {
  const [payload, setPayload] = React.useState('');
  const [languageTag, setLanguageTag] = React.useState(defaultLanguageTag ?? 'en');
  const [namespaceSlug, setNamespaceSlug] = React.useState(defaultNamespaceSlug ?? 'default');
  const [mode, setMode] = React.useState<ConflictMode>('MERGE');
  const [result, setResult] = React.useState<ImportResult | null>(null);
  const fileRef = React.useRef<HTMLInputElement>(null);

  const mutation = useImportJson({
    onSuccess: (r) => setResult(r),
  });

  const reset = React.useCallback(() => {
    setPayload('');
    setResult(null);
    mutation.reset();
  }, [mutation]);

  const onSubmit = React.useCallback(
    (event: React.FormEvent) => {
      event.preventDefault();
      if (!payload.trim()) return;
      setResult(null);
      mutation.mutate({
        orgSlug,
        projectSlug,
        languageTag,
        namespaceSlug: namespaceSlug || undefined,
        mode,
        body: payload,
      });
    },
    [mutation, orgSlug, projectSlug, languageTag, namespaceSlug, mode, payload],
  );

  const onFile = React.useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    setPayload(text);
  }, []);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) reset();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('route.imports.title')}</DialogTitle>
          <DialogDescription>{t('route.imports.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="import-form">
          <div className="space-y-1.5">
            <Label htmlFor="import-language">{t('route.imports.languageTag')}</Label>
            <Input
              id="import-language"
              autoFocus
              autoComplete="off"
              value={languageTag}
              onChange={(e) => setLanguageTag(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">{t('route.imports.languageHint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="import-namespace">{t('route.imports.namespace')}</Label>
            <Input
              id="import-namespace"
              autoComplete="off"
              value={namespaceSlug}
              onChange={(e) => setNamespaceSlug(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">{t('route.imports.namespaceHint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="import-mode">{t('route.imports.mode')}</Label>
            <select
              id="import-mode"
              value={mode}
              onChange={(e) => setMode(e.target.value as ConflictMode)}
              data-testid="import-mode"
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="MERGE">{t('route.imports.mode.merge')}</option>
              <option value="KEEP">{t('route.imports.mode.keep')}</option>
              <option value="OVERWRITE">{t('route.imports.mode.overwrite')}</option>
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="import-payload">{t('route.imports.payload')}</Label>
            <input
              type="file"
              accept=".json,application/json"
              ref={fileRef}
              onChange={onFile}
              className="text-xs"
              data-testid="import-file"
            />
            <textarea
              id="import-payload"
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              placeholder='{"nav.signIn":"Sign in"}'
              rows={8}
              className="block w-full resize-y rounded-md border border-input bg-background p-2 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-ring"
              data-testid="import-payload"
            />
          </div>
          {mutation.isError ? (
            <p
              className="rounded-md border border-destructive bg-destructive/5 p-2 text-xs text-destructive"
              role="alert"
            >
              {formatApiError(mutation.error)}
            </p>
          ) : null}
          {result ? <ImportResultBlock result={result} /> : null}
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              {t('route.imports.close')}
            </Button>
            <Button type="submit" disabled={mutation.isPending || payload.trim().length === 0}>
              {mutation.isPending ? t('route.imports.running') : t('route.imports.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ImportResultBlock({ result }: { result: ImportResult }) {
  return (
    <div
      className="rounded-md border border-border bg-muted/50 p-3 text-xs"
      role="status"
      aria-live="polite"
      data-testid="import-result"
    >
      <p className="font-semibold">
        {t('route.imports.result', {
          total: result.total,
          created: result.created,
          updated: result.updated,
          skipped: result.skipped,
          failed: result.failed,
        })}
      </p>
      {result.errors.length > 0 ? (
        <ul className="mt-2 space-y-1">
          {result.errors.map((err) => (
            <li key={err.keyName + err.code} className="font-mono">
              <span className="text-destructive">[{err.code}]</span> {err.keyName} — {err.message}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
