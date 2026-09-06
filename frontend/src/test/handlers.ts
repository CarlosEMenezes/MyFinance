import { HttpResponse, http } from 'msw';

import { dashboard } from './dashboard.fixture';
import { accounts, cards, categoryList } from './fixtures';
import { goals } from './goals.fixture';
import { notifications, notificationSettings } from './notifications.fixture';
import { fxRates, user } from './settings.fixture';
import type {
  Account,
  Card,
  Category,
  CreateAccountRequest,
  CreateCardRequest,
  CreateCategoryRequest,
  CreatePocketRequest,
  CreateTransactionRequest,
  Dashboard,
  MarkNotificationsReadRequest,
  Notification,
  NotificationSettings,
  PlanRow,
  UpdateNotificationSettingsRequest,
  UpdateUserRequest,
  User,
} from '../types/api';

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

let accountList: Account[] = [...accounts];
let cardList: Card[] = [...cards];
let categories: Category[] = [...categoryList.categories];
let currentDashboard: Dashboard = dashboard;
let notificationQueue: Notification[] = [...notifications];
let settings: NotificationSettings = notificationSettings;
let currentUser: User = user;
/**
 * ADR-11: a real session is an HttpOnly cookie the tests cannot read, so the
 * fake backend keeps a flag instead. What matters for the frontend's tests is
 * whether /users/me answers 401, and that is exactly what this decides.
 */
let signedIn = true;

/** Lets a test start from a signed-out browser. */
export function signOutInTests(): void {
  signedIn = false;
}
/** Only the count is needed: the ids a fake backend hands out must not repeat. */
let loggedTransactions: string[] = [];

/** Called between tests so no test can see another's writes. */
export function resetApiState(): void {
  accountList = [...accounts];
  cardList = [...cards];
  categories = [...categoryList.categories];
  currentDashboard = dashboard;
  notificationQueue = [...notifications];
  settings = notificationSettings;
  currentUser = user;
  loggedTransactions = [];
  signedIn = true;
}

function newTransactionId(): string {
  const id = `t-${String(loggedTransactions.length + 1)}`;
  loggedTransactions.push(id);
  return id;
}

const problem = (status: number, title: string) =>
  HttpResponse.json({ type: 'about:blank', title, status, detail: '' }, { status });

