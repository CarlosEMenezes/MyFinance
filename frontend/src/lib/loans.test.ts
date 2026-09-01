import { describe, expect, it } from 'vitest';

import { formatAnnualRate } from './instalments';
import { analyseLoan } from './loans';
import { format, fromDecimal } from './money';

/** The two loans the design prototype ships with. */
const creditUnion = {
  principal: fromDecimal('2500'),
  instalmentCount: 24,
  instalmentAmount: fromDecimal('118.40'),
  frequency: 'MONTHLY',
  instalmentsPaid: 5,
} as const;

const familyLoan = {
  principal: fromDecimal('600'),
  instalmentCount: 6,
  instalmentAmount: fromDecimal('100'),
  frequency: 'MONTHLY',
  instalmentsPaid: 3,
} as const;

describe('analyseLoan — the cost of borrowing', () => {
  it('repays the instalment amount times the count', () => {
    expect(format(analyseLoan(creditUnion).totalRepayable)).toBe('€2,841.60');
  });

  it('charges the difference between what is repaid and what was received', () => {
    expect(format(analyseLoan(creditUnion).interest)).toBe('€341.60');
  });

  it('solves the same rate an instalment plan on those terms would', () => {
    const analysis = analyseLoan(creditUnion);

    expect(analysis.periodicRate).toBeCloseTo(0.01051039, 8);
    expect(formatAnnualRate(analysis)).toBe('13.4%');
  });

  it('recognises an interest-free loan from a family member', () => {
    const analysis = analyseLoan(familyLoan);

    expect(format(analysis.interest)).toBe('€0.00');
    expect(analysis.isInterestFree).toBe(true);
    expect(formatAnnualRate(analysis)).toBe('0%');
  });
});

describe('analyseLoan — what is still owed', () => {
  it('counts the instalments still to pay', () => {
    expect(analyseLoan(creditUnion).instalmentsRemaining).toBe(19);
  });

  it('owes the remaining instalments at face value', () => {
    expect(format(analyseLoan(creditUnion).remainingRepayable)).toBe('€2,249.60');
  });
});

describe('analyseLoan — settling early', () => {
  it('discounts the remaining instalments back to today', () => {
    // 19 x 118.40 = 2,249.60 face value, worth 2,029.59 today at 1.051% a month.
    const analysis = analyseLoan(creditUnion);

    expect(format(analysis.settlementFigureToday)).toBe('€2,029.59');
  });

  it('saves the interest that would have accrued on the remaining term', () => {
    expect(format(analyseLoan(creditUnion).earlyPayoffSaving)).toBe('€220.01');
  });

  it('settles an untouched loan at exactly the principal, saving exactly the interest', () => {
    // The coherence check for BR-7: before any instalment is paid, the present
    // value of the whole schedule is the money that was handed over, and
    // clearing it immediately saves the entire interest charge.
    const analysis = analyseLoan({ ...creditUnion, instalmentsPaid: 0 });

    expect(format(analysis.settlementFigureToday)).toBe('€2,500.00');
    expect(format(analysis.earlyPayoffSaving)).toBe('€341.60');
    expect(format(analysis.earlyPayoffSaving)).toBe(format(analysis.interest));
  });

  it('discounts a single remaining instalment by one period', () => {
    const analysis = analyseLoan({ ...creditUnion, instalmentsPaid: 23 });

    expect(analysis.instalmentsRemaining).toBe(1);
    expect(format(analysis.settlementFigureToday)).toBe('€117.17');
    expect(format(analysis.earlyPayoffSaving)).toBe('€1.23');
  });

  it('gains nothing by settling an interest-free loan early', () => {
    // BR-7: with no interest, the settlement figure is simply the face value.
    const analysis = analyseLoan(familyLoan);

    expect(format(analysis.remainingRepayable)).toBe('€300.00');
    expect(format(analysis.settlementFigureToday)).toBe('€300.00');
    expect(format(analysis.earlyPayoffSaving)).toBe('€0.00');
  });

  it('owes and saves nothing once every instalment is paid', () => {
    const analysis = analyseLoan({ ...creditUnion, instalmentsPaid: 24 });

    expect(analysis.instalmentsRemaining).toBe(0);
    expect(format(analysis.remainingRepayable)).toBe('€0.00');
    expect(format(analysis.settlementFigureToday)).toBe('€0.00');
    expect(format(analysis.earlyPayoffSaving)).toBe('€0.00');
  });

  it('never claims a saving larger than the interest itself', () => {
    const analysis = analyseLoan(creditUnion);

    expect(analysis.earlyPayoffSaving).toBeLessThanOrEqual(analysis.interest);
  });
});

describe('analyseLoan — invalid terms', () => {
  it('rejects a negative number of instalments paid', () => {
    expect(() => analyseLoan({ ...creditUnion, instalmentsPaid: -1 })).toThrow(/instalmentsPaid/i);
  });

  it('rejects more instalments paid than the loan has', () => {
    expect(() => analyseLoan({ ...creditUnion, instalmentsPaid: 25 })).toThrow(/instalmentsPaid/i);
  });

  it('rejects a fractional number of instalments paid', () => {
    expect(() => analyseLoan({ ...creditUnion, instalmentsPaid: 2.5 })).toThrow(/instalmentsPaid/i);
  });

  it('rejects a non-positive principal', () => {
    expect(() => analyseLoan({ ...creditUnion, principal: fromDecimal('0') })).toThrow(
      /principal/i,
    );
  });
});
