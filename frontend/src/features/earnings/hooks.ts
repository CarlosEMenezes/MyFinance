import { useMemo, useState } from 'react';

import { arrangeRows, type GroupBy, type SortBy } from '../../lib/planRows';
import { fromMinorUnits, type Money } from '../../lib/money';
import type { PeriodKind, PlanRow } from '../../types/api';
import { useDashboard } from '../dashboard/hooks';

export interface EarningsView {
  readonly rows: readonly (PlanRow & { groupHeading?: string })[];
  readonly plannedTotal: Money;
  readonly realTotal: Money;
  readonly periodLabel: string;
  readonly groupBy: GroupBy;
  readonly sortBy: SortBy;
  readonly setGroupBy: (groupBy: GroupBy) => void;
  readonly setSortBy: (sortBy: SortBy) => void;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

/**
 * BR-15: grouping and sorting are view state, owned here rather than by the
 * table. The table renders whatever order it is handed, which is why the same
 * component serves Earnings and Expenses on different axes.
 */
export function useEarnings(period: PeriodKind = 'MONTH'): EarningsView {
  const { dashboard, isLoading, error } = useDashboard(period);
  const [groupBy, setGroupBy] = useState<GroupBy>('NONE');
  const [sortBy, setSortBy] = useState<SortBy>('CATEGORY');

  const rows = useMemo(
    () =>
      arrangeRows(
        (dashboard?.earnings ?? []).map((row) => ({ ...row, id: row.categoryId })),
        { groupBy, sortBy },
      ),
    [dashboard, groupBy, sortBy],
  );

  return {
    rows,
    plannedTotal: fromMinorUnits(dashboard?.totals.earningsPlanned ?? 0),
    realTotal: fromMinorUnits(dashboard?.totals.earningsReal ?? 0),
    periodLabel: dashboard?.period.label ?? '',
    groupBy,
    sortBy,
    setGroupBy,
    setSortBy,
    isLoading,
    error,
  };
}
