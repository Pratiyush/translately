/**
 * AuthStore — minimal client-side auth state for the app shell.
 *
 * Phase 1 intentionally ships a mock store: it reads a pre-seeded user from
 * localStorage and exposes a handful of imperative mutators for the shell
 * (org switch, sign out). Phase 1 task T117 replaces the persistence layer
 * with the real backend-issued JWT flow; the public interface here is the
 * shape the shell already consumes, so the swap is non-breaking.
 */

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface AuthOrg {
  id: string;
  slug: string;
  name: string;
  role: OrganizationRole;
}

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string;
  orgs: AuthOrg[];
  activeOrgId: string | null;
}

const STORAGE_KEY = 'translately.mockUser';
const TOKENS_KEY = 'translately.tokens';

export interface StoredTokens {
  accessToken: string;
  accessExpiresAt: string;
  refreshToken: string;
  refreshExpiresAt: string;
}

type Listener = () => void;

export interface AuthSnapshot {
  user: AuthUser | null;
  activeOrg: AuthOrg | null;
}

/**
 * Read-only snapshot accessor + imperative mutators. Not a React-specific
 * store; the `useAuthStore` hook in `./useAuthStore.ts` (lands with T117)
 * will wrap this with React external-store semantics. For now the shell
 * consumes it via a thin `useSyncExternalStore` helper defined below.
 */
class AuthStoreImpl {
  private listeners = new Set<Listener>();
  private cached: AuthUser | null | undefined = undefined;

  private read(): AuthUser | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as AuthUser;
      if (!parsed || typeof parsed !== 'object') return null;
      if (!Array.isArray(parsed.orgs)) return null;
      return parsed;
    } catch {
      return null;
    }
  }

  private write(user: AuthUser | null) {
    try {
      if (user === null) {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
      }
    } catch {
      // storage unavailable — acceptable for shell
    }
    this.cached = user;
    this.emit();
  }

  private emit() {
    for (const l of this.listeners) l();
  }

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  /**
   * Snapshot accessor used by `useSyncExternalStore`. Returns a stable
   * reference between emits so the hook can short-circuit re-renders.
   */
  getSnapshot = (): AuthUser | null => {
    if (this.cached === undefined) this.cached = this.read();
    return this.cached;
  };

  /** Force a re-read from localStorage (used by tests after seeding). */
  refresh(): void {
    this.cached = this.read();
    this.emit();
  }

  setUser(user: AuthUser | null): void {
    this.write(user);
  }

  setActiveOrg(orgId: string): void {
    const user = this.getSnapshot();
    if (!user) return;
    const next = user.orgs.find((o) => o.id === orgId);
    if (!next) return;
    this.write({ ...user, activeOrgId: orgId });
  }

  signOut(): void {
    this.write(null);
    this.setTokens(null);
  }

  /**
   * Access + refresh token accessors. Tokens persist alongside the user
   * but in a separate localStorage key so dev-mode mock seeding (which
   * doesn't have a backend) never resurrects stale credentials.
   */
  getTokens(): StoredTokens | null {
    try {
      const raw = localStorage.getItem(TOKENS_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as StoredTokens;
      if (!parsed || typeof parsed.accessToken !== 'string') return null;
      return parsed;
    } catch {
      return null;
    }
  }

  setTokens(tokens: StoredTokens | null): void {
    try {
      if (tokens === null) {
        localStorage.removeItem(TOKENS_KEY);
      } else {
        localStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
      }
    } catch {
      // storage unavailable — acceptable
    }
  }
}

export const authStore = new AuthStoreImpl();

/**
 * Derive the currently-active organization for the snapshot.
 * Handy in selectors/components without reconstructing the lookup each time.
 */
export function resolveActiveOrg(user: AuthUser | null): AuthOrg | null {
  if (!user) return null;
  if (user.activeOrgId) {
    const hit = user.orgs.find((o) => o.id === user.activeOrgId);
    if (hit) return hit;
  }
  return user.orgs[0] ?? null;
}
