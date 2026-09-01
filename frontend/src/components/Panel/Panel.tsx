import styles from './Panel.module.css';
import type { PanelProps } from './Panel.types';

/**
 * The wireframe frame every card, figure and section in the design wears:
 * square, transparent, hairline-bordered, with a `+` registration mark at
 * each corner.
 *
 * The design system is explicit that the marks are not optional — a framed
 * element without them is off-system — so `Panel` always draws all four and
 * no consumer can forget them. They are decorative, so they are hidden from
 * assistive technology.
 */

const CORNERS = ['tl', 'tr', 'bl', 'br'] as const;

export function Panel({
  title,
  subtitle,
  meta,
  density = 'comfortable',
  className,
  children,
}: PanelProps) {
  const hasHeader = title !== undefined || meta !== undefined;
  const classNames = ['blueprint', styles.panel, styles[density], className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNames}>
      {CORNERS.map((corner) => (
        <i key={corner} className={`corner ${corner}`} aria-hidden="true" />
      ))}

      {hasHeader && (
        <div className={styles.header}>
          {title !== undefined && <h2 className={styles.title}>{title}</h2>}
          {meta !== undefined && <span className={styles.meta}>{meta}</span>}
        </div>
      )}

      {subtitle !== undefined && <p className={styles.subtitle}>{subtitle}</p>}

      {children}
    </div>
  );
}
