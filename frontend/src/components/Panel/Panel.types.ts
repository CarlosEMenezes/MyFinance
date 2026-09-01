import type { ReactNode } from 'react';

/**
 * How tightly the panel is padded. `compact` is the KPI-tile density from the
 * design; `comfortable` is every other panel.
 */
export type PanelDensity = 'comfortable' | 'compact';

export interface PanelProps {
  readonly children: ReactNode;
  /**
   * Rendered as a level-2 heading. The design draws it at the h4 step of the
   * type scale, but a panel sits directly under the page's h1, so the tag
   * follows the document outline and the size comes from CSS.
   */
  readonly title?: string;
  /** Small muted line under the title, e.g. "Rate per job or per hour". */
  readonly subtitle?: string;
  /** Right-aligned uppercase note on the title row, e.g. "Ghost row = planned". */
  readonly meta?: string;
  readonly density?: PanelDensity;
  /**
   * Layout-level classes only — grid placement, never appearance.
   *
   * Explicitly admits `undefined` because callers pass CSS-module lookups,
   * which `noUncheckedIndexedAccess` types as `string | undefined`. Without
   * this, `exactOptionalPropertyTypes` rejects every such call.
   */
  readonly className?: string | undefined;
}
