import { AccountCard } from '../../components/AccountCard';
import { EmptyState } from '../../components/EmptyState';
import { KpiCard } from '../../components/KpiCard';
import { MoneyText } from '../../components/MoneyText';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { PlanVsRealTable } from '../../components/PlanVsRealTable';
import { ProgressBar } from '../../components/ProgressBar';
import { format as formatDate, fromIso } from '../../lib/dates';
import { format as formatMoney, fromMinorUnits, isNegative } from '../../lib/money';
import { useDashboard } from '../dashboard/hooks';
import { toTableRow } from '../earnings/toTableRows';

import styles from './OverviewPage.module.css';

/**
 * The whole position in one view (BR-1, BR-2, BR-9).
 *
 * Every figure here is computed server-side and rendered as received — spec §4
 * is explicit that the frontend must not recompute business figures, and this
 * is the page most tempted to.
 *
 * The breakdown is one table rather than two, with the earnings and expenses
 * sections headed, so "Net for period" is the foot of a single reckoning
 * rather than a third number floating beside two others.
 */
export function OverviewPage() {
  const { dashboard, isLoading, error } = useDashboard('MONTH');

  if (error !== null) {
    return (
      <>
        <PageHeader kicker="Fig. 01 — Position" title="Overview" />
        <EmptyState title="The overview could not be loaded" message={error.message} />
      </>
    );
  }

  if (isLoading || dashboard === undefined) {
    return (
      <>
        <PageHeader kicker="Fig. 01 — Position" title="Overview" />
        <p className={styles.status}>Loading your position…</p>
      </>
    );
  }

  const { position, totals, period } = dashboard;
  const totalMoneyNow = fromMinorUnits(position.totalMoneyNow);
  const netReal = fromMinorUnits(totals.netReal);

  const breakdown = [
    ...dashboard.earnings.map((row, index) => ({
      ...row,
      ...(index === 0 ? { groupHeading: 'Earnings' } : {}),
    })),
    ...dashboard.expenses.map((row, index) => ({
      ...row,
      ...(index === 0 ? { groupHeading: 'Expenses' } : {}),
    })),
  ];

  return (
    <>
      <PageHeader kicker={`Fig. 01 — Position, ${period.label}`} title="Overview" />

      <div className={styles.kpis}>
        <KpiCard
          kicker="Total money now"
          value={<MoneyText amount={totalMoneyNow} />}
          note="available minus everything owed"
          tone={isNegative(totalMoneyNow) ? 'negative' : 'neutral'}
        />
        <KpiCard
          kicker="Available now"
          value={<MoneyText amount={fromMinorUnits(position.availableNow)} />}
          note={
            position.borrowed > 0
              ? `includes ${formatMoney(fromMinorUnits(position.borrowed))} borrowed`
              : 'cash + bank + savings'
          }
        />
        <KpiCard
          kicker="Owed"
          value={<MoneyText amount={fromMinorUnits(position.owed)} />}
          note={`card ${formatMoney(fromMinorUnits(position.owedOnCards))} · instalments ${formatMoney(fromMinorUnits(position.owedOnInstalments))} · loans ${formatMoney(fromMinorUnits(position.owedOnLoans))}`}
          tone="negative"
        />
        <KpiCard
          kicker={`Net, ${period.label}`}
          value={<MoneyText amount={netReal} />}
          note="earned minus spent this period"
          tone={isNegative(netReal) ? 'negative' : 'accent'}
        />
      </div>

      <div className={styles.main}>
        <Panel title="Breakdown — planned against real" meta="Ghost row = planned">
          <PlanVsRealTable
            caption="Earnings and expenses, planned against real"
            rows={breakdown.map(toTableRow)}
            totalLabel="Net for period"
            totalType="EARNING"
            totalPlanned={fromMinorUnits(totals.netPlanned)}
            totalReal={netReal}
          />
        </Panel>

        <div className={styles.side}>
          <Panel title="Upcoming">
            <ul className={styles.upcoming} aria-label="Upcoming payments">
              {dashboard.upcoming.map((payment) => (
                <li key={payment.key} className={styles.upcomingRow}>
                  <span className={styles.upcomingDay}>
                    {formatDate(fromIso(payment.date), 'DD-MM-YYYY').slice(0, 5)}
                  </span>
                  <span>
                    {payment.label}
                    <span className={styles.upcomingDetail}>{payment.detail}</span>
                  </span>
                  <MoneyText amount={fromMinorUnits(payment.amount)} signed />
                </li>
              ))}
            </ul>
          </Panel>

          <Panel title="Where it went" subtitle="Real spend, the tick marks the plan">
            {dashboard.categorySpend.map((entry) => (
              <div key={entry.categoryId} className={styles.spendRow}>
                <div className={styles.spendHead}>
                  <span>{entry.label}</span>
                  <MoneyText amount={fromMinorUnits(entry.real)} />
                </div>
                <ProgressBar
                  label={`${entry.label} spend against plan`}
                  value={entry.percentOfLargest}
                  paceMarker={entry.plannedPercentOfLargest}
                />
              </div>
            ))}
          </Panel>
        </div>
      </div>

      <ul className={styles.accounts}>
        {dashboard.accounts.map((account) => (
          <li key={account.id}>
            <AccountCard
              name={account.name}
              kind={account.kind}
              balance={fromMinorUnits(account.balance)}
              currency={account.currency}
              includedInTotals={account.includeInTotals}
              {...(account.note === null ? {} : { note: account.note })}
            />
          </li>
        ))}
      </ul>
    </>
  );
}
