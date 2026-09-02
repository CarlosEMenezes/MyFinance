import type { PeriodKind } from '../../types/api';
import { SegmentedControl } from '../SegmentedControl';

import styles from './PeriodPicker.module.css';
import type { PeriodPickerProps } from './PeriodPicker.types';

/**
 * Which window the page reports on, and the dates that window covers.
 *
 * The range beside the control is not decoration: "Month" names the window,
 * but only "01-08 → 31-08-2026" says where its edges fall, and BR-10 makes
 * those edges decide how many times a weekly plan lands.
 */

const PERIODS = [
  { value: 'DAY', label: 'Day' },
  { value: 'WEEK', label: 'Week' },
  { value: 'MONTH', label: 'Month' },
  { value: 'YEAR', label: 'Year' },
  { value: 'CUSTOM', label: 'Custom' },
] as const satisfies readonly { value: PeriodKind; label: string }[];

export function PeriodPicker({ value, onChange, rangeLabel }: PeriodPickerProps) {
  return (
    <div className={styles.picker}>
      <SegmentedControl
        name="period"
        label="Period"
        hideLabel
        options={PERIODS}
        value={value}
        onChange={onChange}
      />
      <span className={styles.range}>{rangeLabel}</span>
    </div>
  );
}