/** BR-12 persists only `readAt`, so that is the only field a write touches. */
const READ_AT = '2026-08-31T10:00:00+01:00';

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
  http.get(`${API_BASE}/accounts`, () => HttpResponse.json(accountList)),
  http.get(`${API_BASE}/cards`, () => HttpResponse.json(cardList)),

  http.post(`${API_BASE}/accounts`, async ({ request }) => {
    const body = (await request.json()) as CreateAccountRequest;
    const created: Account = {
      id: `a-${String(accountList.length + 1)}`,
      note: null,
      pockets: [],
      cardNames: [],
      ...body,
    };
    accountList = [...accountList, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  http.post(`${API_BASE}/accounts/:id/pockets`, async ({ params, request }) => {
    const body = (await request.json()) as CreatePocketRequest;
    const parent = accountList.find((account) => account.id === params.id);
    if (parent === undefined) {
      return problem(404, 'No such account');
    }
    // BR-13: the pocket is already inside the parent, so the parent's balance
    // does not move. A fake backend that added it would teach the UI to
    // double-count.
    const pocket = {
      id: `p-${parent.id}-${String(parent.pockets.length + 1)}`,
      accountId: parent.id,
      ...body,
    };
    const updated = { ...parent, pockets: [...parent.pockets, pocket] };
    accountList = accountList.map((account) => (account.id === parent.id ? updated : account));
    return HttpResponse.json(updated, { status: 201 });
  }),

  http.post(`${API_BASE}/cards`, async ({ request }) => {
    const body = (await request.json()) as CreateCardRequest;
    const settlesFrom = accountList.find((account) => account.id === body.accountId)?.name ?? '';
    const created: Card =
      body.kind === 'CREDIT'
        ? {
            id: `c-${String(cardList.length + 1)}`,
            name: body.name,
            kind: 'CREDIT',
            accountId: body.accountId,
            settlesFrom,
            creditLimit: body.creditLimit,
            currentBalance: 0,
            closingDay: body.closingDay,
            dueDay: body.dueDay,
            // BR-4 is the server's: the cycle dates come back computed.
            cycle: {
              nextBillDate: '2026-09-05',
              billDateOnClosingDay: '2026-10-05',
              billDateAfterClosingDay: '2026-11-05',
            },
          }
        : {
            id: `c-${String(cardList.length + 1)}`,
            name: body.name,
            kind: 'DEBIT',
            accountId: body.accountId,
            settlesFrom,
            creditLimit: null,
            currentBalance: null,
            closingDay: null,
            dueDay: null,
            cycle: null,
          };
    cardList = [...cardList, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  http.post(`${API_BASE}/categories`, async ({ request }) => {
    const body = (await request.json()) as CreateCategoryRequest;
    const created: Category = {
      id: `cat-${String(categories.length + 1)}`,
      archived: false,
      ...body,
    };
    categories = [...categories, created];
    return HttpResponse.json(created, { status: 201 });
  }),
  http.get(`${API_BASE}/dashboard`, () => HttpResponse.json(currentDashboard)),
  http.get(`${API_BASE}/goals`, () => HttpResponse.json(goals)),
  http.get(`${API_BASE}/users/me`, () =>
    signedIn ? HttpResponse.json(currentUser) : problem(401, 'Not signed in'),
  ),

  http.post(`${API_BASE}/auth/register`, async ({ request }) => {
    const body = (await request.json()) as { email: string; name: string };
    currentUser = { ...currentUser, name: body.name };
    signedIn = true;
    return HttpResponse.json(currentUser, { status: 201 });
  }),

  http.post(`${API_BASE}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    // One wrong password to test against, and the same message either way.
    if (body.password === 'wrong-password') {
      return problem(401, 'That email address and password do not match');
    }
    signedIn = true;
    return HttpResponse.json(currentUser);
  }),

  http.post(`${API_BASE}/auth/logout`, () => {
    signedIn = false;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${API_BASE}/transactions`, async ({ request }) => {
    const body = (await request.json()) as CreateTransactionRequest;
    // BR-8 belongs to the server: the rate and the converted amount come back
    // from it, and the frontend renders what it is given.
    return HttpResponse.json(
      {
        id: newTransactionId(),
        ...body,
        amountInDefaultCurrency: body.amount,
        fxRate: 1,
        note: null,
        instalmentPlanId: null,
        loanId: null,
        plannedExpenseDate: null,
      },
      { status: 201 },
    );
  }),
  http.get(`${API_BASE}/fx/rates`, () => HttpResponse.json(fxRates)),

  http.patch(`${API_BASE}/users/me`, async ({ request }) => {
    const changes = (await request.json()) as UpdateUserRequest;
    currentUser = { ...currentUser, ...changes };
    return HttpResponse.json(currentUser);
  }),

  http.get(`${API_BASE}/notifications`, () => HttpResponse.json(notificationQueue)),
  http.get(`${API_BASE}/notifications/settings`, () => HttpResponse.json(settings)),

  http.patch(`${API_BASE}/notifications/read`, async ({ request }) => {
    const { keys, read } = (await request.json()) as MarkNotificationsReadRequest;
    notificationQueue = notificationQueue.map((item) =>
      keys.includes(item.key) ? { ...item, readAt: read ? READ_AT : null } : item,
    );
    return HttpResponse.json(notificationQueue.filter((item) => keys.includes(item.key)));
  }),

  http.patch(`${API_BASE}/notifications/settings`, async ({ request }) => {
    const changes = (await request.json()) as UpdateNotificationSettingsRequest;
    settings = {
      leadDays: changes.leadDays ?? settings.leadDays,
      channels: changes.channels ?? settings.channels,
    };
    return HttpResponse.json(settings);
  }),

  http.get(`${API_BASE}/categories`, () =>
    HttpResponse.json({ period: categoryList.period, categories }),
  ),

  http.patch(`${API_BASE}/categories/:id`, async ({ params, request }) => {
    const changes = (await request.json()) as Partial<Category>;
    const index = categories.findIndex((category) => category.id === params.id);
    if (index < 0) {
      return problem(404, 'No such category');
    }
    const updated = { ...categories[index], ...changes } as Category;
    categories = categories.map((category, i) => (i === index ? updated : category));
    if (typeof changes.plannedAmount === 'number') {
      applyPlanChange(updated.id, changes.plannedAmount);
    }
    return HttpResponse.json(updated);
  }),
];
