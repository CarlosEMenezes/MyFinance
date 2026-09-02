import { HttpResponse, http } from 'msw';

import { accounts, cards, categoryList } from './fixtures';
import type { Category } from '../types/api';

/**
 * The API as `types/api.ts` promises it, answered from the prototype's own
 * numbers.
 *
 * Writes are remembered. A handler that accepted a PATCH and then served the
 * original row again would make every optimistic update look like a bug — the
 * value would flash to the new figure and revert on refetch — so the fake
 * backend persists for the life of a test and is reset between them.
 */
export const API_BASE = '/api/v1';

let categories: Category[] = [...categoryList.categories];

/** Called between tests so no test can see another's writes. */
export function resetApiState(): void {
  categories = [...categoryList.categories];
}

export const handlers = [
  http.get(`${API_BASE}/accounts`, () => HttpResponse.json(accounts)),
  http.get(`${API_BASE}/cards`, () => HttpResponse.json(cards)),

  http.get(`${API_BASE}/categories`, () =>
    HttpResponse.json({ period: categoryList.period, categories }),
  ),

  http.patch(`${API_BASE}/categories/:id`, async ({ params, request }) => {
    const changes = (await request.json()) as Partial<Category>;
    const index = categories.findIndex((category) => category.id === params.id);
    if (index < 0) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'No such category', status: 404, detail: '' },
        { status: 404 },
      );
    }
    const updated = { ...categories[index], ...changes } as Category;
    categories = categories.map((category, i) => (i === index ? updated : category));
    return HttpResponse.json(updated);
  }),
];
