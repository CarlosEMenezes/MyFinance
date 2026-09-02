import { NavLink } from 'react-router-dom';

import styles from './BottomTabBar.module.css';
import type { BottomTabBarProps } from './BottomTabBar.types';

/**
 * The phone shell navigation, below the 940px breakpoint (spec §5).
 *
 * It carries only the primary destinations; the setup pages move to an icon
 * row in the top bar, because four thumb-sized targets is the most a tab bar
 * can hold without becoming a menu.
 */
export function BottomTabBar({ items }: BottomTabBarProps) {
  return (
    <nav aria-label="Main" className={['tabbar', styles.bar].filter(Boolean).join(' ')}>
      {items.map((item) => {
        const badge = item.badge ?? 0;

        return (
          <NavLink key={item.to} to={item.to} className={styles.tab ?? ''} end={item.to === '/'}>
            <span className={styles.icon}>{item.icon}</span>
            {item.label}
            {badge > 0 && (
              <span className={styles.badge}>
                {badge}
                <span className="sr-only"> unread</span>
              </span>
            )}
          </NavLink>
        );
      })}
    </nav>
  );
}
