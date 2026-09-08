import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

/**
 * The same fake backend the tests use, in the browser.
 *
 * Built when the frontend was finished before the backend, so that the pages
 * could be looked at rather than only asserted. It answers from
 * `handlers.ts` — the one place the contract in `types/api.ts` is
 * implemented — so what a browser shows is what the tests assert.
 *
 * Now that the backend exists this is a deliberate choice rather than the
 * default: started only under `npm run dev:mock`, and dynamically imported so
 * it is never bundled into a build without it. Useful for working on a page
 * with no database running, and for reproducing exactly what a test sees.
 */
export async function startMockApi(): Promise<void> {
  await setupWorker(...handlers).start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  });
}
