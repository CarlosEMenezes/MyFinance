import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { fromMinorUnits, sum, type Money } from '../../lib/money';
import type { Account } from '../../types/api';

import type { NewAccountSubmission } from './NewAccountDialog';
import { createAccount, createPocket, fetchAccounts } from './api';

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

/**
 * Creating an account, or a pocket inside one (BR-13).
 *
 * One hook for both because the dialog offers them as one choice, but they are
 * two writes: a pocket goes to its parent's own endpoint, and the parent's
 * balance does not move — the pocket is already inside it.
 *
 * Not optimistic. The server assigns the id and, for a pocket, returns the
 * parent with the pocket in it; inventing either locally would put a row on
 * screen that does not exist yet, which for a balance is worse than waiting.
 */
export function useCreateAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (submission: NewAccountSubmission) => {
      if (submission.kind === 'POCKET' && submission.parentAccountId !== null) {
        return createPocket(submission.parentAccountId, {
          name: submission.name,
          balance: submission.balance,
        });
      }
      return createAccount({
        name: submission.name,
        // Narrowed by the branch above: POCKET never reaches here.
        kind: submission.kind === 'POCKET' ? 'SAVINGS' : submission.kind,
        balance: submission.balance,
        currency: submission.currency,
        includeInTotals: submission.includeInTotals,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: accountsQueryKey });
      // A new account is a payment method and a place a card can settle from.
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
