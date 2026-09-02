import { EditablePlanCell } from '../../../components/EditablePlanCell';
import { MoneyText } from '../../../components/MoneyText';
import type { Money } from '../../../lib/money';
import type { Frequency } from '../../../lib/period';
import type { CategoryRowView } from '../hooks';

import styles from './CategoryTable.module.css';

/**
 * The plan itself, edited in place (BR-14).
 *
 * The occurrence count beside each row is what makes BR-10 legible: "€160 a
 * week" and "×5 this period" together explain a planned total that "€160"
 * alone does not, and the count follows a frequency change immediately.
 */

const FREQUENCIES: readonly { value: Frequency; label: string }[] = [
  { value: 'WEEKLY', label: 'week' },
  { value: 'FORTNIGHTLY', label: 'fortnight' },
  { value: 'MONTHLY', label: 'month' },
];

export interface CategoryTableProps {
  readonly caption: string;
  readonly rows: readonly CategoryRowView[];
  readonly groups: readonly string[];
  readonly onPlanChange: (id: string, amount: Money) => void;
  readonly onFrequencyChange: (id: string, frequency: Frequency) => void;
  readonly onGroupChange: (id: string, group: string) => void;
}

export function CategoryTable({
  caption,
  rows,
  groups,
  onPlanChange,
  onFrequencyChange,
  onGroupChange,
}: CategoryTableProps) {
  return (
    <table className={styles.table}>
      <caption className="text-muted">{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Category</th>
          <th scope="col">Group</th>
          <th scope="col" className={styles.numeric}>
            Planned
          </th>
          <th scope="col">Per</th>
          <th scope="col" className={styles.numeric}>
            Times
          </th>
          <th scope="col" className={styles.numeric}>
            This period
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id}>
            <th scope="row">{row.name}</th>
            <td>
              <select
                className={['input', styles.field].join(' ')}
                aria-label={`Group for ${row.name}`}
                value={row.group}
                onChange={(event) => {
                  onGroupChange(row.id, event.target.value);
                }}
              >
                {groups.map((group) => (
                  <option key={group} value={group}>
                    {group}
                  </option>
                ))}
              </select>
            </td>
            <td className={styles.numeric}>
              <EditablePlanCell
                label={`Planned amount for ${row.name}`}
                value={row.perOccurrence}
                onChange={(amount) => {
                  onPlanChange(row.id, amount);
                }}
              />
            </td>
            <td>
              <select
                className={['input', styles.field].join(' ')}
                aria-label={`Frequency for ${row.name}`}
                value={row.frequency}
                onChange={(event) => {
                  onFrequencyChange(row.id, event.target.value as Frequency);
                }}
              >
                {FREQUENCIES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </td>
            <td className={styles.occurrences}>×{row.occurrencesInPeriod}</td>
            <td className={styles.numeric}>
              <MoneyText amount={row.plannedInPeriod} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
