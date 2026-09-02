import { HttpResponse, http } from 'msw';

import { accounts, cards } from './fixtures';

/**
 * The API as `types/api.ts` promises it, answered from the prototype's own
 * numbers.
 *
 * Handlers are added as pages need them rather than all at once, so an
 * endpoint here always has a consumer.
 */
export const API_BASE = '/api/v1';

export const handlers = [
  http.get(`${API_BASE}/accounts`, () => HttpResponse.json(accounts)),
  http.get(`${API_BASE}/cards`, () => HttpResponse.json(cards)),
];
