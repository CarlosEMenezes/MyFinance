import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { LeadTimeToggle } from '../../components/LeadTimeToggle';
import { NotificationRow } from '../../components/NotificationRow';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { format as formatDate, fromIso } from '../../lib/dates';
import { fromMinorUnits } from '../../lib/money';
import type { NotificationSource } from '../../types/api';

import styles from './NotificationsPage.module.css';
import { useNotifications, type ChannelKey } from './hooks';

/**
 * What is coming due (BR-12).
 *
 * The queue is derived server-side and arrives sorted by days remaining; this
 * page filters it to the widest enabled lead time, counts what is unread and
 * writes read state back. It never works out when anything falls due.
 *
 * The lead-time panel sits beside the queue rather than on Settings because
 * the count on each toggle only means something next to the list it changes —
 * "5 days before due, 6 items" is a sentence about what is on screen.
 */

const SOURCE_LABEL: Record<NotificationSource, string> = {
  CARD_BILL: 'Card bill',
  LOAN: 'Loan',
  INSTALMENT: 'Instalment',
  DIRECT_DEBIT: 'Direct debit',
  SUBSCRIPTION: 'Subscription',
};

const CHANNELS: readonly { readonly key: ChannelKey; readonly label: string }[] = [
  { key: 'push', label: 'Push notification' },
  { key: 'email', label: 'Email' },
  { key: 'weeklySummary', label: 'Weekly summary every Monday' },
];

function describeQueue(dueSoon: number, unread: number): string {
  const due = dueSoon === 1 ? '1 due soon' : `${String(dueSoon)} due soon`;
  return unread === 0 ? due : `${String(unread)} unread · ${due}`;
}

export function NotificationsPage() {
  const {
    visible,
    unreadCount,
    leadTimes,
    channels,
    setLeadEnabled,
    setChannel,
    setRead,
    markAllRead,
    isLoading,
    error,
  } = useNotifications();

  return (
    <>
      <PageHeader kicker="Fig. 07 — What is coming due" title="Notifications" />

      {isLoading && <p className={styles.status}>Loading what is due…</p>}

      {error !== null && (
        <EmptyState title="Notifications could not be loaded" message={error.message} />
      )}

      {!isLoading && error === null && (
        <div className={styles.layout}>
          <Panel title={describeQueue(visible.length, unreadCount)} className={styles.queue}>
            {visible.length > 0 && (
              <div className={styles.actions}>
                <button
                  type="button"
                  className={['btn', 'btn-ghost'].join(' ')}
                  disabled={unreadCount === 0}
                  onClick={markAllRead}
                >
                  Mark all read
                </button>
              </div>
            )}

            {visible.length === 0 ? (
              <EmptyState
                title="Nothing due inside your lead times"
                message="Widen a lead time to see payments further out."
              />
            ) : (
              <ul className={styles.list}>
                {visible.map((item) => (
                  <li key={item.key}>
                    <NotificationRow
                      label={item.label}
                      // The date is part of the detail line because the big
                      // numeral says how soon and this says exactly when.
                      detail={`${item.detail} · ${formatDate(fromIso(item.dueDate))}`}
                      source={SOURCE_LABEL[item.sourceType]}
                      amount={fromMinorUnits(item.amount)}
                      daysUntilDue={item.daysUntilDue}
                      isRead={item.readAt !== null}
                      onToggleRead={(read) => {
                        setRead(item.key, read);
                      }}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel
            title="When to warn me"
            subtitle="Lead time before a payment is due"
            className={styles.settings}
          >
            {leadTimes.map((lead) => (
              <LeadTimeToggle
                key={lead.days}
                days={lead.days}
                enabled={lead.enabled}
                itemCount={lead.itemCount}
                onChange={(enabled) => {
                  setLeadEnabled(lead.days, enabled);
                }}
              />
            ))}

            <div className={styles.channels}>
              {CHANNELS.map((channel) => (
                <Checkbox
                  key={channel.key}
                  label={channel.label}
                  checked={channels[channel.key]}
                  onChange={(on) => {
                    setChannel(channel.key, on);
                  }}
                />
              ))}
            </div>
          </Panel>
        </div>
      )}
    </>
  );
}
