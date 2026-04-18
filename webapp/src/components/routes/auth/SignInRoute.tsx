import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { api, unwrap } from '@/lib/api/client';
import { authStore } from '@/lib/auth/AuthStore';
import { seedDevUser } from '@/lib/auth/mockAuth';
import { userFromLoginResponse, type TokenPair } from '@/lib/auth/session';
import { t } from '@/i18n';
import { AuthLayout } from './AuthLayout';
import { formatAuthError } from './formUtils';

const schema = z.object({
  email: z.string().email({ message: 'INVALID_EMAIL' }),
  password: z.string().min(1, { message: 'REQUIRED' }),
});
type SignInInput = z.infer<typeof schema>;

/**
 * Sign-in page (T117). POSTs to `/api/v1/auth/login`, stores the returned
 * token pair via `authStore.setTokens(...)`, derives the user shape from
 * the access-token claims, and redirects to `location.state.from` (if
 * present) or `/` (the dashboard).
 *
 * Dev bundles still expose the "Seed dev user" affordance so reviewers can
 * drop into the shell without a running backend.
 */
export function SignInRoute() {
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: { pathname?: string } })?.from?.pathname ?? '/';

  const form = useForm<SignInInput>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  const mutation = useMutation({
    mutationFn: async (input: SignInInput) => {
      const response = await api.POST('/api/v1/auth/login', {
        body: { email: input.email, password: input.password },
      });
      // The generated response type is `never` because the OpenAPI schema
      // doesn't declare a body — the backend still returns the token pair;
      // widen to `unknown` before the known-shape cast.
      const tokens = unwrap(response as unknown as { data?: TokenPair; error?: unknown; response: Response });
      if (!tokens || typeof tokens.accessToken !== 'string') {
        throw new Error('Login succeeded but the response did not carry a token pair.');
      }
      return tokens;
    },
    onSuccess: (tokens) => {
      authStore.setTokens(tokens);
      authStore.setUser(userFromLoginResponse(tokens));
      navigate(redirectTo, { replace: true });
    },
  });

  const onSubmit = form.handleSubmit((data) => mutation.mutate(data));
  const fieldError = (name: keyof SignInInput): string | undefined => {
    const msg = form.formState.errors[name]?.message;
    if (!msg) return undefined;
    const key = `auth.field.${msg}` as const;
    const translated = t(key as 'auth.field.REQUIRED');
    return translated === key ? msg : translated;
  };

  return (
    <AuthLayout
      title={t('auth.signIn.title')}
      subtitle={t('auth.signIn.subtitle')}
      footer={
        <>
          {t('auth.signIn.noAccount')}{' '}
          <Link to="/signup" className="text-primary underline-offset-4 hover:underline">
            {t('auth.signIn.signUpCta')}
          </Link>
        </>
      }
      testId="route-sign-in"
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

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="password">{t('auth.field.password')}</Label>
            <Link
              to="/forgot-password"
              className="text-xs text-muted-foreground underline-offset-4 hover:underline"
            >
              {t('auth.signIn.forgot')}
            </Link>
          </div>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={fieldError('password') !== undefined}
            aria-describedby={fieldError('password') ? 'password-error' : undefined}
            {...form.register('password')}
          />
          {fieldError('password') ? (
            <p id="password-error" className="text-sm text-destructive">
              {fieldError('password')}
            </p>
          ) : null}
        </div>

        {mutation.isError ? (
          <p role="alert" className="text-sm text-destructive" data-testid="sign-in-error">
            {formatAuthError(mutation.error)}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={mutation.isPending} data-testid="sign-in-submit">
          {mutation.isPending ? t('auth.signIn.submitting') : t('auth.signIn.submit')}
        </Button>

        {import.meta.env.DEV ? (
          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={() => {
              seedDevUser();
              navigate(redirectTo, { replace: true });
            }}
            data-testid="seed-dev-user"
          >
            {t('auth.signIn.devSeed')}
          </Button>
        ) : null}
      </form>
    </AuthLayout>
  );
}
