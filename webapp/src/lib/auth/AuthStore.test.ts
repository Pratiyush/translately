import { beforeEach, describe, expect, it } from 'vitest';
import { authStore, resolveActiveOrg, type AuthUser } from './AuthStore';

const sampleUser: AuthUser = {
  id: 'u1',
  email: 'a@b.test',
  fullName: 'Sample User',
  orgs: [
    { id: 'o1', slug: 'alpha', name: 'Alpha', role: 'OWNER' },
    { id: 'o2', slug: 'bravo', name: 'Bravo', role: 'MEMBER' },
  ],
  activeOrgId: 'o1',
};

describe('AuthStore — persistence', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.refresh();
  });

  it('returns null by default when nothing is stored', () => {
    expect(authStore.getSnapshot()).toBeNull();
  });

  it('persists the user to localStorage after setUser', () => {
    authStore.setUser(sampleUser);
    expect(localStorage.getItem('translately.mockUser')).not.toBeNull();
    expect(authStore.getSnapshot()?.id).toBe('u1');
  });

  it('returns null when the stored value is not valid JSON', () => {
    localStorage.setItem('translately.mockUser', '{not json');
    authStore.refresh();
    expect(authStore.getSnapshot()).toBeNull();
  });

  it('returns null when stored JSON is missing the orgs array', () => {
    localStorage.setItem('translately.mockUser', JSON.stringify({ id: 'x' }));
    authStore.refresh();
    expect(authStore.getSnapshot()).toBeNull();
  });
});

describe('AuthStore — mutation', () => {
  beforeEach(() => {
    localStorage.clear();
    authStore.refresh();
  });

  it('setActiveOrg updates the snapshot when the org exists', () => {
    authStore.setUser(sampleUser);
    authStore.setActiveOrg('o2');
    expect(authStore.getSnapshot()?.activeOrgId).toBe('o2');
  });

  it('setActiveOrg ignores unknown org ids', () => {
    authStore.setUser(sampleUser);
    authStore.setActiveOrg('nope');
    expect(authStore.getSnapshot()?.activeOrgId).toBe('o1');
  });

  it('setActiveOrg no-ops when the user is null', () => {
    authStore.setUser(null);
    authStore.setActiveOrg('o2');
    expect(authStore.getSnapshot()).toBeNull();
  });

  it('signOut clears the user and removes the localStorage key', () => {
    authStore.setUser(sampleUser);
    authStore.signOut();
    expect(authStore.getSnapshot()).toBeNull();
    expect(localStorage.getItem('translately.mockUser')).toBeNull();
  });

  it('notifies subscribers on write', () => {
    let calls = 0;
    const unsub = authStore.subscribe(() => {
      calls += 1;
    });
    authStore.setUser(sampleUser);
    authStore.setActiveOrg('o2');
    authStore.signOut();
    unsub();
    expect(calls).toBeGreaterThanOrEqual(3);
  });
});

describe('resolveActiveOrg', () => {
  it('returns null when no user is present', () => {
    expect(resolveActiveOrg(null)).toBeNull();
  });

  it('returns the selected org when activeOrgId matches', () => {
    expect(resolveActiveOrg(sampleUser)?.id).toBe('o1');
  });

  it('falls back to the first org when activeOrgId is stale', () => {
    const mutated: AuthUser = { ...sampleUser, activeOrgId: 'ghost' };
    expect(resolveActiveOrg(mutated)?.id).toBe('o1');
  });

  it('falls back to null when the user has no orgs', () => {
    const empty: AuthUser = { ...sampleUser, orgs: [], activeOrgId: null };
    expect(resolveActiveOrg(empty)).toBeNull();
  });
});
