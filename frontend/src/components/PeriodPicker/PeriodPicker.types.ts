import type { PeriodKind } from '../../types/api';

export interface PeriodPickerProps {
  readonly value: PeriodKind;
  readonly onChange: (value: PeriodKind) => void;
  /** The dates the window actually covers, e.g. "01-08 → 31-08-2026". */
  readonly rangeLabel: string;
}
