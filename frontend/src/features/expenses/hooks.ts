import { useMemo, useState } from 'react';

import { fromMinorUnits, sum, type Money } from '../../lib/money';
import { arrangeRows, type GroupBy, type SortBy } from '../../lib/planRows';
import type { PeriodKind, PlanRow } from '../../types/api';
import { useDashboard } from '../dashboard/hooks';

export const ALL_METHODS = 'ALL';

export interface ExpensesView {
  readonly rows: readonly (PlanRow & { groupHeading?: string })[];
  readonly paymentMethods: readonly string[];
  readonly plannedTotal: Money;
  readonly realTotal: Money;
  /** True when the totals describe a filtered subset rather than the period. */
  readonly isFiltered: boolean;
  readonly groupBy: GroupBy;
  readonly sortBy: SortBy;
  readonly paidWith: string;
  readonly setGroupBy: (groupBy: GroupBy) => void;
  readonly setSortBy: (sortBy: SortBy) => void;
  readonly setPaidWith: (paidWith: string) => void;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

export function useExpenses(period: PeriodKind = 'MONTH'): ExpensesView {
  const { dashboard, isLoading, error } = useDashboard(period);
  const [groupBy, setGroupBy] = useState<GroupBy>('NONE');
  const [sortBy, setSortBy] = useState<SortBy>('CATEGORY');
  const [paidWith, setPaidWith] = useState<string>(ALL_METHODS);

  const all = useMemo(() => dashboard?.expenses ?? [], [dashboard]);
  const isFiltered = paidWith !== ALL_METHODS;
  const visible = useMemo(
    () => (isFiltered ? all.filter((row) => row.paidWith === paidWith) : all),
    [all, isFiltered, paidWith],
  );

  const rows = useMemo(
    () =>
      arrangeRows(
        visible.map((row) => ({ ...row, id: row.categoryId })),
        { groupBy, sortBy },
      ),
    [visible, groupBy, sortBy],
  );

  const paymentMethods = useMemo(
    () => [...new Set(all.map((row) => row.paidWith).filter((name) => name !== null))].sort(),
    [all],
  );

  /**
   * BR-15: displayed totals respect the active filter, while the dashboard's
   * own totals always cover the whole period. Unfiltered, the server's figure
   * is authoritative and is used as-is; filtered, only the client knows what
   * is on screen, so the visible rows are summed.
   */
  const plannedTotal = isFiltered
    ? sum(visible.map((row) => fromMinorUnits(row.planned)))
    : fromMinorUnits(dashboard?.totals.expensesPlanned ?? 0);
  const realTotal = isFiltered
    ? sum(visible.map((row) => fromMinorUnits(row.real)))
    : fromMinorUnits(dashboard?.totals.expensesReal ?? 0);

  return {
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
  };
}
