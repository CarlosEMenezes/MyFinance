import type { ReactNode } from 'react';

export interface CheckboxProps {
  readonly label: string;
  /** Explains the consequence of the choice, shown under the label. */
  readonly hint?: string;
  /** Trailing note, such as how many items a setting currently affects. */
  readonly meta?: ReactNode;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
  readonly className?: string | undefined;
}
