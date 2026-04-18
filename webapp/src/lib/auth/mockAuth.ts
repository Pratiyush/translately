import { authStore, type AuthUser } from './AuthStore';

/**
 * Dev-only mock auth helpers. `seedDevUser()` writes a fake user + two orgs
 * into localStorage so the app shell renders something realistic before the
 * real sign-in flow lands in T117. Never called in production bundles —
 * callers should gate invocations behind `import.meta.env.DEV`.
 */

export const DEV_USER: AuthUser = {
  id: 'user_01HQZZZZZZZZZZZZZZZZZZZZZZ',
  email: 'dev@translately.test',
  fullName: 'Dev Pratiyush',
  orgs: [
    {
      id: 'org_01HQAAAA000000000000000000',
      slug: 'acme',
      name: 'Acme Corp',
      role: 'OWNER',
    },
    {
      id: 'org_01HQBBBB000000000000000000',
      slug: 'contoso',
      name: 'Contoso Ltd',
      role: 'MEMBER',
    },
  ],
  activeOrgId: 'org_01HQAAAA000000000000000000',
};

export function seedDevUser(): void {
  authStore.setUser(DEV_USER);
}

export function clearDevUser(): void {
  authStore.signOut();
}
