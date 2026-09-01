/**
 * Turning a recurring plan into a figure for a period.
 *
 * **BR-10 is the reference.** {@link occurrencesIn} and {@link plannedAmountIn}
 * count **real dates**, and that is the real cost: a month holding five
 * paydays plans five. The user said "€160 each week", so five weeks is €800.
 * Every plan figure on every screen comes from here unless there is a stated
 * reason otherwise.
 *
 * {@link periodsPerMonth} and {@link smoothedMonthlyEquivalent} use the
 * averaged 52/12 and 26/12 instead. That average is **not** a real cost and
 * must not stand in for one. It has exactly two sanctioned uses:
 *
 * 1. The derived commitment rows of BR-3 — "Loan repayments" and "Card
 *    instalments" — which are a smoothed obligation, not a schedule.
 * 2. The per-month equivalent the plan summary states alongside the real
 *    figure (BR-10).
 *
 * Anywhere else, reach for {@link plannedAmountIn}. The two disagree by
 * design and must never be unified.
 */

import {
  type CalendarDate,
  addDays,
  addMonths,
  dayOf,
  daysBetween,
  daysInMonth,
  fromParts,
  isAfter,
  isSameOrAfter,
  isSameOrBefore,
  monthOf,
  yearOf,
} from './dates';
import { type Money, multiply } from './money';

/** How often a planned amount recurs (spec §2, `plannedFrequency`). */
export type Frequency = 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY';

/** An inclusive span of calendar dates. */
export interface DateRange {
  readonly start: CalendarDate;
  readonly end: CalendarDate;
}

const MONTHS_PER_YEAR = 12;

const PERIODS_PER_YEAR: Record<Frequency, number> = {
  WEEKLY: 52,
  FORTNIGHTLY: 26,
  MONTHLY: MONTHS_PER_YEAR,
};

const DAYS_PER_STEP: Record<Exclude<Frequency, 'MONTHLY'>, number> = {
  WEEKLY: 7,
  FORTNIGHTLY: 14,
};

/** BR-6: the compounding count an APR conversion needs. */
export function periodsPerYear(frequency: Frequency): number {
  return PERIODS_PER_YEAR[frequency];
}

/** BR-3: weekly = 52/12, fortnightly = 26/12, monthly = 1. */
export function periodsPerMonth(frequency: Frequency): number {
  return PERIODS_PER_YEAR[frequency] / MONTHS_PER_YEAR;
}

function assertOrderedRange(range: DateRange): void {
  if (isAfter(range.start, range.end)) {
    throw new RangeError(`The range ${range.start}..${range.end} ends before it starts`);
  }
}

/**
 * Counts monthly occurrences by walking the months the range touches and
 * placing the anchor's day in each, clamped to that month's length — so a
 * plan anchored on the 31st still lands once in a 30-day month.
 */
function countMonthlyOccurrences(anchor: CalendarDate, range: DateRange): number {
  const anchorDay = dayOf(anchor);
  let cursor = fromParts(yearOf(range.start), monthOf(range.start), 1);
  let count = 0;

  while (isSameOrBefore(cursor, range.end)) {
    const year = yearOf(cursor);
    const month = monthOf(cursor);
    const occurrence = fromParts(year, month, Math.min(anchorDay, daysInMonth(year, month)));

    if (
      isSameOrAfter(occurrence, range.start) &&
      isSameOrBefore(occurrence, range.end) &&
      isSameOrAfter(occurrence, anchor)
    ) {
      count += 1;
    }
    cursor = addMonths(cursor, 1);
  }

  return count;
}

/**
 * Counts weekly or fortnightly occurrences by stepping in whole days from the
 * anchor. Stepping in days rather than milliseconds is what makes this
 * immune to daylight saving.
 */
function countSteppedOccurrences(
  anchor: CalendarDate,
  range: DateRange,
  stepInDays: number,
): number {
  const daysUntilStart = daysBetween(anchor, range.start);
  let occurrence =
    daysUntilStart > 0
      ? addDays(anchor, Math.ceil(daysUntilStart / stepInDays) * stepInDays)
      : anchor;
  let count = 0;

  while (isSameOrBefore(occurrence, range.end)) {
    count += 1;
    occurrence = addDays(occurrence, stepInDays);
  }

  return count;
}

/**
 * BR-10 — how many times a recurrence actually falls inside a range.
 *
 * Occurrences before the anchor are not counted, so adding a plan today does
 * not retroactively claim the earlier weeks of the period.
 */
export function occurrencesIn(
  frequency: Frequency,
  anchor: CalendarDate,
  range: DateRange,
): number {
  assertOrderedRange(range);

  if (frequency === 'MONTHLY') {
    return countMonthlyOccurrences(anchor, range);
  }
  return countSteppedOccurrences(anchor, range, DAYS_PER_STEP[frequency]);
}

/** BR-10 — the per-occurrence amount times how many times it lands. */
export function plannedAmountIn(
  perOccurrence: Money,
  frequency: Frequency,
  anchor: CalendarDate,
  range: DateRange,
): Money {
  return multiply(perOccurrence, occurrencesIn(frequency, anchor, range));
}

/**
 * BR-3, BR-10 — the **smoothed** per-month figure. Not a real cost.
 *
 * Named for what it is so that a call site reading `smoothedMonthlyEquivalent`
 * cannot be mistaken for the real figure. It will not agree with
 * {@link plannedAmountIn} in any particular month, which is intended: an
 * average of a weekly plan is €693.33 where five real paydays are €800.
 *
 * Use it only for the BR-3 derived commitment rows and for the per-month
 * equivalent shown beside the real figure. For anything a user reads as
 * "what this period costs", use {@link plannedAmountIn}.
 */
export function smoothedMonthlyEquivalent(perOccurrence: Money, frequency: Frequency): Money {
  return multiply(perOccurrence, periodsPerMonth(frequency));
}
