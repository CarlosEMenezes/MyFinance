import styles from './GhostPlanRow.module.css';
import type { GhostPlanRowProps } from './GhostPlanRow.types';

/**
 * The planned line that sits above every real line (BR-9).
 *
 * It is a separate `<tr>` rather than extra cells on the real row so that the
 * two align in the same columns without any grid arithmetic, and so a screen
 * reader reads "planned ..." and then the actual figures rather than
 * interleaving them.
 *
 * The trailing cells are deliberately empty: a plan has no real amount and no
 * variance of its own — those belong to the row below.
 */
export function GhostPlanRow({
  note,
  children,
  showPaymentMethod = false,
  dueNote,
}: GhostPlanRowProps) {
  return (
    <tr className={styles.row}>
      <td>
        <span className={styles.label}>
          <span className={styles.rule} aria-hidden="true" />
          <span>planned {note}</span>
        </span>
      </td>
      {showPaymentMethod && <td>{dueNote}</td>}
      <td className={styles.amount}>{children}</td>
      <td />
      <td />
    </tr>
  );
}
