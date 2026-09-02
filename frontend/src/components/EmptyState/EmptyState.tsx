import { Panel } from '../Panel';

import styles from './EmptyState.module.css';
import type { EmptyStateProps } from './EmptyState.types';

/**
 * Nothing here yet, said in a way that helps.
 *
 * Not in the prototype, so it is drawn from the system: the same blueprint
 * frame every other panel wears, because an empty region that loses the frame
 * reads as a rendering failure rather than as a state.
 *
 * The message says what would fill it, and the action is how — an empty state
 * with neither is just a smaller way of saying nothing.
 */
export function EmptyState({ title, message, action }: EmptyStateProps) {
  return (
    <Panel className={styles.state}>
      <h2 className={styles.title}>{title}</h2>
      <p className={styles.message}>{message}</p>
      {action !== undefined && <div className={styles.action}>{action}</div>}
    </Panel>
  );
}
