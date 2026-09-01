import type { ReactNode } from 'react';

/**
 * The design system is a mono palette: its `accent-2` ramp is a machine-derived
 * stand-in that reads the same as `accent`, so it is deliberately not offered
 * here. Three variants is the whole vocabulary.
 */
export type TagVariant = 'accent' | 'neutral' | 'outline';

export interface TagChipProps {
  readonly children: ReactNode;
  readonly variant?: TagVariant;
  readonly className?: string | undefined;
}
