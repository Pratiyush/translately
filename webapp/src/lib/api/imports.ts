/**
 * TanStack Query hooks for the i18next JSON import + export endpoints
 * (T301 + T302). Import is a POST that returns counts + error rows;
 * export is a GET that returns the JSON file body, used to drive the
 * browser "save file" flow.
 *
 * The hooks stay small because the import wizard (T304) and export
 * modal (T305) own the UX choreography — conflict selection, preview,
 * confirm. These are the data-plane wrappers they compose.
 */
import { useMutation, type UseMutationOptions } from '@tanstack/react-query';
import { api, unwrap, type ApiRequestError } from './client';
import { keysQueryKeys } from './keys';
import { useQueryClient } from '@tanstack/react-query';

export type ConflictMode = 'KEEP' | 'OVERWRITE' | 'MERGE';
export type ExportShape = 'FLAT' | 'NESTED';
export type MinState = 'EMPTY' | 'DRAFT' | 'TRANSLATED' | 'REVIEW' | 'APPROVED';

export interface ImportError {
  keyName: string;
  code: string;
  message: string;
}

export interface ImportResult {
  total: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  errors: ImportError[];
}

export interface ImportJsonInput {
  orgSlug: string;
  projectSlug: string;
  languageTag: string;
  namespaceSlug?: string;
  mode: ConflictMode;
  /** Raw i18next JSON payload, flat or nested — server auto-detects. */
  body: string;
}

export function useImportJson(options?: UseMutationOptions<ImportResult, ApiRequestError, ImportJsonInput>) {
  const qc = useQueryClient();
  return useMutation<ImportResult, ApiRequestError, ImportJsonInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, languageTag, namespaceSlug, mode, body }) => {
      const raw = await api.POST('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/imports/json', {
        params: { path: { orgSlug, projectSlug } },
        body: {
          languageTag,
          namespaceSlug: namespaceSlug || undefined,
          mode,
          body,
        } as never,
      });
      return unwrap(raw as unknown as { data?: ImportResult; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: keysQueryKeys.all });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

export interface ExportJsonInput {
  orgSlug: string;
  projectSlug: string;
  languageTag: string;
  namespaceSlug?: string;
  tags?: string[];
  minState?: MinState;
  shape: ExportShape;
}

export interface ExportJsonResult {
  body: string;
  filename: string;
  keyCount: number;
}

/**
 * The export endpoint returns the file body directly. Hit it with
 * `fetch` so we can read the `Content-Disposition` header and pick the
 * suggested filename — openapi-fetch's typed client strips non-JSON
 * bodies through its JSON parser.
 */
export async function exportTranslations(
  input: ExportJsonInput,
  opts?: { bearerToken?: string; fetchImpl?: typeof fetch },
): Promise<ExportJsonResult> {
  const params = new URLSearchParams();
  params.set('languageTag', input.languageTag);
  params.set('shape', input.shape);
  if (input.namespaceSlug) params.set('namespaceSlug', input.namespaceSlug);
  if (input.minState) params.set('minState', input.minState);
  if (input.tags && input.tags.length > 0) params.set('tags', input.tags.join(','));

  const url = `/api/v1/organizations/${encodeURIComponent(input.orgSlug)}/projects/${encodeURIComponent(input.projectSlug)}/exports/json?${params.toString()}`;
  const fetchImpl = opts?.fetchImpl ?? globalThis.fetch.bind(globalThis);
  const headers: Record<string, string> = {};
  if (opts?.bearerToken) headers.authorization = `Bearer ${opts.bearerToken}`;
  const res = await fetchImpl(url, { headers });

  const body = await res.text();
  if (!res.ok) {
    let code = 'UNKNOWN';
    let message = `Export failed (${res.status})`;
    try {
      const parsed = JSON.parse(body) as { error?: { code?: string; message?: string } };
      code = parsed.error?.code ?? code;
      message = parsed.error?.message ?? message;
    } catch {
      /* body wasn't JSON — keep defaults */
    }
    throw new Error(`${code}: ${message}`);
  }

  const disposition = res.headers.get('content-disposition') ?? '';
  const match = /filename="([^"]+)"/.exec(disposition);
  const filename =
    match?.[1] ?? `${input.projectSlug}-${input.languageTag}-${input.shape.toLowerCase()}.json`;
  const keyCountHeader = res.headers.get('x-translately-key-count');
  const keyCount = keyCountHeader ? Number(keyCountHeader) : 0;
  return { body, filename, keyCount };
}

/**
 * Convenience: export and kick off a browser download. Used by the
 * Export modal (T305). Caller supplies the token — the modal owns
 * localStorage access via the `authStore`.
 */
export async function downloadExport(
  input: ExportJsonInput,
  opts?: { bearerToken?: string },
): Promise<ExportJsonResult> {
  const result = await exportTranslations(input, opts);
  if (typeof document !== 'undefined') {
    const blob = new Blob([result.body], { type: 'application/json' });
    const href = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = href;
    a.download = result.filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(href), 1000);
  }
  return result;
}
