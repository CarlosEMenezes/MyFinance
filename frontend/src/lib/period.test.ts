import { describe, expect, it } from 'vitest';

import { fromIso } from './dates';
import { fromDecimal, format } from './money';
import {
  occurrencesIn,
  periodsPerMonth,
  periodsPerYear,
  plannedAmountIn,
  smoothedMonthlyEquivalent,
} from './period';

const august2026 = { start: fromIso('2026-08-01'), end: fromIso('2026-08-31') };
const wholeOf2026 = { start: fromIso('2026-01-01'), end: fromIso('2026-12-31') };

describe('occurrencesIn — monthly', () => {
  it('counts one occurrence in a single month', () => {
    expect(occurrencesIn('MONTHLY', fromIso('2026-01-01'), august2026)).toBe(1);
  });

  it('counts every month of a year', () => {
    expect(occurrencesIn('MONTHLY', fromIso('2026-01-01'), wholeOf2026)).toBe(12);
  });

  it('clamps a 31st anchor into short months and still counts every month', () => {
    expect(occurrencesIn('MONTHLY', fromIso('2026-01-31'), wholeOf2026)).toBe(12);
  });

  it('places a 31st anchor on the last day of a 30-day month', () => {
    const september = { start: fromIso('2026-09-01'), end: fromIso('2026-09-30') };
    expect(occurrencesIn('MONTHLY', fromIso('2026-01-31'), september)).toBe(1);
  });

  it('does not count occurrences before the anchor, so a new plan is not backdated', () => {
    expect(occurrencesIn('MONTHLY', fromIso('2026-09-15'), august2026)).toBe(0);
  });

  it('counts the anchor month itself when the anchor falls inside the range', () => {
    expect(occurrencesIn('MONTHLY', fromIso('2026-08-12'), august2026)).toBe(1);
  });

  it('excludes an occurrence that falls just outside the range', () => {
    const firstHalf = { start: fromIso('2026-08-01'), end: fromIso('2026-08-11') };
    expect(occurrencesIn('MONTHLY', fromIso('2026-01-12'), firstHalf)).toBe(0);
  });
});

describe('occurrencesIn — weekly and fortnightly', () => {
  it('counts five paydays in a month that holds five, not 52/12', () => {
    // BR-10. Anchored 05-01-2026, stepping by 7, August 2026 holds
    // the 3rd, 10th, 17th, 24th and 31st.
    expect(occurrencesIn('WEEKLY', fromIso('2026-01-05'), august2026)).toBe(5);
  });

  it('counts four in a month that holds four', () => {
    const february = { start: fromIso('2026-02-01'), end: fromIso('2026-02-28') };
    expect(occurrencesIn('WEEKLY', fromIso('2026-01-05'), february)).toBe(4);
  });

  it('steps fortnightly from the anchor rather than halving the weekly count', () => {
    // Anchored 05-01-2026 and stepping by 14, August 2026 holds the 3rd,
    // 17th and 31st — three, not half of the five weekly landings. Alignment
    // to the anchor decides this, so it cannot be derived from the weekly
    // count.
    expect(occurrencesIn('FORTNIGHTLY', fromIso('2026-01-05'), august2026)).toBe(3);
  });

  it('lands twice in a month when the anchor aligns differently', () => {
    // The same month, anchored a week later: the 10th and the 24th.
    expect(occurrencesIn('FORTNIGHTLY', fromIso('2026-01-12'), august2026)).toBe(2);
  });

  it('counts from the anchor when the anchor is inside the range', () => {
    expect(occurrencesIn('WEEKLY', fromIso('2026-08-20'), august2026)).toBe(2);
  });

  it('counts nothing when the anchor is after the range', () => {
    expect(occurrencesIn('WEEKLY', fromIso('2026-09-07'), august2026)).toBe(0);
  });

  it('counts the anchor itself on a single-day range', () => {
    const oneDay = { start: fromIso('2026-08-31'), end: fromIso('2026-08-31') };
    expect(occurrencesIn('WEEKLY', fromIso('2026-01-05'), oneDay)).toBe(1);
    expect(occurrencesIn('WEEKLY', fromIso('2026-01-06'), oneDay)).toBe(0);
  });

  it('is unaffected by a daylight-saving change inside the range', () => {
    // Europe/Dublin moves the clock on 2026-10-25. Counting in milliseconds
    // would drop or double an occurrence here.
    const october = { start: fromIso('2026-10-01'), end: fromIso('2026-10-31') };
    expect(occurrencesIn('WEEKLY', fromIso('2026-01-05'), october)).toBe(4);
  });
});

