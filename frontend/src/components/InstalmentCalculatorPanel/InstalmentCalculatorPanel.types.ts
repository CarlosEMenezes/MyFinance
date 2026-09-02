import type { CalendarDate, DateFormat } from '../../lib/dates';
import type { Currency, Money } from '../../lib/money';
import type { Frequency } from '../../lib/period';

/**
 * The same maths read from two sides (BR-6, BR-7). Spreading a purchase and
 * taking a loan are the same annuity; only the wording and which side of the
 * position moves differ.
 */
export type FinanceMode = 'INSTALMENT' | 'LOAN';

export interface InstalmentCalculatorPanelProps {
  readonly mode: FinanceMode;
  /** The cash price being spread, or the principal being received. */
  readonly principal: Money;
  /** Raw field text, so a half-typed value is not rejected mid-keystroke. */
  readonly instalmentCount: string;
  readonly instalmentAmount: string;
  readonly frequency: Frequency;
  readonly onInstalmentCountChange: (value: string) => void;
  readonly onInstalmentAmountChange: (value: string) => void;
  readonly onFrequencyChange: (frequency: Frequency) => void;
  /**
   * BR-4. Supplied rather than derived: the card's cycle is the server's.
   *
   * Explicitly `undefined`-able because the log form passes it through before
   * a card has been chosen, and there is genuinely no due date yet.
   */
  readonly firstDueDate?: CalendarDate | undefined;
  readonly currency?: Currency;
  readonly dateFormat?: DateFormat;
}
