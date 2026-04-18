import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { api, unwrap } from '@/lib/api/client';
import { t } from '@/i18n';
import { AuthLayout } from './AuthLayout';
import { formatAuthError } from './formUtils';

const schema = z.object({
  email: z.string().email({ message: 'INVALID_EMAIL' }),
});
type ForgotInput = z.infer<typeof schema>;

/**
 * Forgot-password page (T117). Always renders the "check your inbox" view
 * on submit — the backend returns 202 whether or not the account exists
 * (anti-enumeration), so the UI matches that behaviour exactly. No
 * "unknown email" hint anywhere.
 */
export function ForgotPasswordRoute() {
  const form = useForm<ForgotInput>({
    resolver: zodResolver(schema),
    defaultValues: { email: '' },
  });

  const mutation = useMutation({
    mutationFn: async (input: ForgotInput) => {
      const response = await api.POST('/api/v1/auth/forgot-password', {
        body: { email: input.email },
      });
      return unwrap(response);
    },
  });

  const onSubmit = form.handleSubmit((data) => mutation.mutate(data));
  const fieldError = (name: keyof ForgotInput): string | undefined => {
    const msg = form.formState.errors[name]?.message;
    if (!msg) return undefined;
    const key = `auth.field.${msg}` as const;
    const translated = t(key as 'auth.field.REQUIRED');
    return translated === key ? msg : translated;
  };

  if (mutation.isSuccess) {
    return (
      <AuthLayout
        title={t('auth.forgot.successTitle')}
        subtitle={t('auth.forgot.successBody', { email: form.getValues('email') })}
        footer={
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signUp.backToSignIn')}
          </Link>
        }
        testId="route-forgot-success"
      />
    );
  }

  return (
    <AuthLayout
      title={t('auth.forgot.title')}
      subtitle={t('auth.forgot.subtitle')}
      footer={
        <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
          {t('auth.signUp.backToSignIn')}
        </Link>
      }
      testId="route-forgot"
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="email">{t('auth.field.email')}</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            autoFocus
            aria-invalid={fieldError('email') !== undefined}
            aria-describedby={fieldError('email') ? 'email-error' : undefined}
            {...form.register('email')}
          />
          {fieldError('email') ? (
            <p id="email-error" className="text-sm text-destructive">
              {fieldError('email')}
            </p>
          ) : null}
        </div>

        {mutation.isError ? (
          <p role="alert" className="text-sm text-destructive" data-testid="forgot-error">
            {formatAuthError(mutation.error)}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={mutation.isPending} data-testid="forgot-submit">
          {mutation.isPending ? t('auth.forgot.submitting') : t('auth.forgot.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
