import type { CalendarDate, DateFormat } from '../../lib/dates';
import type { ContributionFrequency } from '../../lib/goals';
import type { Currency, Money } from '../../lib/money';

export interface WhatIfPanelProps {
  readonly goalName: string;
  readonly targetAmount: Money;
  readonly savedAmount: Money;
  /** How far out the target is, 1-36 (BR-11). */
  readonly months: number;
  readonly frequency: ContributionFrequency;
  /**
   * Planned in minus planned out, from the server. The one figure here the
   * panel cannot derive, because it depends on the whole plan (BR-11).
   */
  readonly monthlySpare: Money;
  readonly today: CalendarDate;
  readonly onMonthsChange: (months: number) => void;
  readonly onFrequencyChange: (frequency: ContributionFrequency) => void;
  readonly onApply: () => void;
  readonly currency?: Currency;
  readonly dateFormat?: DateFormat;
}
