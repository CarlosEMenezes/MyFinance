import type { Money } from '../../lib/money';

export interface EditablePlanCellProps {
  /** The planned amount per occurrence. */
  readonly value: Money;
  /**
   * Accessible name, e.g. "Planned amount for Groceries". The design shows no
   * visible label — the column heading carries it — so this is what makes the
   * field identifiable to a screen reader and to a test.
   */
  readonly label: string;
  /** Fires only when an edit is finished and parsed, never mid-keystroke. */
  readonly onChange: (value: Money) => void;
  readonly className?: string | undefined;
}
