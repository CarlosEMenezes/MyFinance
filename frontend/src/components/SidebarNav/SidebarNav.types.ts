import type { NavItem } from '../../types/navigation';

export interface SidebarNavProps {
  /** The pages the app is for. */
  readonly primary: readonly NavItem[];
  /** The pages that configure it. */
  readonly setup: readonly NavItem[];
  readonly onLogEntry: () => void;
  /** Ends the session. Placed with the profile, not among the pages. */
  readonly onSignOut: () => void;
  readonly defaultCurrency: string;
  /** When FX rates were last pulled (BR-8). */
  readonly fxUpdatedAt: string;
}
