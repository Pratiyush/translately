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
import { downloadExport, type ExportShape, type MinState } from '@/lib/api/imports';
import { authStore } from '@/lib/auth/AuthStore';
import { t } from '@/i18n';

/**
 * T305 — i18next JSON export modal. One language, one shape, optional
 * filters. Hits the GET endpoint, reads the `Content-Disposition`
 * filename, triggers a browser download.
 */
export function ExportModal({
  open,
  onOpenChange,
  orgSlug,
  projectSlug,
  defaultLanguageTag,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgSlug: string;
  projectSlug: string;
  defaultLanguageTag?: string;
}) {
  const [languageTag, setLanguageTag] = React.useState(defaultLanguageTag ?? 'en');
  const [namespaceSlug, setNamespaceSlug] = React.useState('');
  const [shape, setShape] = React.useState<ExportShape>('FLAT');
  const [minState, setMinState] = React.useState<MinState | ''>('');
  const [tagsInput, setTagsInput] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [pending, setPending] = React.useState(false);
  const [summary, setSummary] = React.useState<{ filename: string; keyCount: number } | null>(null);

  const reset = React.useCallback(() => {
    setError(null);
    setSummary(null);
    setPending(false);
  }, []);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSummary(null);
    setPending(true);
    try {
      const tags =
        tagsInput
          .split(',')
          .map((x) => x.trim())
          .filter((x) => x.length > 0) || [];
      const token = authStore.getTokens()?.accessToken ?? undefined;
      const result = await downloadExport(
        {
          orgSlug,
          projectSlug,
          languageTag,
          shape,
          namespaceSlug: namespaceSlug || undefined,
          minState: (minState || undefined) as MinState | undefined,
          tags: tags.length > 0 ? tags : undefined,
        },
        { bearerToken: token },
      );
      setSummary({ filename: result.filename, keyCount: result.keyCount });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setPending(false);
    }
  };

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
          <DialogTitle>{t('route.exports.title')}</DialogTitle>
          <DialogDescription>{t('route.exports.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" data-testid="export-form">
          <div className="space-y-1.5">
            <Label htmlFor="export-language">{t('route.exports.languageTag')}</Label>
            <Input
              id="export-language"
              autoFocus
              autoComplete="off"
              value={languageTag}
              onChange={(e) => setLanguageTag(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="export-shape">{t('route.exports.shape')}</Label>
            <select
              id="export-shape"
              value={shape}
              onChange={(e) => setShape(e.target.value as ExportShape)}
              data-testid="export-shape"
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="FLAT">{t('route.exports.shape.flat')}</option>
              <option value="NESTED">{t('route.exports.shape.nested')}</option>
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="export-namespace">{t('route.exports.namespace')}</Label>
            <Input
              id="export-namespace"
              autoComplete="off"
              value={namespaceSlug}
              onChange={(e) => setNamespaceSlug(e.target.value)}
              placeholder={t('route.exports.namespacePlaceholder')}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="export-tags">{t('route.exports.tags')}</Label>
            <Input
              id="export-tags"
              autoComplete="off"
              value={tagsInput}
              onChange={(e) => setTagsInput(e.target.value)}
              placeholder="email,onboarding"
            />
            <p className="text-xs text-muted-foreground">{t('route.exports.tagsHint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="export-min-state">{t('route.exports.minState')}</Label>
            <select
              id="export-min-state"
              value={minState}
              onChange={(e) => setMinState(e.target.value as MinState | '')}
              data-testid="export-min-state"
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">{t('route.exports.minState.any')}</option>
              <option value="DRAFT">{t('translation.state.DRAFT')}+</option>
              <option value="TRANSLATED">{t('translation.state.TRANSLATED')}+</option>
              <option value="REVIEW">{t('translation.state.REVIEW')}+</option>
              <option value="APPROVED">{t('translation.state.APPROVED')}</option>
            </select>
          </div>
          {error ? (
            <p
              className="rounded-md border border-destructive bg-destructive/5 p-2 text-xs text-destructive"
              role="alert"
            >
              {error}
            </p>
          ) : null}
          {summary ? (
            <p className="text-xs text-muted-foreground" role="status" data-testid="export-summary">
              {t('route.exports.done', { filename: summary.filename, count: summary.keyCount })}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
              {t('route.exports.close')}
            </Button>
            <Button type="submit" disabled={pending || languageTag.trim().length === 0}>
              {pending ? t('route.exports.running') : t('route.exports.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
