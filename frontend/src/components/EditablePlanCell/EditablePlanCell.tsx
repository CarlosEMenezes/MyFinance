import { useEffect, useState } from 'react';

import { fromDecimal, toDecimalString } from '../../lib/money';

import styles from './EditablePlanCell.module.css';
import type { EditablePlanCellProps } from './EditablePlanCell.types';

/**
 * The inline planned-amount field on the ghost row (BR-14).
 *
 * The text being edited is held locally rather than derived from the amount on
 * every keystroke, because half-typed values like `75.` are not amounts. The
 * parse happens once, when the edit is finished, so the plan is never rewritten
 * on the way to the value the user meant. An entry that does not parse restores
 * the last good amount instead of silently storing zero.
 */
export function EditablePlanCell({ value, label, onChange, className }: EditablePlanCellProps) {
  const committed = toDecimalString(value);
  const [draft, setDraft] = useState(committed);

  // Follows the amount when the plan is changed elsewhere — the same category
  // is editable on the Categories page as well (BR-14).
  useEffect(() => {
    setDraft(committed);
  }, [committed]);

  const commit = () => {
    if (draft === committed) {
      return;
    }
    try {
      onChange(fromDecimal(draft));
    } catch {
      setDraft(committed);
    }
  };

  return (
    <input
      type="text"
      inputMode="decimal"
      aria-label={label}
      className={['input', styles.cell, className].filter(Boolean).join(' ')}
      value={draft}
      onChange={(event) => {
        setDraft(event.target.value);
      }}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          commit();
        }
      }}
    />
  );
}
