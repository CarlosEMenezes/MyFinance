/**
 * BR-7 — borrowing, and what it costs to stop borrowing early.
 *
 * A loan is an instalment plan seen from the other side. There, a cash price
 * is what you would pay today and the instalments are what you pay instead;
 * here, the principal is what you received today and the instalments are what
 * you pay back. Mathematically it is the same annuity, so the rate is solved
 * by {@link analyseInstalmentPlan} rather than by a second copy of BR-6.
 *
 * What is genuinely new is the **settlement figure**: the remaining
 * instalments discounted back to today at the loan's own implied rate. Paying
 * a loan off early buys back the interest that had not yet accrued, and the
 * difference between the face value of what is left and that settlement
 * figure is the saving. BR-7 requires the UI to show it, because it is the
 * number a person needs in order to decide.
 *
 * Note what is *not* here: a loan is not income (BR-2). It raises available
 * money by the principal and owed money by the whole repayable amount, so the
 * net effect on the total position is exactly the interest. That belongs to
 * the position calculation, not to this module, and a loan must never appear
 * in the earnings breakdown.
 */

import { type AnnualRateSummary, analyseInstalmentPlan } from './instalments';
import { type Money, isPositive, multiply, subtract } from './money';
import { type Frequency } from './period';

export interface LoanTerms {
  /** The money actually received. */
  readonly principal: Money;
  readonly instalmentCount: number;
  readonly instalmentAmount: Money;
  readonly frequency: Frequency;
  readonly instalmentsPaid: number;
}

export interface LoanAnalysis extends AnnualRateSummary {
  /** `instalmentAmount × instalmentCount` — everything the loan will cost. */
  readonly totalRepayable: Money;
  /** `totalRepayable − principal`. */
  readonly interest: Money;
  readonly instalmentsRemaining: number;
  /** Face value of the instalments still to pay. */
  readonly remainingRepayable: Money;
  /** Present value of those instalments at the implied rate — the cost of
   *  clearing the loan today. */
  readonly settlementFigureToday: Money;
  /** `remainingRepayable − settlementFigureToday`. Zero when interest free. */
  readonly earlyPayoffSaving: Money;
}

function assertInstalmentsPaid(paid: number, count: number): void {
  if (!Number.isInteger(paid) || paid < 0 || paid > count) {
    throw new RangeError(
      `instalmentsPaid must be a whole number from 0 to ${String(count)}, received ${String(paid)}`,
    );
  }
}

/**
 * The annuity present-value factor for `remaining` payments at rate `i`:
 * `(1 − (1+i)^−remaining) / i`.
 *
 * With no interest there is nothing to discount, so the factor is simply the
 * number of payments left and the settlement figure comes out at face value.
 */
function presentValueFactor(remaining: number, periodicRate: number): number {
  if (periodicRate <= 0) {
    return remaining;
  }
  return (1 - Math.pow(1 + periodicRate, -remaining)) / periodicRate;
}

export function analyseLoan(terms: LoanTerms): LoanAnalysis {
  // Checked here rather than left to the delegate below, which would report a
  // bad principal as "cashPrice must be greater than zero" — a field that does
  // not exist on a loan form.
  if (!isPositive(terms.principal)) {
    throw new RangeError('principal must be greater than zero');
  }

  // Validates count and amount, and solves the rate (BR-6).
  const schedule = analyseInstalmentPlan({
    cashPrice: terms.principal,
    instalmentCount: terms.instalmentCount,
    instalmentAmount: terms.instalmentAmount,
    frequency: terms.frequency,
  });
  assertInstalmentsPaid(terms.instalmentsPaid, terms.instalmentCount);

  const instalmentsRemaining = terms.instalmentCount - terms.instalmentsPaid;
  const remainingRepayable = multiply(terms.instalmentAmount, instalmentsRemaining);
  const settlementFigureToday = multiply(
    terms.instalmentAmount,
    presentValueFactor(instalmentsRemaining, schedule.periodicRate),
  );

  return {
    totalRepayable: schedule.financedTotal,
    interest: schedule.interest,
    isInterestFree: schedule.isInterestFree,
    periodicRate: schedule.periodicRate,
    annualRate: schedule.annualRate,
    isAboveDisplayCap: schedule.isAboveDisplayCap,
    instalmentsRemaining,
    remainingRepayable,
    settlementFigureToday,
    earlyPayoffSaving: subtract(remainingRepayable, settlementFigureToday),
  };
}
