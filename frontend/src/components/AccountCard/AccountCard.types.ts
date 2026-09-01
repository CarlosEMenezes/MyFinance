import type { Currency, Money } from '../../lib/money';

/** Spec §2. Where the money sits. */
export type AccountKind = 'CASH' | 'BANK' | 'SAVINGS';

export interface AccountPocket {
  readonly id: string;
  readonly name: string;
  readonly balance: Money;
}

export interface AccountCardProps {
  readonly name: string;
  readonly kind: AccountKind;
  readonly balance: Money;
  readonly currency?: Currency;
  readonly note?: string;
  /** BR-13. Defaults to true; false is labelled everywhere the account appears. */
  readonly includedInTotals?: boolean;
  /**
   * BR-13. A pocket is a named sub-balance already inside `balance`. Listing
   * them must never read as extra money.
   */
  readonly pockets?: readonly AccountPocket[];
  /** Names of the cards that settle from this account. */
  readonly cards?: readonly string[];
}
