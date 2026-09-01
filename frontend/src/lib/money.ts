/**
 * Money in the frontend is an integer number of minor units (cents).
 *
 * Spec §0.5: never use a JavaScript `number` for money arithmetic. The brand
 * on {@link Money} is what enforces that — a plain number will not typecheck
 * where a Money is expected, so amounts cannot leak in unrounded from a form
 * field or an API response without passing through {@link fromDecimal} or
 * {@link fromMinorUnits} first.
 *
 * Formatting happens only at the edge, through {@link format}.
 */

declare const moneyBrand: unique symbol;

/** An exact amount, held as a whole number of minor units. */
export type Money = number & { readonly [moneyBrand]: true };

export type Currency = 'EUR' | 'USD' | 'GBP' | 'BRL';

const MINOR_UNITS_PER_MAJOR = 100;
const DECIMAL_PLACES = 2;
const DEFAULT_CURRENCY: Currency = 'EUR';

/** Matches an optionally signed decimal. Deliberately strict: no thousands
 *  separators, no whitespace, no exponent — those are input-parsing concerns
 *  that belong to the form layer, not to money. */
const DECIMAL_PATTERN = /^([+-]?)(\d+)(?:\.(\d+))?$/;

const SYMBOLS: Record<Currency, string> = {
  EUR: '€',
  USD: '$',
  GBP: '£',
  BRL: 'R$',
};

/** The locale supplies grouping only; the decimal part is rendered from the
 *  exact minor units, so no locale can change the value that is displayed. */
const GROUPING_LOCALE = 'en-IE';

/** U+2212. A hyphen is not a minus sign, and in tabular figures it reads as
 *  a dash rather than a negative (spec §5). */
const MINUS_SIGN = '−';

export const ZERO = 0 as Money;

export function fromMinorUnits(minorUnits: number): Money {
  if (!Number.isFinite(minorUnits)) {
    throw new RangeError(`An amount must be a finite number, received ${String(minorUnits)}`);
  }
  if (!Number.isInteger(minorUnits)) {
    throw new RangeError(
      `An amount must be a whole number of minor units, received ${String(minorUnits)}`,
    );
  }
  if (!Number.isSafeInteger(minorUnits)) {
    throw new RangeError(`The amount ${String(minorUnits)} is too large to represent exactly`);
  }
  return minorUnits as Money;
}

export function toMinorUnits(money: Money): number {
  return money;
}

/**
 * Parses a decimal amount, rounding half away from zero so that it agrees with
 * the backend's `BigDecimal` scale 2, `RoundingMode.HALF_UP`.
 *
 * The digits are read as text rather than through `parseFloat`, so a value
 * like `0.005` is never first mangled into binary floating point.
 */
export function fromDecimal(value: string | number): Money {
  const text = typeof value === 'number' ? String(value) : value.trim();
  const match = DECIMAL_PATTERN.exec(text);
  if (match === null) {
    throw new TypeError(`"${text}" is not a valid amount`);
  }

  const isNegativeAmount = match[1] === '-';
  const wholePart = match[2] ?? '0';
  const fractionDigits = match[3] ?? '';

  const cents = (fractionDigits + '00').slice(0, DECIMAL_PLACES);
  const beyondCents = fractionDigits.slice(DECIMAL_PLACES);
  const roundsUp = (beyondCents[0] ?? '0') >= '5';

  const magnitude = Number(wholePart) * MINOR_UNITS_PER_MAJOR + Number(cents) + (roundsUp ? 1 : 0);

  return fromMinorUnits(isNegativeAmount ? -magnitude : magnitude);
}

export function add(augend: Money, addend: Money): Money {
  return fromMinorUnits(augend + addend);
}

export function subtract(minuend: Money, subtrahend: Money): Money {
  return fromMinorUnits(minuend - subtrahend);
}

/**
 * Scales an amount, rounding half away from zero.
 *
 * `Math.round` rounds half towards positive infinity, which would turn −0.5
 * into −0 and quietly disagree with the backend on every negative half-cent.
 */
export function multiply(money: Money, factor: number): Money {
  if (!Number.isFinite(factor)) {
    throw new RangeError(`A factor must be a finite number, received ${String(factor)}`);
  }
  const scaled = money * factor;
  const rounded = Math.sign(scaled) * Math.round(Math.abs(scaled));
  return fromMinorUnits(rounded);
}

export function negate(money: Money): Money {
  // Scaling rather than unary minus: `-money` on a branded type is flagged by
  // @typescript-eslint/no-unsafe-unary-minus, and multiplying by -1 is exact.
  return multiply(money, -1);
}

export function absolute(money: Money): Money {
  return fromMinorUnits(Math.abs(money));
}

export function sum(amounts: readonly Money[]): Money {
  return amounts.reduce<Money>(add, ZERO);
}

/** Negative when `a` is the smaller amount, positive when it is the larger,
 *  zero when they are equal — the contract `Array.prototype.sort` expects. */
export function compare(a: Money, b: Money): number {
  return a - b;
}

export function isZero(money: Money): boolean {
  return money === 0;
}

export function isNegative(money: Money): boolean {
  return money < 0;
}

export function isPositive(money: Money): boolean {
  return money > 0;
}

/**
 * Renders an amount for display, e.g. `€1,234.56` or `-€1,234.56`.
 *
 * The sign sits outside the symbol, and the fractional part is built from the
 * exact minor units rather than by dividing, so nothing is lost to binary
 * floating point on the way to the screen.
 */
export function format(money: Money, currency: Currency = DEFAULT_CURRENCY): string {
  const magnitude = Math.abs(money);
  const majorUnits = Math.trunc(magnitude / MINOR_UNITS_PER_MAJOR);
  const minorUnits = magnitude % MINOR_UNITS_PER_MAJOR;

  const grouped = majorUnits.toLocaleString(GROUPING_LOCALE);
  const fraction = String(minorUnits).padStart(DECIMAL_PLACES, '0');
  const sign = money < 0 ? '-' : '';

  return `${sign}${SYMBOLS[currency]}${grouped}.${fraction}`;
}

/**
 * Renders an amount with an explicit sign, for variances and deltas where the
 * direction matters as much as the size (BR-9). Zero is rendered unsigned.
 */
export function formatSigned(money: Money, currency: Currency = DEFAULT_CURRENCY): string {
  if (isPositive(money)) {
    return `+${format(money, currency)}`;
  }
  if (isNegative(money)) {
    return `${MINUS_SIGN}${format(absolute(money), currency)}`;
  }
  return format(money, currency);
}
