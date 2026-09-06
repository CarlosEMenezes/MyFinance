import { useId } from 'react';

import styles from './Field.module.css';
import type { FieldProps } from './Field.types';

/**
 * A labelled form control, with room for a hint and an error.
 *
 * The hint and the error are wired with `aria-describedby` rather than nested
 * inside the label: a hint inside a `<label>` becomes part of the control's
 * accessible name, so the announced name turns into a paragraph (gotcha 26).
 *
 * The error is a live region. A message that appears only visually leaves
 * anyone not looking at that spot with a form that silently refuses to save.
 */
export function Field({ label, children, hint, error, className }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;

  const describedBy = [
    hint === undefined ? undefined : hintId,
    error === undefined ? undefined : errorId,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>

      {children(describedBy === '' ? { id } : { id, describedBy })}

      {hint !== undefined && (
        <p className={styles.hint} id={hintId}>
          {hint}
        </p>
      )}

      {error !== undefined && (
        <p className={styles.error} id={errorId} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
