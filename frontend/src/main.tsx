import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element #root is missing from index.html');
}

/**
 * Figures come from the server and change when something is logged, so a stale
 * read is a wrong number rather than an old one. A short freshness window keeps
 * navigation from refetching everything on each click without letting a total
 * linger after the entry that changed it.
 */
const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, refetchOnWindowFocus: false } },
});

/**
 * Until the backend exists, `npm run dev` runs against the same fake API the
 * tests use, so the work can actually be looked at rather than only asserted.
 * Opt out with `VITE_USE_MOCK_API=false` once there is a server on :8085.
 *
 * A production build never reaches this: `import.meta.env.DEV` is statically
 * false there, so the dynamic import is dropped from the bundle entirely — the
 * fake backend cannot ship by accident.
 */
async function startMockApiIfAsked(): Promise<void> {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_API !== 'false') {
    const { startMockApi } = await import('./test/browser');
    await startMockApi();
  }
}

void startMockApiIfAsked().then(() => {
  createRoot(container).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </StrictMode>,
  );
});
