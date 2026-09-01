import { EditablePlanCell } from '../EditablePlanCell';
import { GhostPlanRow } from '../GhostPlanRow';
import { MoneyText } from '../MoneyText';
import { VarianceText } from '../VarianceText';

import styles from './PlanVsRealTable.module.css';
import type { PlanVsRealRow, PlanVsRealTableProps } from './PlanVsRealTable.types';

/**
 * Every category as a plan, a reality and the variance between them - the idea
 * the whole product turns on (BR-9).
 *
 * A real `<table>` rather than the prototype's CSS grid. The columns then align
 * without arithmetic, the headers are associated with their cells for a screen
 * reader, and each category reads as a pair of rows rather than as eight
 * unlabelled boxes.
 *
 * Grouping and sorting are not done here. The caller supplies rows already in
 * order with `groupHeading` set on the first of each group, because BR-15 makes
 * those view state that the page owns.
 */

const COLUMNS_WITHOUT_PAYMENT_METHOD = 4;
const COLUMNS_WITH_PAYMENT_METHOD = 5;

export function PlanVsRealTable({
  caption,
  rows,
  showPaymentMethod = false,
  totalLabel,
  totalType,
  totalPlanned,
  totalReal,
  currency = 'EUR',
  onPlanChange,
}: PlanVsRealTableProps) {
  const columnCount = showPaymentMethod
    ? COLUMNS_WITH_PAYMENT_METHOD
    : COLUMNS_WITHOUT_PAYMENT_METHOD;

  const renderPlannedCell = (row: PlanVsRealRow) => {
    // BR-14: a derived row has nothing to edit, so it is text. Without a change
    // handler nothing is editable either.
    if (row.locked === true || row.perOccurrence === undefined || onPlanChange === undefined) {
      return <MoneyText amount={row.planned} currency={currency} />;
    }
    return (
      <EditablePlanCell
        label={`Planned amount for ${row.category}`}
        value={row.perOccurrence}
        onChange={(value) => {
          onPlanChange(row.id, value);
        }}
      />
    );
  };

  return (
    <table className={styles.table}>
      <caption className="text-muted">{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Category</th>
          {showPaymentMethod && <th scope="col">Paid with</th>}
          <th scope="col" className={styles.numeric}>
            Planned / each
          </th>
          <th scope="col" className={styles.numeric}>
            Real
          </th>
          <th scope="col" className={styles.numeric}>
            Variance
          </th>
        </tr>
      </thead>

      {rows.map((row) => (
        <tbody key={row.id}>
          {row.groupHeading !== undefined && (
            <tr className={styles.groupHeading}>
              <th scope="rowgroup" colSpan={columnCount}>
                {row.groupHeading}
              </th>
            </tr>
          )}

          <GhostPlanRow
            note={row.planNote}
            showPaymentMethod={showPaymentMethod}
            dueNote={row.dueNote}
          >
            {renderPlannedCell(row)}
          </GhostPlanRow>

          <tr className={styles.realRow}>
            <th scope="row">
              <span className={styles.category}>
                {row.category}
                {row.foreignAmount !== undefined && (
                  <span className="tag tag-outline">{row.foreignAmount}</span>
                )}
              </span>
            </th>
            {showPaymentMethod && <td className={styles.paidWith}>{row.paidWith}</td>}
            <td />
            <td className={styles.numeric}>
              <MoneyText amount={row.real} currency={currency} />
            </td>
            <td className={styles.numeric}>
              <VarianceText
                type={row.type}
                planned={row.planned}
                real={row.real}
                currency={currency}
              />
            </td>
          </tr>
        </tbody>
      ))}

      <tfoot>
        <tr className={styles.totals}>
          <th scope="row">{totalLabel}</th>
          {showPaymentMethod && <td />}
          <td className={styles.numeric}>
            <MoneyText amount={totalPlanned} currency={currency} className={styles.totalPlanned} />
          </td>
          <td className={styles.numeric}>
            <MoneyText amount={totalReal} currency={currency} />
          </td>
          <td className={styles.numeric}>
            <VarianceText
              type={totalType}
              planned={totalPlanned}
              real={totalReal}
              currency={currency}
            />
          </td>
        </tr>
      </tfoot>
    </table>
  );
}
