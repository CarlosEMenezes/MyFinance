/**
 * BR-4 — which bill a credit-card purchase lands on.
 *
 * A card purchase does not cost you money on the day you spend it. It joins a
 * statement, and the statement is paid later. So the planned-expense date for
 * a card purchase is the computed bill date, **never** the purchase date, and
 * the log form has to show the user that date before saving.
 *
 * Two independent rolls decide it:
 *
 * 1. Spending after the closing day misses this statement and waits for next
 *    month's.
 * 2. A due day on or before the closing day cannot be in the closing month —
 *    it has already passed — so the bill falls the month after.
 *
 * BR-5 is the counterpart and needs no code: a debit card has no cycle, and
 * its spend leaves the assigned account the same day.
 */

import { type CalendarDate, addMonths, dayOf, fromParts, isBefore, monthOf, yearOf } from './dates';

/**
 * Spec §2 caps both days at 28. That is what guarantees the bill date exists
 * in every month, February included, so the result never needs clamping and a
 * card can never silently skip a month.
 */
const EARLIEST_CYCLE_DAY = 1;
const LATEST_CYCLE_DAY = 28;

export interface CreditCardCycle {
  /** Day of the month the statement closes, 1–28. */
  readonly closingDay: number;
  /** Day of the month the bill is due, 1–28. */
  readonly dueDay: number;
}

function assertCycleDay(day: number, name: string): void {
  if (!Number.isInteger(day) || day < EARLIEST_CYCLE_DAY || day > LATEST_CYCLE_DAY) {
    throw new RangeError(
      `${name} must be a whole day from ${String(EARLIEST_CYCLE_DAY)} to ${String(
        LATEST_CYCLE_DAY,
      )}, received ${String(day)}`,
    );
  }
}

/**
 * The date a card purchase becomes a planned expense.
 *
 * Only the year and month of the intermediate dates are used, so the day
 * clamping in {@link addMonths} cannot distort the result — a purchase on the
 * 31st rolling into February still bills on the cycle's due day.
 */
export function billDateFor(purchase: CalendarDate, cycle: CreditCardCycle): CalendarDate {
  assertCycleDay(cycle.closingDay, 'closingDay');
  assertCycleDay(cycle.dueDay, 'dueDay');

  const missedThisStatement = dayOf(purchase) > cycle.closingDay;
  const dueDayHasPassedByClosing = cycle.dueDay <= cycle.closingDay;

  const closingMonth = addMonths(purchase, missedThisStatement ? 1 : 0);
  const dueMonth = addMonths(closingMonth, dueDayHasPassedByClosing ? 1 : 0);

  return fromParts(yearOf(dueMonth), monthOf(dueMonth), cycle.dueDay);
}

/**
 * When the next card bill falls: the next occurrence of the due day on or
 * after `today`.
 *
 * Answers "what is coming out of my account next", which is a different
 * question from {@link billDateFor}'s "which bill does this purchase join".
 */
export function nextDueDateOnOrAfter(today: CalendarDate, dueDay: number): CalendarDate {
  assertCycleDay(dueDay, 'dueDay');

  const thisMonth = fromParts(yearOf(today), monthOf(today), dueDay);

  return isBefore(thisMonth, today) ? addMonths(thisMonth, 1) : thisMonth;
}
