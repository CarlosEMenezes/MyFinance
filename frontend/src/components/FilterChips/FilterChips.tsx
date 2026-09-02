import { useId } from 'react';

import styles from './FilterChips.module.css';
import type { FilterChipsProps } from './FilterChips.types';

/**
 * BR-15's payment-method filter on the Expenses table.
 *
 * Chips rather than a joined segmented control, because the options are a
 * variable list of accounts and cards rather than a fixed set of views. They
 * are still radios: the filter is single-select, so arrow-key navigation and
 * the checked state should come from the platform rather than from
 * `aria-pressed` on a row of buttons.
 */
export function FilterChips({
  name,
  label,
  options,
  value,
  onChange,
  className,
}: FilterChipsProps) {
  const labelId = useId();

  return (
    <div
      role="radiogroup"
      aria-labelledby={labelId}
      className={[styles.filter, className].filter(Boolean).join(' ')}
    >
      <span id={labelId} className={styles.label}>
        {label}
      </span>

      {options.map((option) => (
        <label key={option.value} className={['tag', styles.chip].filter(Boolean).join(' ')}>
          <input
            type="radio"
            className={styles.input}
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
  );
}
