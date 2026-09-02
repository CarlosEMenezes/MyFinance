import { NavLink } from 'react-router-dom';

import type { NavItem } from '../../types/navigation';

import styles from './SidebarNav.module.css';
import type { SidebarNavProps } from './SidebarNav.types';

/**
 * The desktop shell navigation.
 *
 * Real links rather than the prototype's buttons, so the browser's own
 * behaviour survives: the back button works, a destination can be opened in a
 * new tab, and `NavLink` marks the current page with `aria-current` rather
 * than leaving the accent fill to carry that meaning alone.
 *
 * Below 940px this is hidden and `BottomTabBar` takes over (spec §5).
 */

function NavRow({ item }: { readonly item: NavItem }) {
  const badge = item.badge ?? 0;

  return (
    // NavLink's className cannot admit undefined, and a CSS-module lookup is
    // `string | undefined` under noUncheckedIndexedAccess, so it is coerced here.
    <NavLink to={item.to} className={styles.link ?? ''} end={item.to === '/'}>
      <span className={styles.icon}>{item.icon}</span>
      <span className={styles.label}>{item.label}</span>
      {badge > 0 && (
        <span className={styles.badge}>
          {badge}
          {/* A bare number beside a label reads as nonsense aloud. */}
          <span className="sr-only"> unread</span>
        </span>
      )}
    </NavLink>
  );
}

export function SidebarNav({
  primary,
  setup,
  onLogEntry,
  defaultCurrency,
  fxUpdatedAt,
}: SidebarNavProps) {
  return (
    <aside className={styles.side}>
      <p className={styles.brand}>BUDGET TRACKER</p>
      <p className={styles.kicker}>Fig. 01 — Planning</p>

      <nav aria-label="Main" className={styles.group}>
        {primary.map((item) => (
          <NavRow key={item.to} item={item} />
        ))}
      </nav>

      <div className={styles.setup}>
        <p className={styles.setupLabel}>Setup</p>
        <nav aria-label="Setup" className={styles.group}>
          {setup.map((item) => (
            <NavRow key={item.to} item={item} />
          ))}
        </nav>
      </div>

      <div className={styles.footer}>
        <button type="button" className="btn btn-primary btn-block" onClick={onLogEntry}>
          + Log entry
        </button>
        <p className={styles.fx}>
          Default currency {defaultCurrency}
          <br />
          Live FX updated {fxUpdatedAt}
        </p>
      </div>
    </aside>
  );
}
