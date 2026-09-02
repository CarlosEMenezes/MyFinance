import { negate } from '../../lib/money';
import { MoneyText } from '../MoneyText';
import { TagChip } from '../TagChip';

import styles from './NotificationRow.module.css';
import type { NotificationRowProps } from './NotificationRow.types';

/**
 * One thing coming due (BR-12).
 *
 * The days remaining are the first thing on the row and the largest thing on
 * it, because the queue is sorted by urgency and that is the number being
 * scanned. Colour alone carries urgency in the design, so the same fact is
 * also stated in text for anyone who cannot see it.
 *
 * Read items recede rather than disappear: BR-12 keeps read state per item, so
 * marking something read must not make it unfindable.
 */

/** Inside this many days the item reads as urgent. */
const URGENT_WITHIN_DAYS = 2;

function describeRemaining(daysUntilDue: number): {
  readonly count: string;
  readonly unit: string;
} {
  if (daysUntilDue <= 0) {
    return { count: 'now', unit: 'due' };
  }
  return { count: String(daysUntilDue), unit: daysUntilDue === 1 ? 'day' : 'days' };
}

function describeUrgency(daysUntilDue: number): string {
  if (daysUntilDue < 0) {
    return 'Overdue';
  }
  if (daysUntilDue === 0) {
    return 'Due today';
  }
  return daysUntilDue === 1 ? 'Due in 1 day' : `Due in ${String(daysUntilDue)} days`;
}

export function NotificationRow({
  label,
  detail,
  source,
  amount,
  daysUntilDue,
  isRead,
  onToggleRead,
  currency = 'EUR',
}: NotificationRowProps) {
  const remaining = describeRemaining(daysUntilDue);
  const isUrgent = daysUntilDue <= URGENT_WITHIN_DAYS;

  return (
    <div
      className={[
        styles.row,
        isUrgent ? styles.urgent : undefined,
        isRead ? styles.read : undefined,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div>
        <div className={styles.days}>{remaining.count}</div>
        <div className={styles.daysUnit}>{remaining.unit}</div>
        <span className={styles.srOnly}>{describeUrgency(daysUntilDue)}</span>
      </div>

      <div>
        <div className={styles.identity}>
          <span className={styles.label}>{label}</span>
          <TagChip variant={isUrgent ? 'accent' : 'neutral'}>{source}</TagChip>
        </div>
        <p className={styles.detail}>{detail}</p>
      </div>

      <div className={styles.trailing}>
        <MoneyText amount={negate(amount)} currency={currency} signed className={styles.amount} />
        <button
          type="button"
          className={['btn', 'btn-ghost', styles.action].join(' ')}
          // The visible text is short because the row is dense; the accessible
          // name names the item, so a list of these is not a column of
          // identical "Mark read" buttons to anyone navigating by control.
          aria-label={isRead ? `Mark ${label} as unread` : `Mark ${label} as read`}
          onClick={() => {
            onToggleRead(!isRead);
          }}
        >
          {isRead ? 'Unread' : 'Mark read'}
        </button>
      </div>
    </div>
  );
}
