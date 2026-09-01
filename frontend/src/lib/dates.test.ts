import { describe, expect, it } from 'vitest';

import {
  addDays,
  addMonths,
  compare,
  daysBetween,
  daysInMonth,
  dayOf,
  format,
  fromIso,
  fromParts,
  isAfter,
  isBefore,
  isSameOrAfter,
  isSameOrBefore,
  monthOf,
  parse,
  toIso,
  today,
  yearOf,
} from './dates';

describe('fromIso', () => {
  it('accepts an ISO calendar date', () => {
    expect(toIso(fromIso('2026-08-31'))).toBe('2026-08-31');
  });

  it('rejects anything that is not exactly YYYY-MM-DD', () => {
    expect(() => fromIso('31-08-2026')).toThrow(/not a valid ISO date/i);
    expect(() => fromIso('2026-8-31')).toThrow(/not a valid ISO date/i);
    expect(() => fromIso('2026-08-31T00:00:00Z')).toThrow(/not a valid ISO date/i);
  });

  it('rejects a date that does not exist', () => {
    expect(() => fromIso('2026-02-31')).toThrow(/does not exist/i);
    expect(() => fromIso('2026-13-01')).toThrow(/does not exist/i);
    expect(() => fromIso('2026-00-10')).toThrow(/does not exist/i);
    expect(() => fromIso('2026-01-00')).toThrow(/does not exist/i);
  });
});

describe('fromParts', () => {
  it('builds a date from year, month and day', () => {
    expect(toIso(fromParts(2026, 8, 31))).toBe('2026-08-31');
  });

  it('accepts 29 February in a leap year', () => {
    expect(toIso(fromParts(2028, 2, 29))).toBe('2028-02-29');
  });

  it('rejects 29 February in a common year', () => {
    expect(() => fromParts(2027, 2, 29)).toThrow(/does not exist/i);
  });

  it('rejects fractional parts, which would otherwise assemble a nonsense string', () => {
    expect(() => fromParts(2026, 8, 31.5)).toThrow(/does not exist/i);
    expect(() => fromParts(2026.5, 8, 31)).toThrow(/does not exist/i);
  });

  it('rejects a century year that is not a leap year', () => {
    expect(() => fromParts(2100, 2, 29)).toThrow(/does not exist/i);
    expect(toIso(fromParts(2000, 2, 29))).toBe('2000-02-29');
  });
});

describe('accessors', () => {
  it('exposes year, month and day as calendar numbers, with month starting at 1', () => {
    const date = fromIso('2026-08-31');
    expect(yearOf(date)).toBe(2026);
    expect(monthOf(date)).toBe(8);
    expect(dayOf(date)).toBe(31);
  });
});

describe('parse', () => {
  it('reads the day-first format the app defaults to', () => {
    expect(toIso(parse('31-08-2026', 'DD-MM-YYYY'))).toBe('2026-08-31');
  });

  it('reads the month-first format', () => {
    expect(toIso(parse('08-31-2026', 'MM-DD-YYYY'))).toBe('2026-08-31');
  });

  it('reads the ISO format', () => {
    expect(toIso(parse('2026-08-31', 'YYYY-MM-DD'))).toBe('2026-08-31');
  });

  it('rejects a malformed value', () => {
    expect(() => parse('31/08/2026', 'DD-MM-YYYY')).toThrow(/does not match/i);
    expect(() => parse('', 'DD-MM-YYYY')).toThrow(/does not match/i);
  });

  it('rejects a well-formed value that is not a real date', () => {
    expect(() => parse('31-02-2026', 'DD-MM-YYYY')).toThrow(/does not exist/i);
  });
});

describe('format', () => {
  const date = fromIso('2026-08-05');

  it('renders each format the settings page offers, zero-padded', () => {
    expect(format(date, 'DD-MM-YYYY')).toBe('05-08-2026');
    expect(format(date, 'MM-DD-YYYY')).toBe('08-05-2026');
    expect(format(date, 'YYYY-MM-DD')).toBe('2026-08-05');
  });

  it('defaults to the day-first format', () => {
    expect(format(date)).toBe('05-08-2026');
  });

  it('round-trips through parse', () => {
    expect(toIso(parse(format(date, 'MM-DD-YYYY'), 'MM-DD-YYYY'))).toBe('2026-08-05');
  });
});

describe('daysInMonth', () => {
  it('knows the length of each month', () => {
    expect(daysInMonth(2026, 1)).toBe(31);
    expect(daysInMonth(2026, 4)).toBe(30);
    expect(daysInMonth(2026, 2)).toBe(28);
  });

  it('knows February in a leap year', () => {
    expect(daysInMonth(2028, 2)).toBe(29);
  });
});

