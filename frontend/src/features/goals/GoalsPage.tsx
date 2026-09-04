import { EmptyState } from '../../components/EmptyState';
import { MoneyText } from '../../components/MoneyText';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { ProgressBar } from '../../components/ProgressBar';
import { TagChip } from '../../components/TagChip';
import { WhatIfPanel } from '../../components/WhatIfPanel';
import { format as formatDate, fromIso, today } from '../../lib/dates';
import { fromMinorUnits } from '../../lib/money';

import styles from './GoalsPage.module.css';
import { useGoals } from './hooks';

/**
 * Saving toward a target (BR-11).
 *
 * Each goal shows progress against a pace marker, because "30% saved" alone
 * does not say whether that is enough — the gap between the fill and the tick
 * is the answer, and `ProgressBar` states it in words for anyone who cannot
 * see the line.
 *
 * Selecting a goal points the what-if at it. The panel then recomputes on
 * every drag from `lib/goals`, which is the case spec §5 names outright.
 */
export function GoalsPage() {
  const {
    goals,
    selected,
    select,
    months,
    frequency,
    setMonths,
    setFrequency,
    monthlySpare,
    isLoading,
    error,
  } = useGoals();

  return (
    <>
      <PageHeader kicker="Fig. 04 — Saving toward a target" title="Plan acquisition" />

      {isLoading && <p className={styles.status}>Loading goals…</p>}

      {error !== null && <EmptyState title="Goals could not be loaded" message={error.message} />}

      {!isLoading && error === null && goals.length === 0 && (
        <EmptyState
          title="No goals yet"
          message="Set a target and a date, and the plan will say what it takes to reach it."
        />
      )}

      {goals.length > 0 && (
        <div className={styles.layout}>
          <ul className={styles.list}>
            {goals.map((goal) => (
              <li key={goal.id}>
                <button
                  type="button"
                  className={styles.goal}
                  aria-pressed={goal.id === selected?.id}
                  onClick={() => {
                    select(goal.id);
                  }}
                >
                  <Panel>
                    <div className={styles.head}>
                      <span className={styles.identity}>
                        <span className={styles.rank}>{String(goal.rank).padStart(2, '0')}</span>
                        <span className={styles.name}>{goal.name}</span>
                      </span>
                      <TagChip variant={goal.onPace ? 'accent' : 'neutral'}>
                        {goal.onPace ? 'On pace' : 'Behind'}
                      </TagChip>
                    </div>

                    <span className={styles.amounts}>
                      <span>
                        <MoneyText amount={fromMinorUnits(goal.savedAmount)} /> of{' '}
                        <MoneyText amount={fromMinorUnits(goal.targetAmount)} />
                      </span>
                      <span>target {formatDate(fromIso(goal.targetDate))}</span>
                    </span>

                    <ProgressBar
                      label={`${goal.name} progress`}
                      value={goal.progressPercent}
                      paceMarker={goal.pacePercent}
                    />

                    <span className={styles.foot}>
                      <span>{goal.progressPercent}% saved</span>
                      <span>
                        <MoneyText amount={fromMinorUnits(goal.contributionPerPeriod)} /> needed
                        monthly
                      </span>
                    </span>
                  </Panel>
                </button>
              </li>
            ))}
          </ul>

          {selected !== undefined && (
            <WhatIfPanel
              goalName={selected.name}
              targetAmount={fromMinorUnits(selected.targetAmount)}
              savedAmount={fromMinorUnits(selected.savedAmount)}
              months={months}
              frequency={frequency}
              monthlySpare={monthlySpare}
              today={today()}
              onMonthsChange={setMonths}
              onFrequencyChange={setFrequency}
              onApply={() => {
                // Committing a new horizon is spec §6 step 9's write path; the
                // panel is read-only until it exists.
              }}
            />
          )}
        </div>
      )}
    </>
  );
}
