import type { NavItem } from '../../types/navigation';

export interface BottomTabBarProps {
  /** The primary destinations only — setup pages move to the top bar (spec §5). */
  readonly items: readonly NavItem[];
}
