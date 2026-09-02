import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { PlanVsRealTable } from '../../components/PlanVsRealTable';
import { SegmentedControl } from '../../components/SegmentedControl';
import type { GroupBy, SortBy } from '../../lib/planRows';

import styles from './EarningsPage.module.css';
import { useEarnings } from './hooks';
import { toTableRow } from './toTableRows';

/**
 * Income logged against income planned (BR-9, BR-15).
 *
 * Earnings group by category group or by frequency, and never by payment
 * method — money arriving does not have one. That is why BR-15 gives the three
 * tables different axes rather than one shared set.
 */

const GROUP_OPTIONS = [
  { value: 'NONE', label: 'None' },
  { value: 'GROUP', label: 'Group' },
  { value: 'FREQUENCY', label: 'Frequency' },
] as const satisfies readonly { value: GroupBy; label: string }[];

const SORT_OPTIONS = [
  { value: 'CATEGORY', label: 'Category' },
  { value: 'PLANNED', label: 'Planned' },
  { value: 'REAL', label: 'Real' },
  { value: 'VARIANCE', label: 'Variance' },
] as const satisfies readonly { value: SortBy; label: string }[];

export function EarningsPage() {
  const {
    rows,
    plannedTotal,
    realTotal,
    groupBy,
    sortBy,
    setGroupBy,
    setSortBy,
    isLoading,
    error,
  } = useEarnings();

  return (
    <>
      <PageHeader kicker="Fig. 02 — Income logged and planned" title="Earnings" />

      {isLoading && <p className={styles.status}>Loading earnings…</p>}

      {error !== null && (
        <EmptyState title="Earnings could not be loaded" message={error.message} />
      )}

      {!isLoading && error === null && rows.length === 0 && (
        <EmptyState
          title="Nothing earned yet"
          message="Log an earning, or set a planned amount on an earning category."
        />
      )}

      {rows.length > 0 && (
        <Panel title="Earnings by category">
          <div className={styles.controls}>
            <SegmentedControl
              name="earnings-group"
              label="Group"
              options={GROUP_OPTIONS}
              value={groupBy}
              onChange={setGroupBy}
            />
            <SegmentedControl
              name="earnings-sort"
              label="Sort"
              options={SORT_OPTIONS}
              value={sortBy}
              onChange={setSortBy}
            />
          </div>

          <PlanVsRealTable
            caption="Earnings by category, planned against real"
            rows={rows.map(toTableRow)}
            totalLabel="Total earned"
            totalType="EARNING"
            totalPlanned={plannedTotal}
            totalReal={realTotal}
          />
        </Panel>
      )}
    </>
  );
}
