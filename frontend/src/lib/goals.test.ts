import { describe, expect, it } from 'vitest';

import { assessFeasibility, periodsUntilTarget, planGoal, progressPercent } from './goals';
import { format, fromDecimal } from './money';

/** The three goals the design prototype ships with. */
const macBook = { target: fromDecimal('1349'), saved: fromDecimal('410') };
const emergencyFund = { target: fromDecimal('2000'), saved: fromDecimal('640') };
const interrail = { target: fromDecimal('900'), saved: fromDecimal('80') };

describe('periodsUntilTarget — BR-11', () => {
  it('counts months directly', () => {
    expect(periodsUntilTarget('MONTHLY', 4)).toBeCloseTo(4, 10);
  });

  it('counts weeks at 4.33 to the month', () => {
    expect(periodsUntilTarget('WEEKLY', 4)).toBeCloseTo(17.32, 10);
  });

  it('counts days at 30.4 to the month', () => {
    expect(periodsUntilTarget('DAILY', 4)).toBeCloseTo(121.6, 10);
  });
});

describe('planGoal — the gap', () => {
  it('is what is left to save', () => {
    expect(format(planGoal(macBook.target, macBook.saved, 'MONTHLY', 4).gap)).toBe('€939.00');
  });

  it('is zero once the goal is reached, never negative', () => {
    const plan = planGoal(fromDecimal('1000'), fromDecimal('1200'), 'MONTHLY', 4);

    expect(format(plan.gap)).toBe('€0.00');
    expect(format(plan.contributionPerPeriod)).toBe('€0.00');
  });
});

describe('planGoal — the required contribution', () => {
  it('divides the gap across the months', () => {
    const plan = planGoal(macBook.target, macBook.saved, 'MONTHLY', 4);
    expect(format(plan.contributionPerPeriod)).toBe('€234.75');
  });

  it('divides the gap across the weeks', () => {
    const plan = planGoal(macBook.target, macBook.saved, 'WEEKLY', 4);
    expect(format(plan.contributionPerPeriod)).toBe('€54.21');
  });

  it('divides the gap across the days', () => {
    const plan = planGoal(macBook.target, macBook.saved, 'DAILY', 4);
    expect(format(plan.contributionPerPeriod)).toBe('€7.72');
  });

  it('always states the per-month equivalent, whatever the frequency', () => {
    const weekly = planGoal(macBook.target, macBook.saved, 'WEEKLY', 4);
    const monthly = planGoal(macBook.target, macBook.saved, 'MONTHLY', 4);

    expect(format(weekly.monthlyRequirement)).toBe('€234.75');
    expect(format(monthly.monthlyRequirement)).toBe('€234.75');
  });

  it('falls as the target date is pushed out', () => {
    const soon = planGoal(macBook.target, macBook.saved, 'MONTHLY', 4);
    const later = planGoal(macBook.target, macBook.saved, 'MONTHLY', 12);

    expect(format(soon.contributionPerPeriod)).toBe('€234.75');
    expect(format(later.contributionPerPeriod)).toBe('€78.25');
    expect(later.contributionPerPeriod).toBeLessThan(soon.contributionPerPeriod);
  });

  it('handles the other prototype goals', () => {
    expect(
      format(
        planGoal(emergencyFund.target, emergencyFund.saved, 'MONTHLY', 10).contributionPerPeriod,
      ),
    ).toBe('€136.00');
    expect(
      format(planGoal(interrail.target, interrail.saved, 'MONTHLY', 9).contributionPerPeriod),
    ).toBe('€91.11');
  });
});

describe('planGoal — invalid horizons', () => {
  it('rejects a target that is not in the future', () => {
    expect(() => planGoal(macBook.target, macBook.saved, 'MONTHLY', 0)).toThrow(/months/i);
    expect(() => planGoal(macBook.target, macBook.saved, 'MONTHLY', -2)).toThrow(/months/i);
  });

  it('rejects a non-positive target amount', () => {
    expect(() => planGoal(fromDecimal('0'), fromDecimal('0'), 'MONTHLY', 4)).toThrow(/target/i);
  });
});

describe('progressPercent', () => {
  it('reports how much of the target is saved', () => {
    expect(progressPercent(macBook.target, macBook.saved)).toBe(30);
    expect(progressPercent(emergencyFund.target, emergencyFund.saved)).toBe(32);
    expect(progressPercent(interrail.target, interrail.saved)).toBe(9);
  });

  it('is zero at the start', () => {
    expect(progressPercent(fromDecimal('1000'), fromDecimal('0'))).toBe(0);
  });

  it('caps at 100 when the goal is overshot, so a bar never overflows', () => {
    expect(progressPercent(fromDecimal('1000'), fromDecimal('1500'))).toBe(100);
  });

  it('reports nothing rather than dividing by zero on a target of zero', () => {
    // planGoal rejects this, but a bar may be asked to render a half-built
    // goal before its target is set.
    expect(progressPercent(fromDecimal('0'), fromDecimal('50'))).toBe(0);
  });

  it('never reports negative progress from a negative balance', () => {
    expect(progressPercent(fromDecimal('1000'), fromDecimal('-50'))).toBe(0);
  });
});

describe('assessFeasibility — BR-11', () => {
  it('fits when the monthly requirement is inside the spare, and states what is left over', () => {
    const feasibility = assessFeasibility(fromDecimal('234.75'), fromDecimal('400'));

    expect(feasibility.isAchievable).toBe(true);
    expect(format(feasibility.spareAfterContributing)).toBe('€165.25');
  });

  it('does not fit when the requirement exceeds the spare, and states the shortfall', () => {
    const feasibility = assessFeasibility(fromDecimal('234.75'), fromDecimal('150'));

    expect(feasibility.isAchievable).toBe(false);
    expect(format(feasibility.spareAfterContributing)).toBe('-€84.75');
  });

  it('just fits when the requirement exactly equals the spare', () => {
    const feasibility = assessFeasibility(fromDecimal('234.75'), fromDecimal('234.75'));

    expect(feasibility.isAchievable).toBe(true);
    expect(format(feasibility.spareAfterContributing)).toBe('€0.00');
  });

  it('never fits when there is nothing spare', () => {
    const feasibility = assessFeasibility(fromDecimal('50'), fromDecimal('-120'));

    expect(feasibility.isAchievable).toBe(false);
    expect(format(feasibility.spareAfterContributing)).toBe('-€170.00');
  });
});
