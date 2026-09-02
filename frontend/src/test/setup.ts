import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { resetApiState } from './handlers';
import { server } from './server';

// `error` rather than `warn`: a request no handler answers is a page asking for
// something the contract does not promise, which is a bug worth failing on.
beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetApiState();
});

afterAll(() => {
  server.close();
});
