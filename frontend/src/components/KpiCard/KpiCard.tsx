import { Panel } from '../Panel';

import styles from './KpiCard.module.css';
import type { KpiCardProps } from './KpiCard.types';

/**
 * A single headline figure — the tiles across the top of the dashboard.
 *
 * Built on {@link Panel} rather than redrawing the frame, so the registration
 * marks and the hairline border can only ever be defined in one place.
 */
export function KpiCard({ kicker, value, note, tone = 'neutral' }: KpiCardProps) {
  return (
    <Panel density="compact" className={styles.card}>
      <span className="card-kicker">{kicker}</span>
      <span className={[styles.value, styles[tone]].filter(Boolean).join(' ')}>{value}</span>
      {note !== undefined && <span className={styles.note}>{note}</span>}
    </Panel>
  );
}
