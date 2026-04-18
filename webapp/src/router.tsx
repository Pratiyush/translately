import * as React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { DashboardRoute } from '@/components/routes/DashboardRoute';
import { NotFoundRoute } from '@/components/routes/NotFoundRoute';
import { OrgsRoute } from '@/components/routes/OrgsRoute';
import { ProjectsRoute } from '@/components/routes/ProjectsRoute';
import { ForgotPasswordRoute } from '@/components/routes/auth/ForgotPasswordRoute';
import { ResetPasswordRoute } from '@/components/routes/auth/ResetPasswordRoute';
import { SignInRoute } from '@/components/routes/auth/SignInRoute';
import { SignUpRoute } from '@/components/routes/auth/SignUpRoute';
import { VerifyEmailRoute } from '@/components/routes/auth/VerifyEmailRoute';
import { useAuth } from '@/lib/auth/useAuth';

/**
 * RequireAuth — redirects to /signin when no user is in the AuthStore.
 * The active route is preserved via `location.state.from` so T117's
 * sign-in page can return the user here after a successful login.
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
      {/* Public auth surface. Five pages, all rendered outside AppShell. */}
      <Route path="/signin" element={<SignInRoute />} />
      <Route path="/signup" element={<SignUpRoute />} />
      <Route path="/verify-email" element={<VerifyEmailRoute />} />
      <Route path="/forgot-password" element={<ForgotPasswordRoute />} />
      <Route path="/reset-password" element={<ResetPasswordRoute />} />

      {/* Everything else lives inside the shell and requires a session. */}
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
