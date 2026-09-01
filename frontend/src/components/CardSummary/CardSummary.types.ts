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

export interface CreditCardSummaryProps extends CommonCardProps {
  readonly kind: 'CREDIT';
  readonly creditLimit: Money;
  /** How much of the limit is currently used. */
  readonly currentBalance: Money;
  readonly closingDay: number;
  readonly dueDay: number;
  /** Reference date for the next bill and the worked cycle example. */
  readonly today: CalendarDate;
}

export type CardSummaryProps = CreditCardSummaryProps | DebitCardSummaryProps;