describe('occurrencesIn — invalid range', () => {
  it('rejects a range that ends before it starts', () => {
    const inverted = { start: fromIso('2026-08-31'), end: fromIso('2026-08-01') };
    expect(() => occurrencesIn('MONTHLY', fromIso('2026-01-01'), inverted)).toThrow(
      /ends before it starts/i,
    );
  });
});

describe('periodsPerMonth — BR-3', () => {
  it('averages weekly and fortnightly over a year, and leaves monthly at one', () => {
    expect(periodsPerMonth('WEEKLY')).toBeCloseTo(52 / 12, 10);
    expect(periodsPerMonth('FORTNIGHTLY')).toBeCloseTo(26 / 12, 10);
    expect(periodsPerMonth('MONTHLY')).toBe(1);
  });
});

describe('periodsPerYear — BR-6', () => {
  it('gives the compounding count the APR conversion needs', () => {
    expect(periodsPerYear('WEEKLY')).toBe(52);
    expect(periodsPerYear('FORTNIGHTLY')).toBe(26);
    expect(periodsPerYear('MONTHLY')).toBe(12);
  });
});

describe('plannedAmountIn — BR-10', () => {
  it('multiplies the per-occurrence amount by how many times it actually lands', () => {
    // €160 a week, in a month holding five of them.
    const planned = plannedAmountIn(
      fromDecimal('160'),
      'WEEKLY',
      fromIso('2026-01-05'),
      august2026,
    );
    expect(format(planned)).toBe('€800.00');
  });

  it('is zero when the plan does not land in the range at all', () => {
    const planned = plannedAmountIn(
      fromDecimal('780'),
      'MONTHLY',
      fromIso('2026-09-15'),
      august2026,
    );
    expect(format(planned)).toBe('€0.00');
  });

  it('matches the per-occurrence amount when it lands exactly once', () => {
    const planned = plannedAmountIn(
      fromDecimal('780'),
      'MONTHLY',
      fromIso('2026-01-01'),
      august2026,
    );
    expect(format(planned)).toBe('€780.00');
  });
});

describe('smoothedMonthlyEquivalent — BR-3, the exception to BR-10', () => {
  it('averages a weekly amount over a month', () => {
    // 160 x 52/12 = 693.33
    expect(format(smoothedMonthlyEquivalent(fromDecimal('160'), 'WEEKLY'))).toBe('€693.33');
  });

  it('averages a fortnightly amount over a month', () => {
    // 100 x 26/12 = 216.67
    expect(format(smoothedMonthlyEquivalent(fromDecimal('100'), 'FORTNIGHTLY'))).toBe('€216.67');
  });

  it('leaves a monthly amount unchanged', () => {
    expect(format(smoothedMonthlyEquivalent(fromDecimal('780'), 'MONTHLY'))).toBe('€780.00');
  });

  it('is the smoothed figure BR-3 wants, not the real-date count BR-10 gives', () => {
    // The same €160 weekly plan: BR-10 says €800 in a five-payday August,
    // BR-3 says €693.33 a month as a commitment figure. Both are correct in
    // their place and must not be unified.
    const realDates = plannedAmountIn(
      fromDecimal('160'),
      'WEEKLY',
      fromIso('2026-01-05'),
      august2026,
    );
    const smoothed = smoothedMonthlyEquivalent(fromDecimal('160'), 'WEEKLY');
    expect(format(realDates)).toBe('€800.00');
    expect(format(smoothed)).toBe('€693.33');
  });
});
