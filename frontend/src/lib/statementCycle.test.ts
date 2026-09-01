import { describe, expect, it } from 'vitest';

import { fromIso, toIso } from './dates';
import { billDateFor } from './statementCycle';

interface CycleCase {
  readonly purchase: string;
  readonly closingDay: number;
  readonly dueDay: number;
  readonly expectedBill: string;
  readonly why: string;
}

/**
 * Table-driven boundary cases (spec §4). The first four are the worked
 * examples in BR-4 itself; the rest are the edges those examples do not reach.
 */
const cases: readonly CycleCase[] = [
  {
    purchase: '2026-08-20',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2026-09-05',
    why: 'BR-4 example: before closing, so it joins this statement',
  },
  {
    purchase: '2026-08-26',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2026-10-05',
    why: 'BR-4 example: after closing, so it waits for the next statement',
  },
  {
    purchase: '2026-08-05',
    closingDay: 10,
    dueDay: 28,
    expectedBill: '2026-08-28',
    why: 'BR-4 example: due day after closing day, so the bill lands the same month',
  },
  {
    purchase: '2026-08-12',
    closingDay: 10,
    dueDay: 28,
    expectedBill: '2026-09-28',
    why: 'BR-4 example: after closing, still same-month due',
  },
  {
    purchase: '2026-08-25',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2026-09-05',
    why: 'on the closing day itself the purchase still joins this statement',
  },
  {
    purchase: '2026-08-01',
    closingDay: 1,
    dueDay: 28,
    expectedBill: '2026-08-28',
    why: 'earliest possible closing day, purchase on it',
  },
  {
    purchase: '2026-08-02',
    closingDay: 1,
    dueDay: 28,
    expectedBill: '2026-09-28',
    why: 'earliest possible closing day, purchase one day late',
  },
  {
    purchase: '2026-08-28',
    closingDay: 28,
    dueDay: 1,
    expectedBill: '2026-09-01',
    why: 'latest possible closing day, due day before it so the bill rolls a month',
  },
  {
    purchase: '2026-08-29',
    closingDay: 28,
    dueDay: 1,
    expectedBill: '2026-10-01',
    why: 'past the latest closing day, so both the statement and the due date roll',
  },
  {
    purchase: '2026-08-31',
    closingDay: 28,
    dueDay: 5,
    expectedBill: '2026-10-05',
    why: 'a 31st purchase in a 31-day month rolls to the next statement',
  },
  {
    purchase: '2026-12-20',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2027-01-05',
    why: 'the due date crosses into the next year',
  },
  {
    purchase: '2026-12-26',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2027-02-05',
    why: 'both the statement and the due date cross into the next year',
  },
  {
    purchase: '2028-02-29',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2028-04-05',
    why: 'a leap day after closing rolls to the March statement',
  },
  {
    purchase: '2028-02-20',
    closingDay: 25,
    dueDay: 5,
    expectedBill: '2028-03-05',
    why: 'a leap-year February before closing bills in March',
  },
  {
    purchase: '2027-02-28',
    closingDay: 28,
    dueDay: 28,
    expectedBill: '2027-03-28',
    why: 'due day equal to closing day always rolls a month',
  },
  {
    purchase: '2027-01-31',
    closingDay: 28,
    dueDay: 15,
    expectedBill: '2027-03-15',
    why: 'a 31st purchase rolling into February still bills on the 15th of March',
  },
];

describe('billDateFor — BR-4', () => {
  it.each(cases)(
    'bills a $purchase purchase on a card closing $closingDay due $dueDay on $expectedBill — $why',
    ({ purchase, closingDay, dueDay, expectedBill }) => {
      const bill = billDateFor(fromIso(purchase), { closingDay, dueDay });
      expect(toIso(bill)).toBe(expectedBill);
    },
  );

  it('never returns the purchase date itself', () => {
    // BR-4: the planned-expense date is the bill date, never the purchase date.
    const purchase = fromIso('2026-08-20');
    expect(toIso(billDateFor(purchase, { closingDay: 25, dueDay: 5 }))).not.toBe(toIso(purchase));
  });

  it('always lands on the due day, because 1-28 exists in every month', () => {
    const bill = billDateFor(fromIso('2027-01-31'), { closingDay: 28, dueDay: 28 });
    expect(toIso(bill)).toBe('2027-03-28');
  });
});

describe('billDateFor — invalid cycle days', () => {
  const purchase = fromIso('2026-08-20');

  it.each([0, 29, 30, 31, -1])('rejects a closing day of %i', (closingDay) => {
    expect(() => billDateFor(purchase, { closingDay, dueDay: 5 })).toThrow(/closingDay/i);
  });

  it.each([0, 29, 31])('rejects a due day of %i', (dueDay) => {
    expect(() => billDateFor(purchase, { closingDay: 25, dueDay })).toThrow(/dueDay/i);
  });

  it('rejects a fractional cycle day', () => {
    expect(() => billDateFor(purchase, { closingDay: 25.5, dueDay: 5 })).toThrow(/closingDay/i);
  });
});
