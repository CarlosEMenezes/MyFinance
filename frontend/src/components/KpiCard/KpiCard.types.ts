import type { ReactNode } from 'react';

/**
 * How the headline figure reads. Never chosen by eye — `negative` is the
 * over-plan red of BR-9, used for money owed and for a negative total
 * position; `accent` is the steel used for goal progress; `muted` is a figure
 * shown only for reference, such as a planned total beside a real one.
 */
export type KpiTone = 'neutral' | 'negative' | 'accent' | 'muted';

export interface KpiCardProps {
  /** Small uppercase label, e.g. "Total money now". */
  readonly kicker: string;
  /**
   * The headline figure. A node rather than a string so a page can pass a
   * `MoneyText` and keep formatting at the single edge that owns it.
   */
  readonly value: ReactNode;
  /** Supporting line, e.g. "available minus everything owed". */
  readonly note?: string;
  readonly tone?: KpiTone;
}
