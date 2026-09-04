import {
  AccountsIcon,
  CardsIcon,
  CategoriesIcon,
  EarningsIcon,
  ExpensesIcon,
  GoalsIcon,
  NotificationsIcon,
  OverviewIcon,
  SettingsIcon,
} from '../components/icons';
import type { NavItem } from '../types/navigation';

/**
 * The app's destinations, in the two groups spec §5 draws.
 *
 * Primary is what the app is *for*; setup is what configures it. The split is
 * not cosmetic: below 940px only the primary four fit in a tab bar, and the
 * setup pages move to the top bar. Defining the groups once means the sidebar
 * and the tab bar cannot come to disagree about which is which.
 *
 * Import (spec §1 page 10) is absent on purpose. It is step 13, in Phase 2,
 * and a nav entry pointing at a page that does not exist is worse than no
 * entry at all.
 */

export const PRIMARY_NAV: readonly NavItem[] = [
  { to: '/', label: 'Overview', icon: <OverviewIcon /> },
  { to: '/earnings', label: 'Earnings', icon: <EarningsIcon /> },
  { to: '/expenses', label: 'Expenses', icon: <ExpensesIcon /> },
  { to: '/goals', label: 'Acquisition', icon: <GoalsIcon /> },
];

const SETUP_NAV: readonly NavItem[] = [
  { to: '/accounts', label: 'Accounts', icon: <AccountsIcon /> },
  { to: '/cards', label: 'Cards', icon: <CardsIcon /> },
  { to: '/categories', label: 'Categories', icon: <CategoriesIcon /> },
  { to: '/notifications', label: 'Alerts', icon: <NotificationsIcon /> },
  { to: '/settings', label: 'Settings', icon: <SettingsIcon /> },
];

/**
 * BR-12: the unread count drives the badge. It is applied here rather than
 * baked into the list so there is one nav model and one place the count
 * enters it — the sidebar and the tab bar cannot show different numbers.
 */
export function setupNavWithBadge(unreadCount: number): readonly NavItem[] {
  return SETUP_NAV.map((item) =>
    item.to === '/notifications' ? { ...item, badge: unreadCount } : item,
  );
}
