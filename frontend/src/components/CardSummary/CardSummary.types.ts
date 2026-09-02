import type { CalendarDate, DateFormat } from '../../lib/dates';
import type { Currency, Money } from '../../lib/money';

interface CommonCardProps {
  readonly name: string;
  /** The account the card settles from. */
  readonly settlesFrom: string;
  readonly currency?: Currency;
  readonly dateFormat?: DateFormat;
}

/**
 * BR-5: a debit card has no statement cycle at all, so it carries no closing
 * day, no due day and no limit. Making that a separate shape rather than a set
 * of optional fields means a debit card with a closing day cannot be written.
 */
export interface DebitCardSummaryProps extends CommonCardProps {
  readonly kind: 'DEBIT';
}

/**
 * The three BR-4 dates a card screen shows. Supplied by the server, never
 * derived here: the server owns BR-4 for anything persisted (ADR-7), and a
 * card's cycle is persisted.
 */
export interface CardCycleDates {
  /** The next time a bill actually falls due. */
  readonly nextBillDate: CalendarDate;
  /** When spend on the closing day itself is billed. */
  readonly billDateOnClosingDay: CalendarDate;
  /** When spend the day after closing is billed — the next statement. */
  readonly billDateAfterClosingDay: CalendarDate;
}

export interface CreditCardSummaryProps extends CommonCardProps {
  readonly kind: 'CREDIT';
  readonly creditLimit: Money;
  /** How much of the limit is currently used. */
  readonly currentBalance: Money;
  readonly closingDay: number;
  readonly dueDay: number;
  readonly cycle: CardCycleDates;
}

export type CardSummaryProps = CreditCardSummaryProps | DebitCardSummaryProps;
