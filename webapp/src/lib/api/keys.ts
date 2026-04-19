/**
 * TanStack Query hooks bound to the key / namespace / translation
 * endpoints shipped in PR #172 (T208 backend). The generated `paths` type
 * gives us compile-time URL + body + response contracts; the exported
 * `Key`, `Namespace`, `TranslationCell` interfaces pin the seam so
 * components don't depend on the raw openapi-typescript shape.
 *
 * Consumers import `{ useKeys, useCreateKey, useUpsertTranslation, ... }`
 * and render — every hook handles the auth token (via the singleton
 * `api`'s bearer middleware), the uniform error envelope (via `unwrap`),
 * and query invalidation after mutations.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationOptions,
  type UseQueryOptions,
} from '@tanstack/react-query';
import { api, unwrap, type ApiRequestError } from './client';

// ---------------------------------------------------------------------------
// Shared types (mirror the backend DTOs — see docs/api/keys-and-namespaces.md)
// ---------------------------------------------------------------------------

/**
 * Whole-key state. Advisory for the translator workflow; `Translation`
 * carries its own per-language state. See ADR 0002 for the 5-state
 * machine (EMPTY/DRAFT/TRANSLATED/REVIEW/APPROVED).
 */
export type KeyState = 'NEW' | 'TRANSLATING' | 'REVIEW' | 'DONE' | 'ARCHIVED';

export type TranslationState = 'EMPTY' | 'DRAFT' | 'TRANSLATED' | 'REVIEW' | 'APPROVED';

export interface Key {
  id: string;
  keyName: string;
  namespaceSlug: string;
  description: string | null;
  state: KeyState;
  createdAt: string;
  updatedAt: string;
}

export interface TranslationCell {
  id: string;
  languageTag: string;
  value: string;
  state: TranslationState;
  updatedAt: string;
}

export interface KeyDetails {
  key: Key;
  translations: TranslationCell[];
}

export interface Namespace {
  id: string;
  slug: string;
  name: string;
  description: string | null;
}

export interface ListResponse<T> {
  data: T[];
}

// ---------------------------------------------------------------------------
// Query keys — single source of truth for invalidation
// ---------------------------------------------------------------------------

export const keysQueryKeys = {
  all: ['keys'] as const,
  keys: (orgSlug: string, projectSlug: string, namespaceSlug: string | null = null) =>
    [...keysQueryKeys.all, 'list', orgSlug, projectSlug, namespaceSlug ?? ''] as const,
  key: (orgSlug: string, projectSlug: string, keyId: string) =>
    [...keysQueryKeys.all, 'one', orgSlug, projectSlug, keyId] as const,
  namespaces: (orgSlug: string, projectSlug: string) =>
    [...keysQueryKeys.all, 'namespaces', orgSlug, projectSlug] as const,
};

// ---------------------------------------------------------------------------
// Keys — list / get / create / update / delete
// ---------------------------------------------------------------------------

export interface ListKeysInput {
  orgSlug: string;
  projectSlug: string;
  namespaceSlug?: string | null;
  limit?: number;
  offset?: number;
}

export function useKeys(input: ListKeysInput, options?: UseQueryOptions<Key[], ApiRequestError>) {
  const { orgSlug, projectSlug, namespaceSlug = null, limit, offset } = input;
  return useQuery<Key[], ApiRequestError>({
    queryKey: keysQueryKeys.keys(orgSlug, projectSlug, namespaceSlug),
    enabled: orgSlug.length > 0 && projectSlug.length > 0,
    queryFn: async () => {
      const query: Record<string, string | number> = {};
      if (namespaceSlug) query.namespace = namespaceSlug;
      if (limit != null) query.limit = limit;
      if (offset != null) query.offset = offset;
      const raw = await api.GET('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys', {
        params: {
          path: { orgSlug, projectSlug },
          query: Object.keys(query).length > 0 ? (query as never) : undefined,
        },
      });
      const body = unwrap(
        raw as unknown as { data?: ListResponse<Key>; error?: unknown; response: Response },
      );
      return body?.data ?? [];
    },
    ...options,
  });
}

export interface GetKeyInput {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
}

export function useKey(input: GetKeyInput | null, options?: UseQueryOptions<KeyDetails, ApiRequestError>) {
  const orgSlug = input?.orgSlug ?? '';
  const projectSlug = input?.projectSlug ?? '';
  const keyId = input?.keyId ?? '';
  return useQuery<KeyDetails, ApiRequestError>({
    queryKey: keysQueryKeys.key(orgSlug, projectSlug, keyId),
    enabled: orgSlug.length > 0 && projectSlug.length > 0 && keyId.length > 0,
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys/{keyId}', {
        params: { path: { orgSlug, projectSlug, keyId } },
      });
      return unwrap(raw as unknown as { data?: KeyDetails; error?: unknown; response: Response });
    },
    ...options,
  });
}

