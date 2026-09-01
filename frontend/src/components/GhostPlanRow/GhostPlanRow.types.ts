import type { ReactNode } from 'react';

export interface GhostPlanRowProps {
  /** How often the plan lands and what that comes to, e.g. "each week x 5 = EUR800.00". */
  readonly note: string;
  /** The planned amount cell: an editable field, or plain text for a derived row. */
  readonly children: ReactNode;
  /** Expenses carry a payment-method column; earnings do not. */
  readonly showPaymentMethod?: boolean;
  /**
   * When the money leaves, shown in the payment-method column.
   *
   * Admits `undefined` explicitly: callers forward an optional field straight
   * through, and `exactOptionalPropertyTypes` rejects that otherwise.
   */
  readonly dueNote?: string | undefined;
}
