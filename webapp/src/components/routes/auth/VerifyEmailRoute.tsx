import { useMutation } from '@tanstack/react-query';
import { CheckCircle2, XCircle } from 'lucide-react';
import * as React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, unwrap } from '@/lib/api/client';
import { t } from '@/i18n';
import { AuthLayout } from './AuthLayout';
import { formatAuthError } from './formUtils';

/**
 * Email-verification landing (T117). The verify email contains a link to
 * `/verify-email?token=<opaque>`; this route fires POST
 * `/api/v1/auth/verify-email` once on mount and surfaces the result.
 *
 * Runs exactly once per token (we guard with a ref) so a React StrictMode
 * double-mount doesn't trigger a `TOKEN_CONSUMED` follow-up that would
 * confuse the success screen.
 */
export function VerifyEmailRoute() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const firedRef = React.useRef(false);

  const mutation = useMutation({
    mutationFn: async () => {
      const response = await api.POST('/api/v1/auth/verify-email', { body: { token } });
      return unwrap(response);
    },
  });

  React.useEffect(() => {
    if (firedRef.current) return;
    if (!token) return;
    firedRef.current = true;
    mutation.mutate();
  }, [token, mutation]);

  if (!token) {
    return (
      <AuthLayout
        title={t('auth.verify.missingTokenTitle')}
        subtitle={t('auth.verify.missingTokenBody')}
        footer={
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signUp.backToSignIn')}
          </Link>
        }
        testId="route-verify-missing"
      />
    );
  }

  if (mutation.isSuccess) {
    return (
      <AuthLayout
        title={t('auth.verify.successTitle')}
        subtitle={t('auth.verify.successBody')}
        footer={
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.verify.signInCta')}
          </Link>
        }
        testId="route-verify-success"
      >
        <div className="flex items-center justify-center py-4">
          <CheckCircle2 className="h-12 w-12 text-primary" aria-hidden="true" />
        </div>
      </AuthLayout>
    );
  }

  if (mutation.isError) {
    return (
      <AuthLayout
        title={t('auth.verify.errorTitle')}
        subtitle={formatAuthError(mutation.error)}
        footer={
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signUp.backToSignIn')}
          </Link>
        }
        testId="route-verify-error"
      >
        <div className="flex items-center justify-center py-4">
          <XCircle className="h-12 w-12 text-destructive" aria-hidden="true" />
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t('auth.verify.pendingTitle')}
      subtitle={t('auth.verify.pendingBody')}
      testId="route-verify-pending"
    />
  );
}
