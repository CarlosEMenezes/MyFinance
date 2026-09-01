import styles from './ProgressBar.module.css';
import type { ProgressBarProps } from './ProgressBar.types';

/**
 * A bar drawn as a wireframe object: a hairline frame with a solid fill, and
 * optionally a tick showing where the plan expected to be by now.
 *
 * The pace marker is the reason this is not a plain `<progress>`: the useful
 * question is not "how far along?" but "am I ahead or behind?", and that is
 * answered by the distance between the fill and the tick.
 */

const MINIMUM = 0;
const MAXIMUM = 100;

const clamp = (value: number): number => Math.min(MAXIMUM, Math.max(MINIMUM, Math.round(value)));

export function ProgressBar({
  label,
  value,
  paceMarker,
  tone = 'accent',
  className,
}: ProgressBarProps) {
  const filled = clamp(value);
  const pace = paceMarker === undefined ? undefined : clamp(paceMarker);

  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuenow={filled}
      aria-valuemin={MINIMUM}
      aria-valuemax={MAXIMUM}
      // A lone tick is meaningless read aloud, so the comparison it exists to
      // make is spelled out instead.
      {...(pace === undefined
        ? {}
        : { 'aria-valuetext': `${String(filled)}% saved, ${String(pace)}% expected by now` })}
      className={[styles.track, className].filter(Boolean).join(' ')}
    >
      <span
        data-testid="fill"
        className={[styles.fill, styles[tone]].filter(Boolean).join(' ')}
        style={{ width: `${String(filled)}%` }}
      />
      {pace !== undefined && (
        <span data-testid="pace" className={styles.pace} style={{ left: `${String(pace)}%` }} />
      )}
    </div>
  );
}
