/**
 * BR-11 — saving toward a target, and whether it is realistic.
 *
 * The question a goal answers is "what do I have to put aside, and can I
 * actually afford it?". The what-if control moves the target date and the
 * frequency and recomputes live, so everything here is a pure function of the
 * numbers it is given.
 *
 * Note the horizon is measured in **months multiplied out**, not in real
 * calendar dates: BR-11 fixes daily at 30.4 and weekly at 4.33 to the month.
 * That is deliberate and differs from BR-10, where a plan's occurrences are
 * counted on the calendar. A goal is a smooth target rather than a schedule —
 * nothing lands on a particular day — so an average is the honest model, and
 * it keeps the slider from jumping as it crosses a month boundary.
 */

import {
  ZERO,
  type Money,
  isPositive,
  multiply,
  subtract,
  toMinorUnits,
  isNegative,
} from './money';

/** How often the user intends to put money aside (spec §2, `Goal`). */
export type ContributionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

/** BR-11: daily = months × 30.4, weekly = months × 4.33, monthly = months. */
const PERIODS_PER_MONTH: Record<ContributionFrequency, number> = {
  DAILY: 30.4,
  WEEKLY: 4.33,
  MONTHLY: 1,
};

const PERCENT = 100;
const FULL_PROGRESS = 100;

export interface GoalPlan {
  /** What is still to save. Never negative — a reached goal has no gap. */
  readonly gap: Money;
  readonly periodsRemaining: number;
  /** What to put aside each period to close the gap in time. */
  readonly contributionPerPeriod: Money;
  /** The same requirement stated per month, whatever the frequency, so it can
   *  be compared against the monthly spare. BR-11 requires this on screen. */
  readonly monthlyRequirement: Money;
}

export interface GoalFeasibility {
  readonly isAchievable: boolean;
  /**
   * Monthly spare minus the monthly requirement. Positive is the surplus left
   * over, negative is the shortfall — one signed figure so the caller states
   * whichever applies without recomputing anything.
   */
  readonly spareAfterContributing: Money;
}

export function periodsUntilTarget(frequency: ContributionFrequency, months: number): number {
  return months * PERIODS_PER_MONTH[frequency];
}

function assertPositiveMonths(months: number): void {
  if (!(months > 0)) {
    throw new RangeError(
      `months until the target must be greater than zero, received ${String(months)}`,
    );
  }
}

export function planGoal(
  targetAmount: Money,
  savedAmount: Money,
  frequency: ContributionFrequency,
  months: number,
): GoalPlan {
  if (!isPositive(targetAmount)) {
    throw new RangeError('targetAmount must be greater than zero');
  }
  assertPositiveMonths(months);

  const outstanding = subtract(targetAmount, savedAmount);
  // Overshooting a goal leaves nothing to save, not a negative contribution.
  const gap = isNegative(outstanding) ? ZERO : outstanding;
  const periodsRemaining = periodsUntilTarget(frequency, months);

  return {
    gap,
    periodsRemaining,
    contributionPerPeriod: multiply(gap, 1 / periodsRemaining),
    monthlyRequirement: multiply(gap, 1 / months),
  };
}

/**
 * How much of the target is saved, 0–100.
 *
 * Capped at 100 so a progress bar cannot overflow when a goal is overshot.
 * A display-only percentage, which is the one place spec §0.5 permits a
 * floating-point number.
 */
export function progressPercent(targetAmount: Money, savedAmount: Money): number {
  if (!isPositive(targetAmount)) {
    return 0;
  }
  const ratio = toMinorUnits(savedAmount) / toMinorUnits(targetAmount);

  return Math.min(FULL_PROGRESS, Math.max(0, Math.round(ratio * PERCENT)));
}

/**
 * BR-11 — compares the monthly requirement against what is actually spare
 * (planned in minus planned out).
 */
export function assessFeasibility(monthlyRequirement: Money, monthlySpare: Money): GoalFeasibility {
  const spareAfterContributing = subtract(monthlySpare, monthlyRequirement);

  return {
    isAchievable: !isNegative(spareAfterContributing),
    spareAfterContributing,
  };
}
