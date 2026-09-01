import { describe, expect, it } from 'vitest';

import {
  ZERO,
  absolute,
  add,
  compare,
  format,
  formatSigned,
  fromDecimal,
  fromMinorUnits,
  isNegative,
  isZero,
  multiply,
  negate,
  subtract,
  sum,
  toMinorUnits,
} from './money';

describe('fromMinorUnits', () => {
  it('accepts a whole number of cents', () => {
    expect(toMinorUnits(fromMinorUnits(1234))).toBe(1234);
  });

  it('accepts zero and negative amounts', () => {
    expect(toMinorUnits(fromMinorUnits(0))).toBe(0);
    expect(toMinorUnits(fromMinorUnits(-500))).toBe(-500);
  });

  it('rejects a fractional cent, because that is how money stops being exact', () => {
    expect(() => fromMinorUnits(12.5)).toThrow(/whole number of minor units/i);
  });

  it('rejects values that are not finite numbers', () => {
    expect(() => fromMinorUnits(Number.NaN)).toThrow();
    expect(() => fromMinorUnits(Number.POSITIVE_INFINITY)).toThrow();
  });

  it('rejects values beyond exact integer arithmetic', () => {
    expect(() => fromMinorUnits(Number.MAX_SAFE_INTEGER + 2)).toThrow();
  });
});

describe('fromDecimal', () => {
  it('parses a plain decimal string into minor units', () => {
    expect(toMinorUnits(fromDecimal('12.34'))).toBe(1234);
  });

  it('parses a value with no fractional part', () => {
    expect(toMinorUnits(fromDecimal('780'))).toBe(78000);
  });

  it('parses a single decimal place as tens of cents', () => {
    expect(toMinorUnits(fromDecimal('1234.5'))).toBe(123450);
  });

  it('parses negative amounts', () => {
    expect(toMinorUnits(fromDecimal('-3.10'))).toBe(-310);
  });

  it('accepts a number as well as a string', () => {
    expect(toMinorUnits(fromDecimal(29))).toBe(2900);
  });

  it('rounds half away from zero, matching BigDecimal HALF_UP', () => {
    expect(toMinorUnits(fromDecimal('0.005'))).toBe(1);
    expect(toMinorUnits(fromDecimal('-0.005'))).toBe(-1);
    expect(toMinorUnits(fromDecimal('0.004'))).toBe(0);
  });

  it('rejects input that is not a number', () => {
    expect(() => fromDecimal('abc')).toThrow(/not a valid amount/i);
    expect(() => fromDecimal('')).toThrow(/not a valid amount/i);
    expect(() => fromDecimal('12,34')).toThrow(/not a valid amount/i);
  });
});

describe('arithmetic', () => {
  it('adds without the floating-point error that 0.1 + 0.2 would produce', () => {
    expect(add(fromDecimal('0.10'), fromDecimal('0.20'))).toBe(fromDecimal('0.30'));
  });

  it('subtracts', () => {
    expect(subtract(fromDecimal('780.00'), fromDecimal('318.40'))).toBe(fromDecimal('461.60'));
  });

  it('multiplies by a scalar, rounding half away from zero', () => {
    expect(multiply(fromDecimal('71.50'), 6)).toBe(fromDecimal('429.00'));
    // 33.33 x 3 = 99.99, and 0.005 of a cent rounds up
    expect(multiply(fromDecimal('10.005'), 1)).toBe(fromDecimal('10.01'));
    expect(multiply(fromDecimal('100.00'), 1 / 3)).toBe(fromDecimal('33.33'));
  });

  it('rejects a factor that is not a finite number', () => {
    expect(() => multiply(fromDecimal('10'), Number.NaN)).toThrow(/finite number/i);
    expect(() => multiply(fromDecimal('10'), Number.POSITIVE_INFINITY)).toThrow(/finite number/i);
  });

  it('negates and takes absolute value', () => {
    expect(negate(fromDecimal('12.34'))).toBe(fromDecimal('-12.34'));
    expect(absolute(fromDecimal('-12.34'))).toBe(fromDecimal('12.34'));
  });

  it('sums a list, and an empty list is zero', () => {
    expect(sum([fromDecimal('120'), fromDecimal('842.30'), fromDecimal('1450')])).toBe(
      fromDecimal('2412.30'),
    );
    expect(sum([])).toBe(ZERO);
  });

  it('compares two amounts', () => {
    expect(compare(fromDecimal('1.00'), fromDecimal('2.00'))).toBeLessThan(0);
    expect(compare(fromDecimal('2.00'), fromDecimal('1.00'))).toBeGreaterThan(0);
    expect(compare(fromDecimal('2.00'), fromDecimal('2.00'))).toBe(0);
  });

  it('reports sign', () => {
    expect(isZero(ZERO)).toBe(true);
    expect(isNegative(fromDecimal('-0.01'))).toBe(true);
    expect(isNegative(ZERO)).toBe(false);
  });
});

describe('format', () => {
  it('renders the currency symbol, thousands separator and two decimals', () => {
    expect(format(fromDecimal('1234.56'))).toBe('€1,234.56');
  });

  it('always shows two decimals', () => {
    expect(format(fromDecimal('780'))).toBe('€780.00');
    expect(format(fromDecimal('0.05'))).toBe('€0.05');
  });

  it('places the sign before the symbol for negatives', () => {
    expect(format(fromDecimal('-1234.56'))).toBe('-€1,234.56');
  });

  it('supports the currencies the app offers', () => {
    expect(format(fromDecimal('10'), 'USD')).toBe('$10.00');
    expect(format(fromDecimal('10'), 'GBP')).toBe('£10.00');
    expect(format(fromDecimal('10'), 'BRL')).toBe('R$10.00');
  });
});

describe('formatSigned', () => {
  it('prefixes a plus for positive amounts', () => {
    expect(formatSigned(fromDecimal('450'))).toBe('+€450.00');
  });

  it('prefixes a true minus sign, not a hyphen, for negative amounts', () => {
    expect(formatSigned(fromDecimal('-450'))).toBe('−€450.00');
  });

  it('shows zero unsigned', () => {
    expect(formatSigned(ZERO)).toBe('€0.00');
  });
});
