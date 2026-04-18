import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { api, unwrap } from '@/lib/api/client';
import { t } from '@/i18n';
import { AuthLayout } from './AuthLayout';
import { formatAuthError } from './formUtils';

const schema = z.object({
  newPassword: z.string().min(12, { message: 'TOO_SHORT' }),
});
type ResetInput = z.infer<typeof schema>;

/**
 * Password-reset landing (T117). The reset email contains a link to
 * `/reset-password?token=<opaque>`; this page takes a new password + the
 * token and POSTs `/api/v1/auth/reset-password`. On success redirects to
 * `/signin` with a success banner in state (rendered there when T120's
 * full i18n lands — for now we just redirect).
 */
export function ResetPasswordRoute() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();

  const form = useForm<ResetInput>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '' },
  });

  const mutation = useMutation({
    mutationFn: async (input: ResetInput) => {
      const response = await api.POST('/api/v1/auth/reset-password', {
        body: { token, newPassword: input.newPassword },
      });
      return unwrap(response);
    },
    onSuccess: () => {
      // A reset invalidates every active refresh token server-side, so
      // clear any stored client tokens then bounce to sign-in.
      navigate('/signin', { replace: true });
    },
  });

  const onSubmit = form.handleSubmit((data) => mutation.mutate(data));
  const fieldError = (name: keyof ResetInput): string | undefined => {
    const msg = form.formState.errors[name]?.message;
    if (!msg) return undefined;
    const key = `auth.field.${msg}` as const;
    const translated = t(key as 'auth.field.REQUIRED');
    return translated === key ? msg : translated;
  };

  if (!token) {
    return (
      <AuthLayout
        title={t('auth.verify.missingTokenTitle')}
        subtitle={t('auth.reset.missingTokenBody')}
        footer={
          <Link to="/forgot-password" className="text-primary underline-offset-4 hover:underline">
            {t('auth.reset.retry')}
          </Link>
        }
        testId="route-reset-missing"
      />
    );
  }

  return (
    <AuthLayout
      title={t('auth.reset.title')}
      subtitle={t('auth.reset.subtitle')}
      footer={
        <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
          {t('auth.signUp.backToSignIn')}
        </Link>
      }
      testId="route-reset"
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="newPassword">{t('auth.reset.newPassword')}</Label>
          <Input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            autoFocus
            aria-invalid={fieldError('newPassword') !== undefined}
            aria-describedby={fieldError('newPassword') ? 'newPassword-error' : undefined}
            {...form.register('newPassword')}
          />
          <p className="text-xs text-muted-foreground">{t('auth.signUp.passwordHint')}</p>
          {fieldError('newPassword') ? (
            <p id="newPassword-error" className="text-sm text-destructive">
              {fieldError('newPassword')}
            </p>
          ) : null}
        </div>

        {mutation.isError ? (
          <p role="alert" className="text-sm text-destructive" data-testid="reset-error">
            {formatAuthError(mutation.error)}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={mutation.isPending} data-testid="reset-submit">
          {mutation.isPending ? t('auth.reset.submitting') : t('auth.reset.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
