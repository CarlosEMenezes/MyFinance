import type { Account, Card, CategoryList } from '../types/api';

/**
 * Seed data taken from the design prototype's own `state` block
 * (`Budget Tracker.dc.html`, lines 1236–1294).
 *
 * Using the numbers the design was drawn with means a page assembled from
 * these fixtures can be compared against the prototype directly: a figure that
 * differs is a bug, not a different dataset.
 *
 * Money is in minor units and dates are ISO, matching `types/api.ts`.
 */

export const accounts: readonly Account[] = [
  {
    id: 'wallet',
    name: 'Wallet',
    kind: 'CASH',
    balance: 12000,
    currency: 'EUR',
    includeInTotals: true,
    note: 'last counted 29-08-2026',
    pockets: [],
    cardNames: [],
  },
  {
    id: 'revolut',
    name: 'Revolut Current',
    kind: 'BANK',
    balance: 84230,
    currency: 'EUR',
    includeInTotals: true,
    note: 'wages land here on the 5th',
    pockets: [],
    cardNames: ['Visa ·· 4417', 'Revolut debit'],
  },
  {
    id: 'aib-savings',
    name: 'AIB Savings',
    kind: 'SAVINGS',
    balance: 145000,
    currency: 'EUR',
    includeInTotals: true,
    note: 'ring-fenced for goals',
    pockets: [
      { id: 'p-macbook', accountId: 'aib-savings', name: 'MacBook Air M4', balance: 41000 },
      { id: 'p-emergency', accountId: 'aib-savings', name: 'Emergency fund', balance: 64000 },
      { id: 'p-interrail', accountId: 'aib-savings', name: 'Interrail summer', balance: 8000 },
    ],
    cardNames: ['AIB debit'],
  },
];

export const cards: readonly Card[] = [
  {
    id: 'visa',
    name: 'Visa ·· 4417',
    kind: 'CREDIT',
    accountId: 'revolut',
    settlesFrom: 'Revolut Current',
    creditLimit: 200000,
    currentBalance: 38640,
    closingDay: 25,
    dueDay: 5,
    cycle: {
      nextBillDate: '2026-09-05',
      billDateOnClosingDay: '2026-09-05',
      billDateAfterClosingDay: '2026-10-05',
    },
  },
  {
    id: 'revolut-debit',
    name: 'Revolut debit',
    kind: 'DEBIT',
    accountId: 'revolut',
    settlesFrom: 'Revolut Current',
    creditLimit: null,
    currentBalance: null,
    closingDay: null,
    dueDay: null,
    cycle: null,
  },
  {
    id: 'aib-debit',
    name: 'AIB debit',
    kind: 'DEBIT',
    accountId: 'aib-savings',
    settlesFrom: 'AIB Savings',
    creditLimit: null,
    currentBalance: null,
    closingDay: null,
    dueDay: null,
    cycle: null,
  },
];

/**
 * Categories with the prototype's own planned amounts and anchors. The window
 * is August 2026, which is the month the prototype hard-codes as "today" — and
 * the month that holds five weekly paydays from a 05-01-2026 anchor.
 */
export const categoryList: CategoryList = {
  period: { kind: 'MONTH', from: '2026-08-01', to: '2026-08-31', label: 'August 2026' },
  categories: [
    {
      id: 'rent',
      type: 'EXPENSE',
      name: 'Rent',
      group: 'Fixed',
      plannedAmount: 78000,
      plannedFrequency: 'MONTHLY',
      anchorDate: '2026-01-01',
      archived: false,
    },
    {
      id: 'groceries',
      type: 'EXPENSE',
      name: 'Groceries',
      group: 'Variable',
      plannedAmount: 6000,
      plannedFrequency: 'WEEKLY',
      anchorDate: '2026-01-05',
      archived: false,
    },
    {
      id: 'retired',
      type: 'EXPENSE',
      name: 'Old subscription',
      group: 'Fixed',
      plannedAmount: 999,
      plannedFrequency: 'MONTHLY',
      anchorDate: '2026-01-03',
      archived: true,
    },
    {
      id: 'cafe',
      type: 'EARNING',
      name: 'Part-time café',
      group: 'Employment',
      plannedAmount: 16000,
      plannedFrequency: 'WEEKLY',
      anchorDate: '2026-01-05',
      archived: false,
    },
  ],
};
