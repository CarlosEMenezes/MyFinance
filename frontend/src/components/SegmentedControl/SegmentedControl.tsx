import { useId } from 'react';

import styles from './SegmentedControl.module.css';
import type { SegmentedControlProps } from './SegmentedControl.types';

/**
 * The design's segmented choice: period, grouping, sorting, frequency, card
 * kind, date format.
 *
 * Built on native radio inputs rather than buttons, so arrow-key navigation,
 * focus behaviour and the checked state all come from the platform. The
 * selected styling is driven by `.seg-opt:has(input:checked)` in the design
 * system, which means the visual state can never disagree with the form state.
 */
export function SegmentedControl<T extends string>({
  name,
  label,
  hideLabel = false,
  options,
  value,
  onChange,
  className,
}: SegmentedControlProps<T>) {
  const labelId = useId();

  return (
    <div
      role="radiogroup"
      aria-labelledby={labelId}
      className={[styles.control, className].filter(Boolean).join(' ')}
    >
      <span id={labelId} className={hideLabel ? styles.hidden : styles.label}>
        {label}
      </span>

      <div className="seg">
        {options.map((option) => (
          <label key={option.value} className="seg-opt">
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => {
                onChange(option.value);
              }}
            />
            {option.label}
          </label>
        ))}
      </div>
    </div>
  );
}
