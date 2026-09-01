import { describe, expect, it } from 'vitest';

import { analyseInstalmentPlan, formatAnnualRate } from './instalments';
import { format, fromDecimal } from './money';

/** The three instalment plans the design prototype ships with. */
const laptopRepair = {
  cashPrice: fromDecimal('399'),
  instalmentCount: 6,
  instalmentAmount: fromDecimal('71.50'),
  frequency: 'MONTHLY',
} as const;

const flight = {
  cashPrice: fromDecimal('184'),
  instalmentCount: 3,
  instalmentAmount: fromDecimal('61.34'),
  frequency: 'MONTHLY',
} as const;

const deskChair = {
  cashPrice: fromDecimal('540'),
  instalmentCount: 12,
  instalmentAmount: fromDecimal('52.90'),
  frequency: 'MONTHLY',
} as const;

describe('analyseInstalmentPlan — the totals', () => {
  it('finances the instalment amount times the count', () => {
    expect(format(analyseInstalmentPlan(laptopRepair).financedTotal)).toBe('€429.00');
  });

  it('charges the difference between the financed total and the cash price', () => {
    expect(format(analyseInstalmentPlan(laptopRepair).interest)).toBe('€30.00');
  });
});

describe('analyseInstalmentPlan — the interest-free tolerance', () => {
  it('treats a plan whose instalments sum exactly to the price as interest free', () => {
    const exact = {
      cashPrice: fromDecimal('600'),
      instalmentCount: 6,
      instalmentAmount: fromDecimal('100'),
      frequency: 'MONTHLY',
    } as const;
    const analysis = analyseInstalmentPlan(exact);

    expect(analysis.isInterestFree).toBe(true);
    expect(analysis.periodicRate).toBe(0);
    expect(formatAnnualRate(analysis)).toBe('0%');
  });

  it('absorbs rounding of one cent per instalment rather than solving on noise', () => {
    // BR-6: 3 x 61.34 = 184.02 against a 184.00 price. The 2c is rounding,
    // not interest, and the tolerance is 0.01 x 3 = 3c.
    const analysis = analyseInstalmentPlan(flight);

    expect(format(analysis.interest)).toBe('€0.02');
    expect(analysis.isInterestFree).toBe(true);
    expect(formatAnnualRate(analysis)).toBe('0%');
  });

  it('is interest free exactly at the tolerance', () => {
    const atTolerance = {
      cashPrice: fromDecimal('120.00'),
      instalmentCount: 6,
      instalmentAmount: fromDecimal('20.01'),
      frequency: 'MONTHLY',
    } as const;
    const analysis = analyseInstalmentPlan(atTolerance);

    expect(format(analysis.interest)).toBe('€0.06');
    expect(analysis.isInterestFree).toBe(true);
  });

  it('charges interest one cent past the tolerance', () => {
    const pastTolerance = {
      cashPrice: fromDecimal('120.00'),
      instalmentCount: 6,
      instalmentAmount: fromDecimal('20.02'),
      frequency: 'MONTHLY',
    } as const;
    const analysis = analyseInstalmentPlan(pastTolerance);

    expect(format(analysis.interest)).toBe('€0.12');
    expect(analysis.isInterestFree).toBe(false);
    expect(analysis.periodicRate).toBeGreaterThan(0);
  });

  it('treats instalments totalling less than the price as interest free', () => {
    const discounted = {
      cashPrice: fromDecimal('600'),
      instalmentCount: 6,
      instalmentAmount: fromDecimal('90'),
      frequency: 'MONTHLY',
    } as const;
    const analysis = analyseInstalmentPlan(discounted);

    expect(analysis.isInterestFree).toBe(true);
    expect(analysis.periodicRate).toBe(0);
  });
});

