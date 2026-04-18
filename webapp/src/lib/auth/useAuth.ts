import * as React from 'react';
import { authStore, resolveActiveOrg, type AuthOrg, type AuthUser } from './AuthStore';

/**
 * React binding around the imperative `authStore`. Uses `useSyncExternalStore`
 * so components re-render deterministically when the store mutates — including
 * cross-tab updates via the `storage` event.
 */
export interface UseAuthResult {
  user: AuthUser | null;
  activeOrg: AuthOrg | null;
  setActiveOrg: (orgId: string) => void;
  signOut: () => void;
}

function subscribe(listener: () => void): () => void {
  const unsubscribeStore = authStore.subscribe(listener);
  const storageHandler = (event: StorageEvent) => {
    if (event.key === 'translately.mockUser' || event.key === null) {
      authStore.refresh();
    }
  };
  window.addEventListener('storage', storageHandler);
  return () => {
    unsubscribeStore();
    window.removeEventListener('storage', storageHandler);
  };
}

export function useAuth(): UseAuthResult {
  const user = React.useSyncExternalStore(subscribe, authStore.getSnapshot, authStore.getSnapshot);
  const activeOrg = React.useMemo(() => resolveActiveOrg(user), [user]);
  return {
    user,
    activeOrg,
    setActiveOrg: authStore.setActiveOrg.bind(authStore),
    signOut: authStore.signOut.bind(authStore),
  };
}
