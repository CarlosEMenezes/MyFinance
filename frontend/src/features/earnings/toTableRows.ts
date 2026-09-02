import { fromMinorUnits } from '../../lib/money';
import type { PlanRow as ApiPlanRow } from '../../types/api';
import type { PlanVsRealRow } from '../../components/PlanVsRealTable';

/**
 * The API's `PlanRow` in minor units becomes the table's row in `Money`.
 *
 * `derived` becomes `locked`: BR-14 requires a derived row — loan repayments,
 * card instalments — to be text rather than an input, because there is nothing
 * on it to edit.
 */
export function toTableRow(row: ApiPlanRow & { groupHeading?: string }): PlanVsRealRow {
  const planNote =
    row.occurrencesInPeriod === 1
      ? 'once this period'
      : `each ${row.frequency.toLowerCase()} × ${String(row.occurrencesInPeriod)}`;

  return {
    id: row.categoryId,
    category: row.category,
    type: row.type,
    planned: fromMinorUnits(row.planned),
    real: fromMinorUnits(row.real),
    planNote,
    ...(row.derived ? { locked: true } : { perOccurrence: fromMinorUnits(row.perOccurrence) }),
    ...(row.paidWith === null ? {} : { paidWith: row.paidWith }),
    ...(row.dueNote === null ? {} : { dueNote: row.dueNote }),
    ...(row.foreignAmount === null ? {} : { foreignAmount: row.foreignAmount }),
    ...(row.groupHeading === undefined ? {} : { groupHeading: row.groupHeading }),
  };
}
