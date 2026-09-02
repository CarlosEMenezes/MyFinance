import styles from './Checkbox.module.css';
import type { CheckboxProps } from './Checkbox.types';

/**
 * A checkbox drawn as a wireframe square.
 *
 * The prototype builds this from a `<button>` with a coloured `<span>`, which
 * looks right and announces nothing useful: no checked state, no role, no
 * space-bar behaviour. Here a real `<input type="checkbox">` carries the state
 * and the keyboard handling, and the square is decorative — so what a screen
 * reader reports and what the user sees cannot drift apart.
 *
 * The hint is inside the label rather than beside it, so it forms part of the
 * accessible name and is not lost to anyone who cannot see the small print.
 */
export function Checkbox({ label, hint, meta, checked, onChange, className }: CheckboxProps) {
  return (
    <label className={[styles.field, className].filter(Boolean).join(' ')}>
      <input
        type="checkbox"
        className={styles.input}
        checked={checked}
        onChange={(event) => {
          onChange(event.target.checked);
        }}
      />
      <span className={styles.box} aria-hidden="true" />
      <span className={styles.text}>
        <span>{label}</span>
        {hint !== undefined && <span className={styles.hint}>{hint}</span>}
      </span>
      {meta !== undefined && <span className={styles.meta}>{meta}</span>}
    </label>
  );
}
