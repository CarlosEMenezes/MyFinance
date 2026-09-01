import type { Currency, Money } from '../../lib/money';
import type { CategoryType } from '../../lib/variance';

export interface VarianceTextProps {
  /** Which side of the plan this row sits on — decides the colour, not the sign. */
  readonly type: CategoryType;
  readonly planned: Money;
  readonly real: Money;
  readonly currency?: Currency;
  /** Layout classes only: alignment and grid placement, never colour. */
  readonly className?: string | undefined;
}
