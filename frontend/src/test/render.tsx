import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render as testingLibraryRender } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Renders a feature the way the app does: inside a router and a query client.
 *
 * Retries are off and the cache is per-test, so a failing request fails once
 * and immediately rather than being retried into a timeout, and no test can
 * see another test's data.
 */
function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
}

export interface RenderOptions {
  /** The route the test starts on. */
  readonly route?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/' }: RenderOptions = {}) {
  const queryClient = createTestQueryClient();

  function Providers({ children }: { readonly children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return testingLibraryRender(ui, { wrapper: Providers });
}
