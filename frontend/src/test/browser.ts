import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

/**
 * The same fake backend the tests use, in the browser.
 *
 * The frontend is being finished before the backend, so without this every
 * page renders its error state and the work cannot be looked at. It answers
 * from `handlers.ts` — the one place the contract in `types/api.ts` is
 * implemented — so what a browser shows is what the tests assert.
 *
 * Started only when `VITE_USE_MOCK_API` is on, and the module is dynamically
 * imported so it is never bundled into a build without it.
 */
export async function startMockApi(): Promise<void> {
  await setupWorker(...handlers).start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  });
}
