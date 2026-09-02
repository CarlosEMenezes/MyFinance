import type { ReactNode } from 'react';

/**
 * One destination in the app shell. Shared by the sidebar and the bottom tab
 * bar so the two can never disagree about what the app contains.
 *
 * The icon arrives as a node rather than a name, so the nav components stay
 * presentational and the icon set is chosen once, by the shell.
 */
export interface NavItem {
  /** Route path, e.g. `/expenses`. */
  readonly to: string;
  readonly label: string;
  readonly icon: ReactNode;
  /** Unread count, shown as a badge. Omitted or zero shows nothing (BR-12). */
  readonly badge?: number;
}
