import { useEffect, useState } from 'react';

import styles from './SettingsPage.module.css';

/**
 * A saved text field that holds its draft locally and commits once, on blur.
 *
 * The same reasoning as `EditablePlanCell`: saving on every keystroke would
 * send a request per character and, worse, would persist half-typed values.
 * A field the user is still typing into is not yet an answer.
 *
 * `saved` re-seeds the draft when the stored value changes underneath — after
 * a failed write rolls back, for instance — but not while the field has focus,
 * or a slow response would overwrite what is being typed.
 */

export interface TextFieldProps {
  readonly label: string;
  readonly value: string;
  readonly onCommit: (value: string) => void;
  readonly placeholder?: string;
  readonly inputMode?: 'numeric';
}

export function TextField({ label, value, onCommit, placeholder, inputMode }: TextFieldProps) {
  const [draft, setDraft] = useState(value);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    if (!editing) {
      setDraft(value);
    }
  }, [value, editing]);

  return (
    <div className={styles.field}>
      <label>
        <span className={styles.label}>{label}</span>
        <input
          className="input"
          type="text"
          value={draft}
          placeholder={placeholder ?? ''}
          {...(inputMode === undefined ? {} : { inputMode })}
          onChange={(event) => {
            setDraft(event.target.value);
          }}
          onFocus={() => {
            setEditing(true);
          }}
          onBlur={() => {
            setEditing(false);
            if (draft !== value) {
              onCommit(draft);
            }
          }}
        />
      </label>
    </div>
  );
}
