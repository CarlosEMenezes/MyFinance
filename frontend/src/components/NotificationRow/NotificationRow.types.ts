import type { Currency, Money } from '../../lib/money';

export interface NotificationRowProps {
  readonly label: string;
  readonly detail: string;
  /** Where the item came from: Card bill, Loan, Instalment, Direct debit... */
  readonly source: string;
  /** The amount leaving. Always shown as an outgoing. */
  readonly amount: Money;
  /** Negative once the payment is overdue. */
  readonly daysUntilDue: number;
  readonly isRead: boolean;
  readonly onToggleRead: (read: boolean) => void;
  readonly currency?: Currency;
}
