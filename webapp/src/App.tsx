import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AppRoutes } from './router';

/**
 * Top-level app wrapper. Hosts the two long-lived providers the shell and
 * every downstream route rely on:
 *   - QueryClientProvider: TanStack Query cache (API data + AuthStore will
 *     graduate onto it in T120).
 *   - BrowserRouter:       history API routing. Phase 1+ pages mount under
 *     routes declared in `./router.tsx`.
 *
 * The `<ThemeProvider>` lives one level up in `main.tsx` so it wraps the
 * query client too — that way theme changes don't thrash the cache.
 */
export default function App(): JSX.Element {
  const [queryClient] = React.useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: false,
            staleTime: 30_000,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
