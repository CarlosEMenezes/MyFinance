import { HttpResponse, http } from 'msw';

import { dashboard } from './dashboard.fixture';
import { accounts, cards, categoryList } from './fixtures';
import { goals } from './goals.fixture';
import type { Category, Dashboard, PlanRow } from '../types/api';

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
let currentDashboard: Dashboard = dashboard;

/** Called between tests so no test can see another's writes. */
export function resetApiState(): void {
  categories = [...categoryList.categories];
  currentDashboard = dashboard;
}

/**
 * A plan edit changes the category *and* the dashboard row derived from it.
 * A backend that updated one and not the other would make an optimistic
 * update revert on refetch, which looks exactly like a rollback bug.
 */
function applyPlanChange(categoryId: string, plannedAmount: number): void {
  const update = (rows: readonly PlanRow[]): PlanRow[] =>
    rows.map((row) =>
      row.categoryId === categoryId
        ? {
            ...row,
            perOccurrence: plannedAmount,
            planned: plannedAmount * row.occurrencesInPeriod,
            variance: row.real - plannedAmount * row.occurrencesInPeriod,
          }
        : row,
    );

  currentDashboard = {
    ...currentDashboard,
    earnings: update(currentDashboard.earnings),
    expenses: update(currentDashboard.expenses),
  };
}

export const handlers = [
  http.get(`${API_BASE}/accounts`, () => HttpResponse.json(accounts)),
  http.get(`${API_BASE}/cards`, () => HttpResponse.json(cards)),
  http.get(`${API_BASE}/dashboard`, () => HttpResponse.json(currentDashboard)),
  http.get(`${API_BASE}/goals`, () => HttpResponse.json(goals)),

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
    if (typeof changes.plannedAmount === 'number') {
      applyPlanChange(updated.id, changes.plannedAmount);
    }
    return HttpResponse.json(updated);
  }),
];