export interface CreateKeyInput {
  orgSlug: string;
  projectSlug: string;
  keyName: string;
  namespaceSlug?: string;
  description?: string;
}

export function useCreateKey(options?: UseMutationOptions<Key, ApiRequestError, CreateKeyInput>) {
  const qc = useQueryClient();
  return useMutation<Key, ApiRequestError, CreateKeyInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, ...rest }) => {
      const raw = await api.POST('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys', {
        params: { path: { orgSlug, projectSlug } },
        body: {
          keyName: rest.keyName,
          namespaceSlug: rest.namespaceSlug || undefined,
          description: rest.description || undefined,
        } as never,
      });
      return unwrap(raw as unknown as { data?: Key; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: keysQueryKeys.all });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

export interface UpdateKeyInput {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
  keyName?: string;
  namespaceSlug?: string;
  description?: string | null;
  state?: KeyState;
}

export function useUpdateKey(options?: UseMutationOptions<Key, ApiRequestError, UpdateKeyInput>) {
  const qc = useQueryClient();
  return useMutation<Key, ApiRequestError, UpdateKeyInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, keyId, ...rest }) => {
      const raw = await api.PATCH('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys/{keyId}', {
        params: { path: { orgSlug, projectSlug, keyId } },
        body: {
          keyName: rest.keyName,
          namespaceSlug: rest.namespaceSlug,
          description: rest.description,
          state: rest.state,
        } as never,
      });
      return unwrap(raw as unknown as { data?: Key; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: keysQueryKeys.all });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

export interface DeleteKeyInput {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
}

export function useDeleteKey(options?: UseMutationOptions<void, ApiRequestError, DeleteKeyInput>) {
  const qc = useQueryClient();
  return useMutation<void, ApiRequestError, DeleteKeyInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, keyId }) => {
      const raw = await api.DELETE('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys/{keyId}', {
        params: { path: { orgSlug, projectSlug, keyId } },
      });
      unwrap(raw as unknown as { data?: void; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: keysQueryKeys.all });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

// ---------------------------------------------------------------------------
// Translation cells — upsert one cell
// ---------------------------------------------------------------------------

export interface UpsertTranslationInput {
  orgSlug: string;
  projectSlug: string;
  keyId: string;
  languageTag: string;
  value: string;
  state?: TranslationState;
}

export function useUpsertTranslation(
  options?: UseMutationOptions<TranslationCell, ApiRequestError, UpsertTranslationInput>,
) {
  const qc = useQueryClient();
  return useMutation<TranslationCell, ApiRequestError, UpsertTranslationInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, keyId, languageTag, value, state }) => {
      const raw = await api.PUT(
        '/api/v1/organizations/{orgSlug}/projects/{projectSlug}/keys/{keyId}/translations/{languageTag}',
        {
          params: { path: { orgSlug, projectSlug, keyId, languageTag } },
          body: { value, state } as never,
        },
      );
      return unwrap(raw as unknown as { data?: TranslationCell; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({
        queryKey: keysQueryKeys.key(variables.orgSlug, variables.projectSlug, variables.keyId),
      });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

// ---------------------------------------------------------------------------
// Namespaces — list / create
// ---------------------------------------------------------------------------

export interface ListNamespacesInput {
  orgSlug: string;
  projectSlug: string;
}

export function useNamespaces(
  input: ListNamespacesInput,
  options?: UseQueryOptions<Namespace[], ApiRequestError>,
) {
  const { orgSlug, projectSlug } = input;
  return useQuery<Namespace[], ApiRequestError>({
    queryKey: keysQueryKeys.namespaces(orgSlug, projectSlug),
    enabled: orgSlug.length > 0 && projectSlug.length > 0,
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces', {
        params: { path: { orgSlug, projectSlug } },
      });
      const body = unwrap(
        raw as unknown as { data?: ListResponse<Namespace>; error?: unknown; response: Response },
      );
      return body?.data ?? [];
    },
    ...options,
  });
}

export interface CreateNamespaceInput {
  orgSlug: string;
  projectSlug: string;
  name: string;
  slug?: string;
  description?: string;
}

export function useCreateNamespace(
  options?: UseMutationOptions<Namespace, ApiRequestError, CreateNamespaceInput>,
) {
  const qc = useQueryClient();
  return useMutation<Namespace, ApiRequestError, CreateNamespaceInput>({
    ...options,
    mutationFn: async ({ orgSlug, projectSlug, ...rest }) => {
      const raw = await api.POST('/api/v1/organizations/{orgSlug}/projects/{projectSlug}/namespaces', {
        params: { path: { orgSlug, projectSlug } },
        body: {
          name: rest.name,
          slug: rest.slug || undefined,
          description: rest.description || undefined,
        } as never,
      });
      return unwrap(raw as unknown as { data?: Namespace; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({
        queryKey: keysQueryKeys.namespaces(variables.orgSlug, variables.projectSlug),
      });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}
