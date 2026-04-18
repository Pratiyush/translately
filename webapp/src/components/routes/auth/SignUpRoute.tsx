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
import { extractFieldErrors, formatAuthError } from './formUtils';

/**
 * Sign-up form validation. Backend enforces email shape + min-12 password
 * too; we mirror it client-side so the first-wrong-click feedback is
 * instant. Any stricter backend rule (`EMAIL_TAKEN`, etc.) surfaces via
 * the mutation error via [formatAuthError].
 */
const schema = z.object({
  email: z.string().email({ message: 'INVALID_EMAIL' }),
  password: z.string().min(12, { message: 'TOO_SHORT' }),
  fullName: z.string().min(1, { message: 'REQUIRED' }),
});
type SignUpInput = z.infer<typeof schema>;

/**
 * Sign-up page (T117). POSTs to `/api/v1/auth/signup`. On success the
 * backend queues a verification email via Mailpit (dev) / SMTP (prod);
 * we render a "check your inbox" confirmation rather than immediately
 * logging the user in — the account is unverified until they click the
 * link, and login against it would 403 with `EMAIL_NOT_VERIFIED`.
 */
export function SignUpRoute() {
  const form = useForm<SignUpInput>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '', fullName: '' },
  });

  const mutation = useMutation({
    mutationFn: async (input: SignUpInput) => {
      const response = await api.POST('/api/v1/auth/signup', {
        body: { email: input.email, password: input.password, fullName: input.fullName },
      });
      return unwrap(response);
    },
  });

  const onSubmit = form.handleSubmit((data) => mutation.mutate(data));
  const serverFieldErrors = extractFieldErrors(mutation.error);
  const fieldError = (name: keyof SignUpInput): string | undefined => {
    const code = form.formState.errors[name]?.message ?? serverFieldErrors[name];
    if (!code) return undefined;
    const key = `auth.field.${code}` as const;
    const translated = t(key as 'auth.field.REQUIRED');
    return translated === key ? code : translated;
  };

  if (mutation.isSuccess) {
    return (
      <AuthLayout
        title={t('auth.signUp.successTitle')}
        subtitle={t('auth.signUp.successBody', { email: form.getValues('email') })}
        footer={
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signUp.backToSignIn')}
          </Link>
        }
        testId="route-sign-up-success"
      />
    );
  }

  return (
    <AuthLayout
      title={t('auth.signUp.title')}
      subtitle={t('auth.signUp.subtitle')}
      footer={
        <>
          {t('auth.signUp.alreadyHave')}{' '}
          <Link to="/signin" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signIn.submit')}
          </Link>
        </>
      }
      testId="route-sign-up"
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="fullName">{t('auth.field.fullName')}</Label>
          <Input
            id="fullName"
            autoComplete="name"
            autoFocus
            aria-invalid={fieldError('fullName') !== undefined}
            aria-describedby={fieldError('fullName') ? 'fullName-error' : undefined}
            {...form.register('fullName')}
          />
          {fieldError('fullName') ? (
            <p id="fullName-error" className="text-sm text-destructive">
              {fieldError('fullName')}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="email">{t('auth.field.email')}</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
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

        <div className="space-y-1.5">
          <Label htmlFor="password">{t('auth.field.password')}</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={fieldError('password') !== undefined}
            aria-describedby={fieldError('password') ? 'password-error' : undefined}
            {...form.register('password')}
          />
          <p className="text-xs text-muted-foreground">{t('auth.signUp.passwordHint')}</p>
          {fieldError('password') ? (
            <p id="password-error" className="text-sm text-destructive">
              {fieldError('password')}
            </p>
          ) : null}
        </div>

        {mutation.isError && !Object.keys(serverFieldErrors).length ? (
          <p role="alert" className="text-sm text-destructive" data-testid="sign-up-error">
            {formatAuthError(mutation.error)}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={mutation.isPending} data-testid="sign-up-submit">
          {mutation.isPending ? t('auth.signUp.submitting') : t('auth.signUp.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
