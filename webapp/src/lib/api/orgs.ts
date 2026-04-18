/**
 * TanStack Query hooks bound to the org / project / member endpoints
 * (T118 + T119). The generated `paths` types give us compile-time URL
 * + body + response contracts; we keep explicit `Org`/`Project`/`Member`
 * interfaces at the seam so components don't depend on the raw
 * openapi-typescript shape.
 *
 * Consumers import `{ useOrgs, useCreateOrg, ... }` and render — every
 * hook handles the auth token (via the singleton `api`'s bearer
 * middleware), the uniform error envelope (via `unwrap`), and query
 * invalidation after mutations.
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
// Shared types (mirror backend DTOs — see docs/api/organizations-and-projects.md)
// ---------------------------------------------------------------------------

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface Org {
  id: string;
  slug: string;
  name: string;
  callerRole: OrgRole;
  createdAt: string;
}

export interface Project {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  baseLanguageTag: string;
  createdAt: string;
}

export interface Member {
  userId: string;
  email: string;
  fullName: string;
  role: OrgRole;
  invitedAt: string;
  joinedAt: string | null;
}

export interface ListResponse<T> {
  data: T[];
}

// ---------------------------------------------------------------------------
// Query keys — single source of truth for invalidation
// ---------------------------------------------------------------------------

export const orgsQueryKeys = {
  all: ['orgs'] as const,
  list: () => [...orgsQueryKeys.all, 'list'] as const,
  one: (slug: string) => [...orgsQueryKeys.all, 'one', slug] as const,
  members: (slug: string) => [...orgsQueryKeys.all, 'members', slug] as const,
  projects: (slug: string) => [...orgsQueryKeys.all, 'projects', slug] as const,
};

// ---------------------------------------------------------------------------
// Orgs
// ---------------------------------------------------------------------------

export function useOrgs(options?: UseQueryOptions<Org[], ApiRequestError>) {
  return useQuery<Org[], ApiRequestError>({
    queryKey: orgsQueryKeys.list(),
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations');
      const body = unwrap(
        raw as unknown as { data?: ListResponse<Org>; error?: unknown; response: Response },
      );
      return body?.data ?? [];
    },
    ...options,
  });
}

export function useOrg(slug: string | null, options?: UseQueryOptions<Org, ApiRequestError>) {
  return useQuery<Org, ApiRequestError>({
    queryKey: orgsQueryKeys.one(slug ?? ''),
    enabled: slug != null && slug.length > 0,
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations/{orgSlug}', {
        params: { path: { orgSlug: slug! } },
      });
      return unwrap(raw as unknown as { data?: Org; error?: unknown; response: Response });
    },
    ...options,
  });
}

export interface CreateOrgInput {
  name: string;
  slug?: string;
}

export function useCreateOrg(options?: UseMutationOptions<Org, ApiRequestError, CreateOrgInput>) {
  const qc = useQueryClient();
  return useMutation<Org, ApiRequestError, CreateOrgInput>({
    ...options,
    mutationFn: async (input) => {
      const raw = await api.POST('/api/v1/organizations', {
        body: { name: input.name, slug: input.slug || undefined } as never,
      });
      return unwrap(raw as unknown as { data?: Org; error?: unknown; response: Response });
    },
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: orgsQueryKeys.list() });
      options?.onSuccess?.(...args);
    },
  });
}

export interface RenameOrgInput {
  slug: string;
  name: string;
}

export function useRenameOrg(options?: UseMutationOptions<Org, ApiRequestError, RenameOrgInput>) {
  const qc = useQueryClient();
  return useMutation<Org, ApiRequestError, RenameOrgInput>({
    ...options,
    mutationFn: async ({ slug, name }) => {
      const raw = await api.PATCH('/api/v1/organizations/{orgSlug}', {
        params: { path: { orgSlug: slug } },
        body: { name } as never,
      });
      return unwrap(raw as unknown as { data?: Org; error?: unknown; response: Response });
    },
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: orgsQueryKeys.all });
      options?.onSuccess?.(...args);
    },
  });
}

// ---------------------------------------------------------------------------
// Members
// ---------------------------------------------------------------------------

export function useOrgMembers(slug: string | null, options?: UseQueryOptions<Member[], ApiRequestError>) {
  return useQuery<Member[], ApiRequestError>({
    queryKey: orgsQueryKeys.members(slug ?? ''),
    enabled: slug != null && slug.length > 0,
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations/{orgSlug}/members', {
        params: { path: { orgSlug: slug! } },
      });
      const body = unwrap(
        raw as unknown as { data?: ListResponse<Member>; error?: unknown; response: Response },
      );
      return body?.data ?? [];
    },
    ...options,
  });
}

export interface ChangeMemberRoleInput {
  orgSlug: string;
  userId: string;
  role: OrgRole;
}

export function useChangeMemberRole(
  options?: UseMutationOptions<Member, ApiRequestError, ChangeMemberRoleInput>,
) {
  const qc = useQueryClient();
  return useMutation<Member, ApiRequestError, ChangeMemberRoleInput>({
    ...options,
    mutationFn: async ({ orgSlug, userId, role }) => {
      const raw = await api.PATCH('/api/v1/organizations/{orgSlug}/members/{userId}', {
        params: { path: { orgSlug, userId } },
        body: { role } as never,
      });
      return unwrap(raw as unknown as { data?: Member; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: orgsQueryKeys.members(variables.orgSlug) });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

export interface RemoveMemberInput {
  orgSlug: string;
  userId: string;
}

export function useRemoveMember(options?: UseMutationOptions<void, ApiRequestError, RemoveMemberInput>) {
  const qc = useQueryClient();
  return useMutation<void, ApiRequestError, RemoveMemberInput>({
    ...options,
    mutationFn: async ({ orgSlug, userId }) => {
      const raw = await api.DELETE('/api/v1/organizations/{orgSlug}/members/{userId}', {
        params: { path: { orgSlug, userId } },
      });
      unwrap(raw as unknown as { data?: void; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: orgsQueryKeys.members(variables.orgSlug) });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}

// ---------------------------------------------------------------------------
// Projects (scoped by org)
// ---------------------------------------------------------------------------

export function useOrgProjects(slug: string | null, options?: UseQueryOptions<Project[], ApiRequestError>) {
  return useQuery<Project[], ApiRequestError>({
    queryKey: orgsQueryKeys.projects(slug ?? ''),
    enabled: slug != null && slug.length > 0,
    queryFn: async () => {
      const raw = await api.GET('/api/v1/organizations/{orgSlug}/projects', {
        params: { path: { orgSlug: slug! } },
      });
      const body = unwrap(
        raw as unknown as { data?: ListResponse<Project>; error?: unknown; response: Response },
      );
      return body?.data ?? [];
    },
    ...options,
  });
}

export interface CreateProjectInput {
  orgSlug: string;
  name: string;
  slug?: string;
  description?: string;
  baseLanguageTag?: string;
}

export function useCreateProject(options?: UseMutationOptions<Project, ApiRequestError, CreateProjectInput>) {
  const qc = useQueryClient();
  return useMutation<Project, ApiRequestError, CreateProjectInput>({
    ...options,
    mutationFn: async ({ orgSlug, ...rest }) => {
      const raw = await api.POST('/api/v1/organizations/{orgSlug}/projects', {
        params: { path: { orgSlug } },
        body: {
          name: rest.name,
          slug: rest.slug || undefined,
          description: rest.description || undefined,
          baseLanguageTag: rest.baseLanguageTag || undefined,
        } as never,
      });
      return unwrap(raw as unknown as { data?: Project; error?: unknown; response: Response });
    },
    onSuccess: (data, variables, ...rest) => {
      qc.invalidateQueries({ queryKey: orgsQueryKeys.projects(variables.orgSlug) });
      options?.onSuccess?.(data, variables, ...rest);
    },
  });
}
