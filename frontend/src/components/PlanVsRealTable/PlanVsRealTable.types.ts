import type { Currency, Money } from '../../lib/money';
import type { CategoryType } from '../../lib/variance';

export interface PlanVsRealRow {
  readonly id: string;
  readonly category: string;
  /** Decides how the variance reads, not how it is signed (BR-9). */
  readonly type: CategoryType;
  /** Planned for the whole period, already normalised by lib/period (BR-10). */
  readonly planned: Money;
  readonly real: Money;
  /** How often the plan lands, e.g. "each week x 5 = EUR800.00". */
  readonly planNote: string;
  /** The editable per-occurrence amount. Absent on a derived row. */
  readonly perOccurrence?: Money;
  /**
   * A derived row - "Loan repayments", "Card instalments". BR-14 requires
   * these to be text, never inputs, because there is nothing to edit: they
   * come from the loan and instalment terms.
   */
  readonly locked?: boolean;
  readonly paidWith?: string;
  readonly dueNote?: string;
  /** Currency tag for an amount logged in something else (BR-8). */
  readonly foreignAmount?: string;
  /** Set on the first row of a group when grouping is active (BR-15). */
  readonly groupHeading?: string;
}

export interface PlanVsRealTableProps {
  /** Accessible name for the table, e.g. "Expenses by category". */
  readonly caption: string;
  readonly rows: readonly PlanVsRealRow[];
  readonly showPaymentMethod?: boolean;
  readonly totalLabel: string;
  readonly totalType: CategoryType;
  /**
   * Totals are given, never summed from the visible rows. BR-15: a filtered
   * table totals the filter while the dashboard totals the whole period, and
   * the component cannot know which it is looking at.
   */
  readonly totalPlanned: Money;
  readonly totalReal: Money;
  readonly currency?: Currency;
  /** Omit to render every plan as text. Supplied, editable rows become fields. */
  readonly onPlanChange?: (rowId: string, value: Money) => void;
}
