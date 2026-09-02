import type { ReactNode } from 'react';

export interface EmptyStateProps {
  readonly title: string;
  readonly message: string;
  /** The way out of the empty state, where there is one. */
  readonly action?: ReactNode;
}
