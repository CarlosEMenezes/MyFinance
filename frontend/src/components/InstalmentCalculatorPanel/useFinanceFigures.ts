import { format as formatDate, type CalendarDate, type DateFormat } from '../../lib/dates';
import { analyseInstalmentPlan, formatAnnualRate } from '../../lib/instalments';
import { analyseLoan } from '../../lib/loans';
import { format, fromDecimal, type Currency, type Money } from '../../lib/money';
import type { Frequency } from '../../lib/period';

import type { FinanceMode } from './InstalmentCalculatorPanel.types';

export interface FinanceFigure {
  readonly label: string;
  readonly value: string;
  readonly tone?: 'good' | 'bad';
}

export interface FinanceFigures {
  readonly lines: readonly FinanceFigure[];
  readonly verdict: string;
  readonly interestFree: boolean;
}

const PERIOD_NOUN: Record<Frequency, string> = {
  WEEKLY: 'week',
  FORTNIGHTLY: 'fortnight',
  MONTHLY: 'month',
};

const PERCENT = 100;
const PERIODIC_RATE_DECIMALS = 2;
const PERCENT_OF_PRICE_DECIMALS = 1;

/** Reads the two free-text fields, or `null` while they are not yet a plan. */
function readTerms(
  countText: string,
  amountText: string,
): { readonly count: number; readonly amount: Money } | null {
  const count = Number(countText);
  if (!Number.isInteger(count) || count < 1) {
    return null;
  }
  try {
    const amount = fromDecimal(amountText);
    return amount > 0 ? { count, amount } : null;
  } catch {
    // Half-typed values like "71." are not amounts yet, and are not errors.
    return null;
  }
}

function describeRate(periodicRate: number, frequency: Frequency, apr: string): string {
  const perPeriod = (periodicRate * PERCENT).toFixed(PERIODIC_RATE_DECIMALS);
  return `${perPeriod}% a ${PERIOD_NOUN[frequency]} \u00b7 ${apr} APR`;
}

/**
 * The live figures behind an instalment plan or a loan the user has not saved
 * yet (ADR-7). The server recomputes them on save and is authoritative; this
 * exists so the slider and the form answer immediately rather than once per
 * network round-trip, which spec §5 explicitly allows.
 */
export function financeFigures(
  mode: FinanceMode,
  principal: Money,
  countText: string,
  amountText: string,
  frequency: Frequency,
  currency: Currency,
  firstDueDate: CalendarDate | undefined,
  dateFormat: DateFormat,
): FinanceFigures | null {
  const terms = readTerms(countText, amountText);
  if (terms === null || principal <= 0) {
    return null;
  }

  if (mode === 'LOAN') {
    const loan = analyseLoan({
      principal,
      instalmentCount: terms.count,
      instalmentAmount: terms.amount,
      frequency,
      instalmentsPaid: 0,
    });
    const percentOfPrincipal = ((loan.interest / principal) * PERCENT).toFixed(
      PERCENT_OF_PRICE_DECIMALS,
    );

    return {
      interestFree: loan.isInterestFree,
      lines: [
        { label: 'Adds to money now', value: `+${format(principal, currency)}`, tone: 'good' },
        { label: 'Creates a debt of', value: format(loan.totalRepayable, currency), tone: 'bad' },
        {
          label: 'Planned expense',
          value: `${format(terms.amount, currency)} every ${PERIOD_NOUN[frequency]}`,
        },
        {
          label: 'Interest cost',
          value: loan.isInterestFree ? 'none' : format(loan.interest, currency),
          tone: loan.isInterestFree ? 'good' : 'bad',
        },
        {
          label: 'Implied rate',
          value: loan.isInterestFree
            ? '0%'
            : describeRate(loan.periodicRate, frequency, formatAnnualRate(loan)),
          tone: loan.isInterestFree ? 'good' : 'bad',
        },
        {
          label: 'Settle upfront today',
          value: format(loan.settlementFigureToday, currency),
        },
      ],
      verdict: loan.isInterestFree
        ? `Interest free — the ${String(terms.count)} instalments repay exactly what you received. Paying early gains nothing.`
        : `You repay ${format(loan.interest, currency)} more than you received (${percentOfPrincipal}%). Clearing it today costs ${format(loan.settlementFigureToday, currency)}.`,
    };
  }

  const plan = analyseInstalmentPlan({
    cashPrice: principal,
    instalmentCount: terms.count,
    instalmentAmount: terms.amount,
    frequency,
  });
  const percentOfPrice = ((plan.interest / principal) * PERCENT).toFixed(PERCENT_OF_PRICE_DECIMALS);

  return {
    interestFree: plan.isInterestFree,
    lines: [
      { label: 'Cash price', value: format(principal, currency) },
      { label: 'Financed total', value: format(plan.financedTotal, currency) },
      {
        label: 'Interest paid',
        value: plan.isInterestFree ? 'none' : `+${format(plan.interest, currency)}`,
        tone: plan.isInterestFree ? 'good' : 'bad',
      },
      {
        label: 'Implied rate',
        value: plan.isInterestFree
          ? '0%'
          : describeRate(plan.periodicRate, frequency, formatAnnualRate(plan)),
        tone: plan.isInterestFree ? 'good' : 'bad',
      },
      ...(firstDueDate === undefined
        ? []
        : [{ label: 'First instalment due', value: formatDate(firstDueDate, dateFormat) }]),
    ],
    verdict: plan.isInterestFree
      ? `Interest free — the ${String(terms.count)} instalments add up to the cash price exactly.`
      : `Spreading this costs ${format(plan.interest, currency)} extra — ${percentOfPrice}% on top of the price. Each instalment lands on the card pay day.`,
  };
}
