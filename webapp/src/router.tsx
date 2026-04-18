import * as React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { DashboardRoute } from '@/components/routes/DashboardRoute';
import { NotFoundRoute } from '@/components/routes/NotFoundRoute';
import { OrgsRoute } from '@/components/routes/OrgsRoute';
import { ProjectsRoute } from '@/components/routes/ProjectsRoute';
import { SignInRoute } from '@/components/routes/SignInRoute';
import { useAuth } from '@/lib/auth/useAuth';

/**
 * RequireAuth — redirects to /signin when no user is in the AuthStore.
 * The active route is preserved via location.state so T117's real sign-in
 * page can restore it after successful login.
 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/signin" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/signin" element={<SignInRoute />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardRoute />} />
        <Route path="orgs" element={<OrgsRoute />} />
        <Route path="projects" element={<ProjectsRoute />} />
        <Route path="*" element={<NotFoundRoute />} />
      </Route>
    </Routes>
  );
}
