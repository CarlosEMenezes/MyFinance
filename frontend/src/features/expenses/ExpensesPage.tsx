import { EmptyState } from '../../components/EmptyState';
import { FilterChips } from '../../components/FilterChips';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { PlanVsRealTable } from '../../components/PlanVsRealTable';
import { SegmentedControl } from '../../components/SegmentedControl';
import { toMinorUnits } from '../../lib/money';
import type { GroupBy, SortBy } from '../../lib/planRows';
import { useUpdatePlanAmount } from '../dashboard/hooks';
import { toTableRow } from '../earnings/toTableRows';

import styles from './ExpensesPage.module.css';
import { ALL_METHODS, useExpenses } from './hooks';

/**
 * Outgoings against plan (BR-9, BR-14, BR-15).
 *
 * Expenses group by account where earnings cannot, because money leaving has a
 * payment method and money arriving does not. The filter is the reason BR-15
 * distinguishes displayed totals from dashboard totals: with one active, the
 * figure at the foot of the table describes what is on screen.
 */

const GROUP_OPTIONS = [
  { value: 'NONE', label: 'None' },
  { value: 'GROUP', label: 'Group' },
  { value: 'ACCOUNT', label: 'Account' },
] as const satisfies readonly { value: GroupBy; label: string }[];

const SORT_OPTIONS = [
  { value: 'CATEGORY', label: 'Category' },
  { value: 'PLANNED', label: 'Planned' },
  { value: 'REAL', label: 'Real' },
  { value: 'VARIANCE', label: 'Variance' },
] as const satisfies readonly { value: SortBy; label: string }[];

export function ExpensesPage() {
  const {
    rows,
    paymentMethods,
    plannedTotal,
    realTotal,
    isFiltered,
    groupBy,
    sortBy,
    paidWith,
    setGroupBy,
    setSortBy,
    setPaidWith,
    isLoading,
    error,
  } = useExpenses();
  const { mutate } = useUpdatePlanAmount('MONTH');

  return (
    <>
      <PageHeader kicker="Fig. 03 — Outgoings and card timing" title="Expenses" />

      {isLoading && <p className={styles.status}>Loading expenses…</p>}

      {error !== null && (
        <EmptyState title="Expenses could not be loaded" message={error.message} />
      )}

      {!isLoading && error === null && rows.length === 0 && !isFiltered && (
        <EmptyState
          title="Nothing spent yet"
          message="Log an expense, or set a planned amount on an expense category."
        />
      )}

      {(rows.length > 0 || isFiltered) && (
        <Panel title="Expenses by category">
          <div className={styles.controls}>
            <SegmentedControl
              name="expenses-group"
              label="Group"
              options={GROUP_OPTIONS}
              value={groupBy}
              onChange={setGroupBy}
            />
            <SegmentedControl
              name="expenses-sort"
              label="Sort"
              options={SORT_OPTIONS}
              value={sortBy}
              onChange={setSortBy}
            />
          </div>

          <FilterChips
            className={styles.filter}
            name="expenses-paid-with"
            label="Paid with"
            options={[
              { value: ALL_METHODS, label: 'All' },
              ...paymentMethods.map((name) => ({ value: name, label: name })),
            ]}
            value={paidWith}
            onChange={setPaidWith}
          />

          {isFiltered && (
            <p className={styles.filterNote}>
              Totals below cover {paidWith} only, not the whole period.
            </p>
          )}

          <PlanVsRealTable
            caption="Expenses by category, planned against real"
            rows={rows.map(toTableRow)}
            showPaymentMethod
            totalLabel="Total spent"
            totalType="EXPENSE"
            totalPlanned={plannedTotal}
            totalReal={realTotal}
            onPlanChange={(categoryId, amount) => {
              mutate({ categoryId, perOccurrence: toMinorUnits(amount) });
            }}
          />
        </Panel>
      )}
    </>
  );
}
