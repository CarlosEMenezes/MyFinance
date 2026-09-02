import type { ReactNode } from 'react';

export interface DialogProps {
  readonly open: boolean;
  /** Names the dialog for assistive technology and heads it on screen. */
  readonly title: string;
  /** Called for Escape, a backdrop click, or a cancel action. */
  readonly onClose: () => void;
  readonly children: ReactNode;
  /** Footer buttons, right-aligned. */
  readonly actions?: ReactNode;
}