describe('addDays', () => {
  it('moves forward within a month', () => {
    expect(toIso(addDays(fromIso('2026-08-05'), 7))).toBe('2026-08-12');
  });

  it('crosses a month boundary', () => {
    expect(toIso(addDays(fromIso('2026-08-31'), 1))).toBe('2026-09-01');
  });

  it('crosses a year boundary', () => {
    expect(toIso(addDays(fromIso('2026-12-31'), 1))).toBe('2027-01-01');
  });

  it('moves backwards with a negative count', () => {
    expect(toIso(addDays(fromIso('2026-09-01'), -1))).toBe('2026-08-31');
  });

  it('crosses a leap day', () => {
    expect(toIso(addDays(fromIso('2028-02-28'), 1))).toBe('2028-02-29');
    expect(toIso(addDays(fromIso('2027-02-28'), 1))).toBe('2027-03-01');
  });
});

describe('addMonths', () => {
  it('keeps the day of the month when it exists', () => {
    expect(toIso(addMonths(fromIso('2026-08-15'), 1))).toBe('2026-09-15');
  });

  it('clamps to the last day when the target month is shorter', () => {
    // BR-10: a 31st anchor lands on the 30th in a 30-day month.
    expect(toIso(addMonths(fromIso('2026-08-31'), 1))).toBe('2026-09-30');
    expect(toIso(addMonths(fromIso('2026-01-31'), 1))).toBe('2026-02-28');
    expect(toIso(addMonths(fromIso('2028-01-31'), 1))).toBe('2028-02-29');
  });

  it('crosses a year boundary', () => {
    expect(toIso(addMonths(fromIso('2026-11-30'), 2))).toBe('2027-01-30');
  });

  it('moves backwards with a negative count', () => {
    expect(toIso(addMonths(fromIso('2026-03-31'), -1))).toBe('2026-02-28');
  });
});

describe('daysBetween', () => {
  it('counts whole days from the first date to the second', () => {
    expect(daysBetween(fromIso('2026-08-31'), fromIso('2026-09-05'))).toBe(5);
  });

  it('is negative when the second date is earlier', () => {
    expect(daysBetween(fromIso('2026-09-05'), fromIso('2026-08-31'))).toBe(-5);
  });

  it('is zero for the same date', () => {
    expect(daysBetween(fromIso('2026-08-31'), fromIso('2026-08-31'))).toBe(0);
  });

  it('is unaffected by daylight saving, which a naive hour count would break', () => {
    // Europe/Dublin moves the clock on 2026-10-25; a 24h-based difference
    // would return 30.958… and truncate to 30.
    expect(daysBetween(fromIso('2026-10-01'), fromIso('2026-11-01'))).toBe(31);
  });
});

describe('comparison', () => {
  const earlier = fromIso('2026-08-31');
  const later = fromIso('2026-09-01');

  it('orders two dates', () => {
    expect(compare(earlier, later)).toBeLessThan(0);
    expect(compare(later, earlier)).toBeGreaterThan(0);
    expect(compare(earlier, earlier)).toBe(0);
  });

  it('reports before and after', () => {
    expect(isBefore(earlier, later)).toBe(true);
    expect(isAfter(later, earlier)).toBe(true);
    expect(isBefore(earlier, earlier)).toBe(false);
    expect(isAfter(earlier, earlier)).toBe(false);
  });

  it('reports inclusive comparisons, which range checks need', () => {
    expect(isSameOrBefore(earlier, later)).toBe(true);
    expect(isSameOrBefore(earlier, earlier)).toBe(true);
    expect(isSameOrBefore(later, earlier)).toBe(false);
    expect(isSameOrAfter(later, earlier)).toBe(true);
    expect(isSameOrAfter(earlier, earlier)).toBe(true);
    expect(isSameOrAfter(earlier, later)).toBe(false);
  });

  it('sorts naturally as strings, so no comparator is needed to order a list', () => {
    const unsorted = [fromIso('2026-12-01'), fromIso('2026-01-31'), fromIso('2026-08-05')];
    expect([...unsorted].sort()).toEqual(['2026-01-31', '2026-08-05', '2026-12-01']);
  });
});

describe('today', () => {
  it('reads the calendar date from an injected instant, not the wall clock', () => {
    expect(toIso(today(new Date(2026, 7, 31, 23, 30)))).toBe('2026-08-31');
  });

  it('uses local calendar parts, so a late-evening instant is not pushed into tomorrow', () => {
    // new Date(...).toISOString() would report 2026-08-31 for this instant in
    // any timezone behind UTC, and 2026-09-01 in any timezone ahead of it.
    expect(toIso(today(new Date(2026, 8, 1, 0, 30)))).toBe('2026-09-01');
  });
});
