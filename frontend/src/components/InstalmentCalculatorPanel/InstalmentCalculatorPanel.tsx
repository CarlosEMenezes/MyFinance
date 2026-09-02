import type { Frequency } from '../../lib/period';
import { SegmentedControl } from '../SegmentedControl';

import styles from './InstalmentCalculatorPanel.module.css';
import type { InstalmentCalculatorPanelProps } from './InstalmentCalculatorPanel.types';
import { financeFigures } from './useFinanceFigures';

/**
 * What financing this actually costs, answered while the user is still typing.
 *
 * The same panel serves both sides of BR-6 and BR-7, because spreading a
 * purchase and taking a loan are the same annuity; only the wording and which
 * side of the position moves differ. Borrowing shows both sides moving (BR-2),
 * because the whole point is that the money arriving is not income.
 *
 * The figures are computed here from `lib/`, which ADR-7 permits for money the
 * user has not committed. On save the server recomputes and is authoritative.
 */

const FREQUENCIES = [
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'FORTNIGHTLY', label: 'Fortnightly' },
  { value: 'MONTHLY', label: 'Monthly' },
] as const satisfies readonly { value: Frequency; label: string }[];

export function InstalmentCalculatorPanel({
  mode,
  principal,
  instalmentCount,
  instalmentAmount,
  frequency,
  onInstalmentCountChange,
  onInstalmentAmountChange,
  onFrequencyChange,
  firstDueDate,
  currency = 'EUR',
  dateFormat = 'DD-MM-YYYY',
}: InstalmentCalculatorPanelProps) {
  const isLoan = mode === 'LOAN';
  const figures = financeFigures(
    mode,
    principal,
    instalmentCount,
    instalmentAmount,
    frequency,
    currency,
    firstDueDate,
    dateFormat,
  );

  return (
    <div className={styles.panel}>
      <p className={styles.kicker}>{isLoan ? 'Loan terms' : 'Instalment plan'}</p>

      <div className={styles.terms}>
        <label className="field">
          {isLoan ? 'Duration in months' : 'Number of instalments'}
          <input
            className="input"
            type="text"
            inputMode="numeric"
            value={instalmentCount}
            onChange={(event) => {
              onInstalmentCountChange(event.target.value);
            }}
          />
        </label>
        <label className="field">
          Amount per instalment
          <input
            className="input"
            type="text"
            inputMode="decimal"
            value={instalmentAmount}
            onChange={(event) => {
              onInstalmentAmountChange(event.target.value);
            }}
          />
        </label>
      </div>

      <SegmentedControl
        className={styles.frequency}
        name={`${mode.toLowerCase()}-frequency`}
        label="Repayment frequency"
        options={FREQUENCIES}
        value={frequency}
        onChange={onFrequencyChange}
      />

      {figures !== null && (
        <>
          <ul className={styles.figures}>
            {figures.lines.map((line) => (
              <li key={line.label} className={styles.figure}>
                <span className={styles.figureLabel}>{line.label}</span>
                <span
                  className={['tabular', line.tone === undefined ? undefined : styles[line.tone]]
                    .filter(Boolean)
                    .join(' ')}
                >
                  {line.value}
                </span>
              </li>
            ))}
          </ul>
          <p
            className={[styles.verdict, figures.interestFree ? styles.verdictFree : undefined]
              .filter(Boolean)
              .join(' ')}
          >
            {figures.verdict}
          </p>
        </>
      )}
    </div>
  );
}
