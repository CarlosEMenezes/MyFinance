/**
 * BR-9 — planned against real, and what the difference means.
 *
 * The subtraction is the same on both sides: **`real − planned`, always**.
 * Only the reading of that number changes. Earning more than planned is good;
 * spending more than planned is bad. So the tone, not the sign, is what
 * carries the judgement.
 *
 * This deliberately differs from the prototype, which flips the sign for
 * expenses so that underspending shows as a positive number. Spec §3 BR-9
 * defines the amount as `real − planned` for both, and the spec wins. A
 * single sign convention also means a column of variances can be summed and
 * sorted without asking what kind of row each one is.
 *
 * No colour is decided here. This module yields a {@link VarianceTone}, and
 * the component layer maps that to a token — which is what stops a variance
 * from ever being coloured by eye rather than by rule.
 */

import { type Money, isPositive, isZero, subtract } from './money';

/** Which side of the plan a category sits on (spec §2, `Category.type`). */
export type CategoryType = 'EARNING' | 'EXPENSE';

/** How the difference should read: favourable, unfavourable, or neither. */
export type VarianceTone = 'GOOD' | 'BAD' | 'NEUTRAL';

export interface Variance {
  /** `real − planned`, with the same sign convention for both category types. */
  readonly amount: Money;
  readonly tone: VarianceTone;
}

function toneFor(type: CategoryType, difference: Money): VarianceTone {
  if (isZero(difference)) {
    return 'NEUTRAL';
  }
  const overPlan = isPositive(difference);
  const favourable = type === 'EARNING' ? overPlan : !overPlan;

  return favourable ? 'GOOD' : 'BAD';
}

export function varianceOf(type: CategoryType, planned: Money, real: Money): Variance {
  const amount = subtract(real, planned);

  return { amount, tone: toneFor(type, amount) };
}
