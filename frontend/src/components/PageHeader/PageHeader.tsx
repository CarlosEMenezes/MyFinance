import styles from './PageHeader.module.css';
import type { PageHeaderProps } from './PageHeader.types';

/**
 * The top of every page: what you are looking at, and the controls that change
 * what it covers.
 *
 * A `banner` landmark carrying the page's only `h1`, so the title is a
 * reliable jump target rather than one heading among several.
 */
export function PageHeader({ kicker, title, children }: PageHeaderProps) {
  return (
    <header className={styles.header}>
      <div>
        <p className={styles.kicker}>{kicker}</p>
        <h1 className={styles.title}>{title}</h1>
      </div>
      {children !== undefined && <div className={styles.controls}>{children}</div>}
    </header>
  );
}
