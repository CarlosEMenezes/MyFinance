import type { ReactNode } from 'react';

export interface FieldProps {
  readonly label: string;
  /**
   * The control. It receives the id, so the label points at it — which is why
   * this is a render prop rather than plain children: a wrapper cannot label a
   * control it has no way to name.
   */
  readonly children: (ids: { readonly id: string; readonly describedBy?: string }) => ReactNode;
  /** Explains the field. Described, never part of the accessible name (gotcha 26). */
  readonly hint?: string;
  /** What is wrong with what was entered. Announced when it appears. */
  readonly error?: string | undefined;
  readonly className?: string | undefined;
}
