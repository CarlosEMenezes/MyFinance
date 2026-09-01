import { formatSigned } from '../../lib/money';
import { type VarianceTone, varianceOf } from '../../lib/variance';

import styles from './VarianceText.module.css';
import type { VarianceTextProps } from './VarianceText.types';

/**
 * The difference between a plan and what actually happened.
 *
 * BR-9 says a variance must never be coloured without applying the
 * convention, so this component takes the raw planned and real amounts and
 * derives both the figure and the tone itself. Passing in a pre-computed
 * colour is not possible, which is the point: there is no way to render a
 * variance and get the convention wrong.
 *
 * The sign is the same on both sides — `real − planned` always. Only the
 * colour differs: earning more than planned is good, spending more is bad.
 */

const TONE_CLASS: Record<VarianceTone, string | undefined> = {
  GOOD: styles.good,
  BAD: styles.bad,
  NEUTRAL: styles.neutral,
};

export function VarianceText({
  type,
  planned,
  real,
  currency = 'EUR',
  className,
}: VarianceTextProps) {
  const { amount, tone } = varianceOf(type, planned, real);

  return (
    <span className={['tabular', TONE_CLASS[tone], className].filter(Boolean).join(' ')}>
      {formatSigned(amount, currency)}
    </span>
  );
}
