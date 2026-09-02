import type { CalendarDate, DateFormat } from '../../lib/dates';
import type { Currency, Money } from '../../lib/money';
import type { Frequency } from '../../lib/period';

export type EntryType = 'EXPENSE' | 'EARNING' | 'SAVING';

export interface CategoryOption {
  readonly id: string;
  readonly name: string;
  readonly type: EntryType;
}

export interface PaymentMethodOption {
  readonly id: string;
  readonly name: string;
  readonly kind: 'ACCOUNT' | 'DEBIT_CARD' | 'CREDIT_CARD';
  /** Credit cards only — what BR-4 needs to place the spend on a bill. */
  readonly closingDay?: number;
  readonly dueDay?: number;
}

/**
 * BR-8. Rates come from a live provider and a failed lookup **blocks the
 * save** rather than guessing, so "no rate" is a first-class state here rather
 * than a missing number.
 */
export type FxState =
  | {
      readonly status: 'READY';
      readonly rates: Readonly<Partial<Record<Currency, number>>>;
      /** Shown in the UI so the user knows how fresh the conversion is. */
      readonly fetchedAt: string;
    }
  | { readonly status: 'UNAVAILABLE' };

/** What the user committed. Amounts stay in the currency they were logged in. */
export interface LoggedEntry {
  readonly type: EntryType;
  readonly amount: Money;
  readonly currency: Currency;
  readonly categoryId: string;
  readonly paymentMethodId: string;
  readonly date: CalendarDate;
  readonly financing: {
    readonly instalmentCount: number;
    readonly instalmentAmount: Money;
    readonly frequency: Frequency;
  } | null;
}

export interface LogEntryFormProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onSubmit: (entry: LoggedEntry) => void;
  readonly categories: readonly CategoryOption[];
  readonly paymentMethods: readonly PaymentMethodOption[];
  readonly defaultCurrency: Currency;
  readonly fx: FxState;
  readonly today: CalendarDate;
  readonly dateFormat?: DateFormat;
}
