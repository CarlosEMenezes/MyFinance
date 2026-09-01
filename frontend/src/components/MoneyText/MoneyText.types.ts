import type { Currency, Money } from '../../lib/money';

export interface MoneyTextProps {
  readonly amount: Money;
  readonly currency?: Currency;
  /**
   * Show an explicit `+` or a true minus. Use for a delta — a variance, a
   * change, an upcoming movement — never for a balance, where a leading `+`
   * reads as noise.
   */
  readonly signed?: boolean;
  /** Layout classes only: alignment and grid placement, never colour. */
  readonly className?: string | undefined;
}
