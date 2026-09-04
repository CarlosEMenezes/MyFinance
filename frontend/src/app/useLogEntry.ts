import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import type {
  CategoryOption,
  FxState,
  LoggedEntry,
  PaymentMethodOption,
} from '../components/LogEntryForm/LogEntryForm.types';
import { useAccounts } from '../features/accounts/hooks';
import { useCards } from '../features/cards/hooks';
import { useCategories } from '../features/categories/hooks';
import { dashboardQueryKey } from '../features/dashboard/hooks';
import { useSettings } from '../features/settings/hooks';
import { postJson } from '../lib/http';
import type { Currency } from '../lib/money';
import type { CreateTransactionRequest, CurrencyCode, Transaction } from '../types/api';

/**
 * Everything the log dialog needs, gathered in one place.
 *
 * The dialog is the shell's, not any page's: it is reachable from every screen
 * and the entry it writes changes what several of them show. Assembling its
 * options here means the shell holds one set of queries rather than each page
 * holding its own copy.
 */

export interface LogEntryState {
  readonly open: boolean;
  readonly openDialog: () => void;
  readonly closeDialog: () => void;
  readonly submit: (entry: LoggedEntry) => void;
  readonly categories: readonly CategoryOption[];
  readonly paymentMethods: readonly PaymentMethodOption[];
  readonly defaultCurrency: Currency;
  readonly fx: FxState;
  /** For the sidebar footer: when the rates behind conversions were pulled. */
  readonly fxUpdatedAt: string;
}

/**
 * BR-8: with no rates, `UNAVAILABLE` is the truthful state and the form blocks
 * a foreign-currency save on it. Reporting stale or absent rates as READY
 * would let the form invent a conversion.
 */
function toFxState(
  rates: readonly { readonly currency: CurrencyCode; readonly rate: number }[],
  fetchedAt: string | undefined,
): FxState {
  if (fetchedAt === undefined) {
    return { status: 'UNAVAILABLE' };
  }
  return {
    status: 'READY',
    rates: Object.fromEntries(rates.map((rate) => [rate.currency, rate.rate])),
    fetchedAt,
  };
}

export function useLogEntry(): LogEntryState {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const { expenses, earnings } = useCategories();
  const { accounts } = useAccounts();
  const { cards } = useCards();
  const { user, fxRates, fxFetchedAt } = useSettings();

  const mutation = useMutation({
    mutationFn: (body: CreateTransactionRequest) => postJson<Transaction>('/transactions', body),
    onSuccess: () => {
      // A logged entry changes the real column, the position and the derived
      // rows. Rather than guess which, refetch what the server computes.
      void queryClient.invalidateQueries({ queryKey: dashboardQueryKey('MONTH') });
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      void queryClient.invalidateQueries({ queryKey: ['accounts'] });
    },
  });

  const categories: readonly CategoryOption[] = [...earnings, ...expenses].map((category) => ({
    id: category.id,
    name: category.name,
    type: category.type,
  }));

  const paymentMethods: readonly PaymentMethodOption[] = [
    ...accounts.map((account) => ({
      id: account.id,
      name: account.name,
      kind: 'ACCOUNT' as const,
    })),
    ...cards.map((card) =>
      card.kind === 'CREDIT' && card.closingDay !== null && card.dueDay !== null
        ? {
            id: card.id,
            name: card.name,
            kind: 'CREDIT_CARD' as const,
            closingDay: card.closingDay,
            dueDay: card.dueDay,
          }
        : // BR-5, and the degradation the Cards page already makes: a credit
          // card missing its cycle is presented as one that settles at once,
          // never as one with an invented bill date.
          { id: card.id, name: card.name, kind: 'DEBIT_CARD' as const },
    ),
  ];

  return {
    open,
    openDialog: () => {
      setOpen(true);
    },
    closeDialog: () => {
      setOpen(false);
    },
    submit: (entry) => {
      mutation.mutate({
        type: entry.type,
        categoryId: entry.categoryId,
        amount: entry.amount,
        currency: entry.currency,
        date: entry.date,
        paymentMethodId: entry.paymentMethodId,
        financing:
          entry.financing === null
            ? null
            : {
                instalmentCount: entry.financing.instalmentCount,
                instalmentAmount: entry.financing.instalmentAmount,
                frequency: entry.financing.frequency,
              },
      });
      setOpen(false);
    },
    categories,
    paymentMethods,
    defaultCurrency: user?.defaultCurrency ?? 'EUR',
    fx: toFxState(fxRates, fxFetchedAt),
    fxUpdatedAt: fxFetchedAt ?? 'never',
  };
}
