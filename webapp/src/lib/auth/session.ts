/**
 * Login-response handling + JWT decode. Keeps token-shape logic in one
 * place so the AuthStore doesn't need to know about Smallrye claim names.
 *
 * T117 consumes this after a successful `/api/v1/auth/login` to populate
 * the AuthStore: user info is derived from the access token's claims
 * (`sub`, `upn`, `orgs`) so we don't need a `/users/me` round-trip.
 */
import type { AuthOrg, AuthUser, OrganizationRole } from './AuthStore';

/** Token pair shape returned by `POST /api/v1/auth/login` + `/auth/refresh`. */
export interface TokenPair {
  accessToken: string;
  accessExpiresAt: string; // ISO-8601 UTC
  refreshToken: string;
  refreshExpiresAt: string; // ISO-8601 UTC
}

const ORG_ROLES: ReadonlyArray<OrganizationRole> = ['OWNER', 'ADMIN', 'MEMBER'];

/**
 * Parse the payload portion of a compact-serialized JWT. Does NOT verify
 * the signature — verification is the server's job; we only decode to
 * read claims the server just told us are valid.
 */
export function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const parts = jwt.split('.');
  if (parts.length !== 3) {
    throw new Error('Malformed JWT: expected 3 parts');
  }
  const payload = parts[1]!;
  // base64url → base64 + padding
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
  // Browsers + Node 16+ both expose `atob` on globalThis. Keeping this
  // branch-free means we don't depend on @types/node for a webapp file.
  const json = globalThis.atob(padded);
  return JSON.parse(json) as Record<string, unknown>;
}

/**
 * Build an [AuthUser] from a successful login response by reading the
 * access token's `sub` / `upn` / `orgs` claims. We don't have a
 * `/users/me` endpoint yet, so `fullName` defaults to the local part of
 * the email until a dedicated profile fetch lands in a later ticket.
 */
export function userFromLoginResponse(tokens: TokenPair): AuthUser {
  const payload = decodeJwtPayload(tokens.accessToken);
  const sub = typeof payload.sub === 'string' ? payload.sub : '';
  const upn = typeof payload.upn === 'string' ? payload.upn : '';
  const rawOrgs = Array.isArray(payload.orgs) ? (payload.orgs as unknown[]) : [];
  const orgs: AuthOrg[] = rawOrgs.flatMap((raw) => {
    if (!raw || typeof raw !== 'object') return [];
    const o = raw as Record<string, unknown>;
    const id = typeof o.id === 'string' ? o.id : null;
    const slug = typeof o.slug === 'string' ? o.slug : null;
    const roleRaw = typeof o.role === 'string' ? o.role.toUpperCase() : null;
    if (!id || !slug || !roleRaw) return [];
    if (!ORG_ROLES.includes(roleRaw as OrganizationRole)) return [];
    return [{ id, slug, name: slug, role: roleRaw as OrganizationRole }];
  });
  return {
    id: sub,
    email: upn,
    // Until a profile fetch lands, surface the email local-part as a
    // best-effort fullName so UserMenu shows initials rather than '?'.
    fullName: upn.includes('@') ? upn.split('@')[0]! : upn,
    orgs,
    activeOrgId: orgs[0]?.id ?? null,
  };
}