describe('analyseInstalmentPlan — the solved rate', () => {
  it('solves the periodic rate from the annuity identity', () => {
    expect(analyseInstalmentPlan(laptopRepair).periodicRate).toBeCloseTo(0.02111472, 8);
  });

  it('reproduces the cash price when the solved rate is put back into the identity', () => {
    // The defining property of BR-6: P = A x (1 - (1+i)^-n) / i.
    const { periodicRate } = analyseInstalmentPlan(laptopRepair);
    const presentValue = (71.5 * (1 - Math.pow(1 + periodicRate, -6))) / periodicRate;

    expect(presentValue).toBeCloseTo(399, 6);
  });

  it('compounds the periodic rate over the year to an APR', () => {
    expect(analyseInstalmentPlan(laptopRepair).annualRate).toBeCloseTo(0.284974, 6);
    expect(formatAnnualRate(analyseInstalmentPlan(laptopRepair))).toBe('28.5%');
  });

  it('handles a longer plan', () => {
    const analysis = analyseInstalmentPlan(deskChair);

    expect(format(analysis.interest)).toBe('€94.80');
    expect(analysis.periodicRate).toBeCloseTo(0.0258051, 7);
    expect(formatAnnualRate(analysis)).toBe('35.8%');
  });

  it('compounds a weekly plan 52 times, not 12', () => {
    const weekly = {
      cashPrice: fromDecimal('500'),
      instalmentCount: 10,
      instalmentAmount: fromDecimal('55'),
      frequency: 'WEEKLY',
    } as const;
    const analysis = analyseInstalmentPlan(weekly);

    expect(analysis.periodicRate).toBeCloseTo(0.01771543, 8);
    expect(formatAnnualRate(analysis)).toBe('149.2%');
  });

  it('compounds a fortnightly plan 26 times', () => {
    const fortnightly = {
      cashPrice: fromDecimal('500'),
      instalmentCount: 10,
      instalmentAmount: fromDecimal('55'),
      frequency: 'FORTNIGHTLY',
    } as const;
    const monthly = { ...fortnightly, frequency: 'MONTHLY' } as const;

    // Same periodic rate, different compounding, so a shorter period costs more.
    expect(analyseInstalmentPlan(fortnightly).periodicRate).toBeCloseTo(
      analyseInstalmentPlan(monthly).periodicRate,
      10,
    );
    expect(analyseInstalmentPlan(fortnightly).annualRate).toBeGreaterThan(
      analyseInstalmentPlan(monthly).annualRate,
    );
  });

  it('solves a single-instalment plan', () => {
    const single = {
      cashPrice: fromDecimal('100'),
      instalmentCount: 1,
      instalmentAmount: fromDecimal('110'),
      frequency: 'MONTHLY',
    } as const;

    // One instalment of 110 for 100 now is simply 10% for the period.
    expect(analyseInstalmentPlan(single).periodicRate).toBeCloseTo(0.1, 8);
  });
});

describe('formatAnnualRate — the display cap', () => {
  it('caps a punitive rate rather than printing a meaningless number', () => {
    const punitive = {
      cashPrice: fromDecimal('100'),
      instalmentCount: 12,
      instalmentAmount: fromDecimal('50'),
      frequency: 'MONTHLY',
    } as const;
    const analysis = analyseInstalmentPlan(punitive);

    expect(analysis.isAboveDisplayCap).toBe(true);
    expect(formatAnnualRate(analysis)).toBe('>900%');
  });

  it('does not cap a rate below the threshold', () => {
    const analysis = analyseInstalmentPlan(deskChair);

    expect(analysis.isAboveDisplayCap).toBe(false);
    expect(formatAnnualRate(analysis)).toBe('35.8%');
  });

  it('shows one decimal place', () => {
    expect(formatAnnualRate(analyseInstalmentPlan(laptopRepair))).toBe('28.5%');
  });
});

describe('analyseInstalmentPlan — invalid terms', () => {
  const valid = laptopRepair;

  it('rejects a non-positive instalment count', () => {
    expect(() => analyseInstalmentPlan({ ...valid, instalmentCount: 0 })).toThrow(
      /instalmentCount/i,
    );
    expect(() => analyseInstalmentPlan({ ...valid, instalmentCount: -3 })).toThrow(
      /instalmentCount/i,
    );
  });

  it('rejects a fractional instalment count', () => {
    expect(() => analyseInstalmentPlan({ ...valid, instalmentCount: 6.5 })).toThrow(
      /instalmentCount/i,
    );
  });

  it('rejects a non-positive cash price', () => {
    expect(() => analyseInstalmentPlan({ ...valid, cashPrice: fromDecimal('0') })).toThrow(
      /cashPrice/i,
    );
  });

  it('rejects a non-positive instalment amount', () => {
    expect(() => analyseInstalmentPlan({ ...valid, instalmentAmount: fromDecimal('0') })).toThrow(
      /instalmentAmount/i,
    );
  });
});
