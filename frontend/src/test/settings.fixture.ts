import type { FxRates, User } from '../types/api';

/** The prototype's own profile and preferences. */
export const user: User = {
  id: 'u-1',
  name: 'Carlos Eduardo',
  age: 24,
  role: 'Freelance designer',
  country: 'Ireland',
  payCycle: 'IRREGULAR',
  defaultCurrency: 'EUR',
  dateFormat: 'DD-MM-YYYY',
  weekStart: 'MONDAY',
  preferences: {
    autoConvertForeignAmounts: true,
    roundGoalContributionsUp: true,
    carryUnspentBudget: false,
  },
};

/**
 * BR-8: rates come from a live provider and the app shows when they were last
 * pulled. Units of each currency per one unit of `base`, in the direction the
 * provider sends them — the screen never inverts a rate to phrase it more
 * conveniently.
 */
export const fxRates: FxRates = {
  base: 'EUR',
  rates: { EUR: 1, USD: 1.0858, GBP: 0.8422, BRL: 5.9134 },
  fetchedAt: '2026-08-31T08:12:00+01:00',
};
