import { describe, expect, it } from 'vitest';

import { formatSigned, fromDecimal } from './money';
import { varianceOf } from './variance';

describe('varianceOf — the amount', () => {
  it('is real minus planned for earnings', () => {
    const variance = varianceOf('EARNING', fromDecimal('1200'), fromDecimal('1010'));
    expect(formatSigned(variance.amount)).toBe('−€190.00');
  });

  it('is real minus planned for expenses too, not flipped', () => {
    // The sign convention is the same on both sides; only the colour differs.
    // Overspending by 258.40 reads as +258.40, never as -258.40.
    const variance = varianceOf('EXPENSE', fromDecimal('60'), fromDecimal('318.40'));
    expect(formatSigned(variance.amount)).toBe('+€258.40');
  });

  it('is zero when reality matched the plan', () => {
    const variance = varianceOf('EXPENSE', fromDecimal('780'), fromDecimal('780'));
    expect(formatSigned(variance.amount)).toBe('€0.00');
  });
});

describe('varianceOf — earnings tone', () => {
  it('is good when more was earned than planned', () => {
    expect(varianceOf('EARNING', fromDecimal('160'), fromDecimal('672')).tone).toBe('GOOD');
  });

  it('is bad when less was earned than planned', () => {
    expect(varianceOf('EARNING', fromDecimal('1200'), fromDecimal('1010')).tone).toBe('BAD');
  });

  it('is neutral when they match', () => {
    expect(varianceOf('EARNING', fromDecimal('450'), fromDecimal('450')).tone).toBe('NEUTRAL');
  });

  it('is good when something unplanned was earned', () => {
    expect(varianceOf('EARNING', fromDecimal('0'), fromDecimal('64.50')).tone).toBe('GOOD');
  });
});

describe('varianceOf — expenses tone', () => {
  it('is bad when more was spent than planned', () => {
    expect(varianceOf('EXPENSE', fromDecimal('60'), fromDecimal('318.40')).tone).toBe('BAD');
  });

  it('is good when less was spent than planned', () => {
    expect(varianceOf('EXPENSE', fromDecimal('45'), fromDecimal('44.98')).tone).toBe('GOOD');
  });

  it('is neutral when they match', () => {
    expect(varianceOf('EXPENSE', fromDecimal('29'), fromDecimal('29')).tone).toBe('NEUTRAL');
  });

  it('is bad when something unplanned was spent', () => {
    expect(varianceOf('EXPENSE', fromDecimal('0'), fromDecimal('12.99')).tone).toBe('BAD');
  });
});

describe('varianceOf — the two types are mirror images', () => {
  it('gives the same amount but the opposite tone for the same numbers', () => {
    const planned = fromDecimal('100');
    const real = fromDecimal('150');

    const earning = varianceOf('EARNING', planned, real);
    const expense = varianceOf('EXPENSE', planned, real);

    expect(formatSigned(earning.amount)).toBe(formatSigned(expense.amount));
    expect(earning.tone).toBe('GOOD');
    expect(expense.tone).toBe('BAD');
  });

  it('agrees on neutral, which has no direction to disagree about', () => {
    const planned = fromDecimal('75');
    const real = fromDecimal('75');

    expect(varianceOf('EARNING', planned, real).tone).toBe('NEUTRAL');
    expect(varianceOf('EXPENSE', planned, real).tone).toBe('NEUTRAL');
  });
});
