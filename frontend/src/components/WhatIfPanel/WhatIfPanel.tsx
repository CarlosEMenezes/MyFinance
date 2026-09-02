import { useId } from 'react';

import { addMonths, format as formatDate } from '../../lib/dates';
import { assessFeasibility, planGoal, type ContributionFrequency } from '../../lib/goals';
import { absolute, format as formatMoney, isNegative } from '../../lib/money';
import { MoneyText } from '../MoneyText';
import { Panel } from '../Panel';
import { SegmentedControl } from '../SegmentedControl';

import styles from './WhatIfPanel.module.css';
import type { WhatIfPanelProps } from './WhatIfPanel.types';

/**
 * BR-11's what-if: move the target date, change the rhythm, and see
 * immediately what it would take.
 *
 * The figures are computed here from `lib/goals`, which is the case spec §5
 * names outright — a slider that recomputes on every drag cannot ask the
 * server between frames. Only `monthlySpare` is supplied, because it depends
 * on the whole plan rather than on this goal.
 *
 * The feasibility line is the point of the panel. A required contribution on
 * its own is a number; set against what is actually spare it becomes a
 * decision, which is why it states either the surplus or the shortfall rather
 * than a yes or no.
 */

const FREQUENCIES = [
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
] as const satisfies readonly { value: ContributionFrequency; label: string }[];

const PERIOD_NOUN: Record<ContributionFrequency, string> = {
  DAILY: 'day',
  WEEKLY: 'week',
  MONTHLY: 'month',
};

const EARLIEST_MONTHS = 1;
const LATEST_MONTHS = 36;

export function WhatIfPanel({
  goalName,
  targetAmount,
  savedAmount,
  months,
  frequency,
  monthlySpare,
  today,
  onMonthsChange,
  onFrequencyChange,
  onApply,
  currency = 'EUR',
  dateFormat = 'DD-MM-YYYY',
}: WhatIfPanelProps) {
  const sliderId = useId();
  const plan = planGoal(targetAmount, savedAmount, frequency, months);
  const feasibility = assessFeasibility(plan.monthlyRequirement, monthlySpare);
  const targetDate = formatDate(addMonths(today, months), dateFormat);
  const shortfall = absolute(feasibility.spareAfterContributing);

  return (
    <Panel>
      <span className="card-kicker">What-if · {goalName}</span>
      <p className={styles.headline}>{formatMoney(plan.contributionPerPeriod, currency)}</p>
      <p className={styles.summary}>
        per {PERIOD_NOUN[frequency]} to close {formatMoney(plan.gap, currency)} by {targetDate}
      </p>

      <div className={styles.control}>
        <label className="field" htmlFor={sliderId}>
          Target date — {months} months out
        </label>
        <input
          id={sliderId}
          className={styles.slider}
          type="range"
          min={EARLIEST_MONTHS}
          max={LATEST_MONTHS}
          value={months}
          onChange={(event) => {
            onMonthsChange(Number(event.target.value));
          }}
        />
        <div className={styles.sliderBounds}>
          <span>1 mo</span>
          <span>36 mo</span>
        </div>
      </div>

      <SegmentedControl
        className={styles.control}
        name="goal-frequency"
        label="Save frequency"
        options={FREQUENCIES}
        value={frequency}
        onChange={onFrequencyChange}
      />

      <ul className={styles.breakdown} aria-label={`${goalName} breakdown`}>
        <li className={styles.line}>
          <span className={styles.lineLabel}>Goal total</span>
          <MoneyText amount={targetAmount} currency={currency} />
        </li>
        <li className={styles.line}>
          <span className={styles.lineLabel}>Already saved</span>
          <MoneyText amount={savedAmount} currency={currency} className={styles.saved} />
        </li>
        <li className={styles.line}>
          <span className={styles.lineLabel}>Still to save</span>
          <MoneyText amount={plan.gap} currency={currency} />
        </li>
        <li className={styles.line}>
          <span className={styles.lineLabel}>Per month equivalent</span>
          <MoneyText amount={plan.monthlyRequirement} currency={currency} />
        </li>
        <li className={styles.line}>
          <span className={styles.lineLabel}>Spare each month now</span>
          <MoneyText
            amount={monthlySpare}
            currency={currency}
            className={isNegative(monthlySpare) ? styles.bad : styles.good}
          />
        </li>
      </ul>

      <p className={styles.feasibility}>
        {feasibility.isAchievable
          ? `Fits inside your current spare ${formatMoney(monthlySpare, currency)} a month, with ${formatMoney(feasibility.spareAfterContributing, currency)} left over.`
          : `Needs ${formatMoney(shortfall, currency)} a month more than you have spare. Push the date out or cut a category.`}
      </p>

      <button
        type="button"
        className={['btn', 'btn-primary', 'btn-block', styles.apply].join(' ')}
        onClick={onApply}
      >
        Apply to plan
      </button>
    </Panel>
  );
}
