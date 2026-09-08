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
 * The fake API is now **opt in**: `npm run dev:mock`, and nothing else.
 *
 * It used to be the default, because for most of this project there was no
 * backend to talk to. Now there is, and a default that quietly serves fixtures
 * is a trap: writes appear to succeed, the figures never move, and nothing on
 * screen says which API answered. That is exactly how it caught somebody out.
 *
 * `--mode mock` rather than an environment variable, because an npm script
 * cannot set one portably — `FOO=bar vite` is a bash-ism that fails on Windows,
 * and mode is Vite's own mechanism for the job.
 *
 * A production build never reaches this: `import.meta.env.DEV` is statically
 * false there, so the dynamic import is dropped from the bundle entirely — the
 * fake backend cannot ship by accident.
 */
async function startMockApiIfAsked(): Promise<void> {
  if (import.meta.env.DEV && import.meta.env.MODE === 'mock') {
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
