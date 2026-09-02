import { useQuery } from '@tanstack/react-query';

import { fromMinorUnits, sum, type Money } from '../../lib/money';
import type { Account } from '../../types/api';

import { fetchAccounts } from './api';

export const accountsQueryKey = ['accounts'] as const;

export interface AccountsView {
  readonly accounts: readonly Account[];
  /**
   * BR-13. Only accounts marked `includeInTotals` are counted, and pocket
   * balances are never added because they are already inside their parent.
   */
  readonly countedTotal: Money;
  readonly hasExcludedAccounts: boolean;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

export function useAccounts(): AccountsView {
  const { data, isPending, error } = useQuery({
    queryKey: accountsQueryKey,
    queryFn: fetchAccounts,
  });

  const accounts = data ?? [];
  const counted = accounts.filter((account) => account.includeInTotals);

  return {
    accounts,
    countedTotal: sum(counted.map((account) => fromMinorUnits(account.balance))),
    hasExcludedAccounts: counted.length !== accounts.length,
    isLoading: isPending,
    error,
  };
}
