import type { ReactNode } from 'react';

export interface PageHeaderProps {
  /** The figure line above the title, e.g. "Fig. 03 — Outgoings and card timing". */
  readonly kicker: string;
  readonly title: string;
  /** Period controls. Omitted on the pages spec §3 gives no period to. */
  readonly children?: ReactNode;
}
