import type { Notification, NotificationSettings } from '../types/api';

/**
 * BR-12's derived queue, as the prototype computes it against a "today" of
 * 31-08-2026.
 *
 * `daysUntilDue` is server-derived, like every other computed field: the
 * queue is assembled from card bills, loans, instalments, direct debits and
 * subscriptions, and only `readAt` is persisted. The page filters and counts,
 * it never works out when something falls due.
 *
 * Sorted ascending by days remaining, which is the order BR-12 specifies and
 * the order the server sends. The gym at twelve days sits outside the widest
 * lead time (10) on purpose, so the filter has something to exclude.
 */
export const notifications: readonly Notification[] = [
  {
    key: 'rent',
    label: 'Rent',
    detail: 'Revolut Current · standing order',
    dueDate: '2026-09-01',
    daysUntilDue: 1,
    amount: 78000,
    sourceType: 'DIRECT_DEBIT',
    readAt: null,
  },
  {
    key: 'subs',
    label: 'Spotify + iCloud',
    detail: 'card · subscriptions',
    dueDate: '2026-09-03',
    daysUntilDue: 3,
    amount: 1498,
    sourceType: 'SUBSCRIPTION',
    readAt: null,
  },
  {
    key: 'card-visa-4417',
    label: 'Visa ·· 4417 bill',
    detail: 'closed day 25 · settles from Revolut Current',
    dueDate: '2026-09-05',
    daysUntilDue: 5,
    amount: 38640,
    sourceType: 'CARD_BILL',
    readAt: null,
  },
  {
    key: 'inst-laptop',
    label: 'Laptop repair — PC World instalment',
    detail: '4 of 6 left',
    dueDate: '2026-09-05',
    daysUntilDue: 5,
    amount: 7150,
    sourceType: 'INSTALMENT',
    readAt: null,
  },
  {
    key: 'inst-flight',
    label: 'Flight — Dublin–Porto instalment',
    detail: '2 of 3 left',
    dueDate: '2026-09-05',
    daysUntilDue: 5,
    amount: 6134,
    sourceType: 'INSTALMENT',
    readAt: null,
  },
  {
    key: 'loan-credit-union',
    label: 'Credit union loan repayment',
    detail: '19 of 24 left',
    dueDate: '2026-09-06',
    daysUntilDue: 6,
    amount: 11840,
    sourceType: 'LOAN',
    // Already read, so a fresh page has both states on it.
    readAt: '2026-08-30T09:12:00+01:00',
  },
  {
    key: 'gym',
    label: 'Gym',
    detail: 'Revolut Current · direct debit',
    dueDate: '2026-09-12',
    daysUntilDue: 12,
    amount: 2900,
    sourceType: 'DIRECT_DEBIT',
    readAt: null,
  },
];

export const notificationSettings: NotificationSettings = {
  leadDays: [10, 5, 2],
  channels: { push: true, email: false, weeklySummary: true },
};
