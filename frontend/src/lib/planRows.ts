/**
 * BR-15 view state: how a table of plan rows is ordered and grouped.
 *
 * This is presentation, not a business rule — it reorders figures the server
 * already computed and never changes one. It lives in `lib/` because both the
 * Earnings and Expenses pages need it and it is pure enough to test on its
 * own.
 *
 * The rows are never mutated: a sort in place would reorder the query cache,
 * and the next render would see a different list than the server sent.
 */

export type GroupBy = 'NONE' | 'GROUP' | 'FREQUENCY' | 'ACCOUNT';
export type SortBy = 'CATEGORY' | 'PLANNED' | 'REAL' | 'VARIANCE';

export interface ArrangeableRow {
  readonly id: string;
  readonly category: string;
  readonly group: string;
  readonly frequency: string;
  readonly planned: number;
  readonly real: number;
  readonly variance: number;
  readonly paidWith?: string | null;
}

export interface ArrangedRow<T> {
  readonly row: T;
  /** Set on the first row of each group, absent on the rest. */
  readonly groupHeading?: string;
}

export interface ArrangeOptions {
  readonly groupBy: GroupBy;
  readonly sortBy: SortBy;
}

const TITLE_CASE_FREQUENCY: Record<string, string> = {
  WEEKLY: 'Weekly',
  FORTNIGHTLY: 'Fortnightly',
  MONTHLY: 'Monthly',
};

function compareBy(sortBy: SortBy, a: ArrangeableRow, b: ArrangeableRow): number {
  switch (sortBy) {
    case 'PLANNED':
      return b.planned - a.planned;
    case 'REAL':
      return b.real - a.real;
    case 'VARIANCE':
      // By size, not by sign: a large miss matters whichever way it points.
      return Math.abs(b.variance) - Math.abs(a.variance);
    case 'CATEGORY':
      return a.category.localeCompare(b.category);
  }
}

function groupKey(groupBy: GroupBy, row: ArrangeableRow): string | undefined {
  switch (groupBy) {
    case 'GROUP':
      return row.group;
    case 'FREQUENCY':
      return TITLE_CASE_FREQUENCY[row.frequency] ?? row.frequency;
    case 'ACCOUNT':
      return row.paidWith ?? 'Unassigned';
    case 'NONE':
      return undefined;
  }
}

export function arrangeRows<T extends ArrangeableRow>(
  rows: readonly T[],
  { groupBy, sortBy }: ArrangeOptions,
): readonly (T & { groupHeading?: string })[] {
  const sorted = [...rows].sort((a, b) => compareBy(sortBy, a, b));

  if (groupBy === 'NONE') {
    return sorted;
  }

  // Grouping is the outer order; the chosen sort still decides order within.
  const grouped = [...sorted].sort((a, b) =>
    (groupKey(groupBy, a) ?? '').localeCompare(groupKey(groupBy, b) ?? ''),
  );

  let previous: string | undefined;
  return grouped.map((row) => {
    const key = groupKey(groupBy, row);
    const heading = key === previous ? undefined : key;
    previous = key;
    return heading === undefined ? row : { ...row, groupHeading: heading };
  });
}
