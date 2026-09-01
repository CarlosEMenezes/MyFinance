/**
 * BR-6 — what an instalment plan really costs.
 *
 * The user is shown a cash price and "n payments of A". The difference
 * between `A × n` and the cash price is interest, and the point of this
 * module is to turn that into a rate they can compare against anything else.
 *
 * Two things here are easy to get wrong:
 *
 * - **Rounding is not interest.** Splitting €184 three ways gives €61.34 each
 *   and totals €184.02. That 2c is the instalment being rounded to the cent,
 *   not a charge. BR-6 tolerates one cent per instalment and reports exactly
 *   0% — running the solver on that noise would print a confident, absurd APR.
 * - **The rate is periodic, the APR is annual.** The same weekly rate
 *   compounds 52 times and a monthly one 12, so the frequency changes the APR
 *   even when nothing else does.
 */

import { type Money, isPositive, multiply, subtract, toMinorUnits } from './money';
import { type Frequency, periodsPerYear } from './period';

/** One cent of rounding per instalment is tolerated (BR-6). */
const TOLERATED_ROUNDING_MINOR_UNITS_PER_INSTALMENT = 1;

/** BR-6 bisects over (0, 3] — 300% per period is past any real product. */
const RATE_SEARCH_LOWER_BOUND = 1e-12;
const RATE_SEARCH_UPPER_BOUND = 3;
const RATE_CONVERGENCE_TOLERANCE = 1e-12;
const MAX_BISECTION_ITERATIONS = 200;

/** BR-6 caps the display at >900% APR. */
const DISPLAY_CAP_ANNUAL_RATE = 9;
const RATE_DECIMAL_PLACES = 1;
const PERCENT = 100;

export interface InstalmentTerms {
  readonly cashPrice: Money;
  readonly instalmentCount: number;
  readonly instalmentAmount: Money;
  readonly frequency: Frequency;
}

/**
 * Everything {@link formatAnnualRate} needs. Kept separate from the analyses
 * that carry it so a loan (BR-7) renders its rate through exactly the same
 * rule as an instalment plan, rather than growing a second copy of it.
 */
export interface AnnualRateSummary {
  /** True when the difference is within the tolerated rounding band. */
  readonly isInterestFree: boolean;
  /** The rate per instalment period. Exactly 0 when interest free. */
  readonly periodicRate: number;
  /** `(1 + periodicRate) ^ periodsPerYear − 1`, as a fraction. */
  readonly annualRate: number;
  /** True when the APR exceeds the 900% the UI is willing to print. */
  readonly isAboveDisplayCap: boolean;
}

export interface InstalmentAnalysis extends AnnualRateSummary {
  /** `instalmentAmount × instalmentCount`. */
  readonly financedTotal: Money;
  /** `financedTotal − cashPrice`. May be zero or negative. */
  readonly interest: Money;
}

function assertPositiveCount(count: number): void {
  if (!Number.isInteger(count) || count < 1) {
    throw new RangeError(
      `instalmentCount must be a whole number of at least 1, received ${String(count)}`,
    );
  }
}

function assertPositiveAmount(amount: Money, name: string): void {
  if (!isPositive(amount)) {
    throw new RangeError(`${name} must be greater than zero`);
  }
}

/**
 * Solves the periodic rate from the annuity present-value identity
 * `P = A × (1 − (1+i)^−n) / i`.
 *
 * Present value falls monotonically as the rate rises, so bisection is both
 * safe and sufficient: a present value above the cash price means the rate
 * guess was too low. There is no closed form for `i`, which is why this is
 * solved rather than calculated.
 */
function solvePeriodicRate(cashPrice: number, instalmentAmount: number, count: number): number {
  let low = RATE_SEARCH_LOWER_BOUND;
  let high = RATE_SEARCH_UPPER_BOUND;

  for (let iteration = 0; iteration < MAX_BISECTION_ITERATIONS; iteration += 1) {
    if (high - low < RATE_CONVERGENCE_TOLERANCE) {
      break;
    }
    const guess = (low + high) / 2;
    const presentValue = (instalmentAmount * (1 - Math.pow(1 + guess, -count))) / guess;

    if (presentValue > cashPrice) {
      low = guess;
    } else {
      high = guess;
    }
  }

  return (low + high) / 2;
}

export function analyseInstalmentPlan(terms: InstalmentTerms): InstalmentAnalysis {
  assertPositiveCount(terms.instalmentCount);
  assertPositiveAmount(terms.cashPrice, 'cashPrice');
  assertPositiveAmount(terms.instalmentAmount, 'instalmentAmount');

  const financedTotal = multiply(terms.instalmentAmount, terms.instalmentCount);
  const interest = subtract(financedTotal, terms.cashPrice);

  const toleratedRounding = TOLERATED_ROUNDING_MINOR_UNITS_PER_INSTALMENT * terms.instalmentCount;
  const isInterestFree = toMinorUnits(interest) <= toleratedRounding;

  if (isInterestFree) {
    return {
      financedTotal,
      interest,
      isInterestFree: true,
      periodicRate: 0,
      annualRate: 0,
      isAboveDisplayCap: false,
    };
  }

  const periodicRate = solvePeriodicRate(
    toMinorUnits(terms.cashPrice),
    toMinorUnits(terms.instalmentAmount),
    terms.instalmentCount,
  );
  const annualRate = Math.pow(1 + periodicRate, periodsPerYear(terms.frequency)) - 1;

  return {
    financedTotal,
    interest,
    isInterestFree: false,
    periodicRate,
    annualRate,
    isAboveDisplayCap: annualRate > DISPLAY_CAP_ANNUAL_RATE,
  };
}

/**
 * Renders the APR for display. Percentages are the one place spec §0.5 allows
 * a floating-point number, because nothing is settled in them.
 */
export function formatAnnualRate(analysis: AnnualRateSummary): string {
  if (analysis.isInterestFree) {
    return '0%';
  }
  if (analysis.isAboveDisplayCap) {
    return `>${String(DISPLAY_CAP_ANNUAL_RATE * PERCENT)}%`;
  }
  return `${(analysis.annualRate * PERCENT).toFixed(RATE_DECIMAL_PLACES)}%`;
}
