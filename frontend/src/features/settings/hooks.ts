import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { CurrencyCode, FxRates, UpdateUserRequest, User } from '../../types/api';

import { fetchFxRates, fetchUser, updateUser } from './api';

export const userQueryKey = ['users', 'me'] as const;
export const fxRatesQueryKey = ['fx', 'rates'] as const;

export interface FxRateView {
  readonly currency: CurrencyCode;
  /** Units of `currency` per one unit of the default currency. */
  readonly rate: number;
}

export interface SettingsView {
  readonly user: User | undefined;
  readonly save: (changes: UpdateUserRequest) => void;
  /** BR-8: every rate as the provider sends it, plus when it was pulled. */
  readonly fxRates: readonly FxRateView[];
  readonly fxFetchedAt: string | undefined;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

/**
 * The rates worth showing: everything except the base, which is always 1 and
 * says nothing. Left in the provider's direction — inverting a rate to phrase
 * it the other way round would be the frontend computing a figure BR-8 says
 * comes from the provider.
 */
function ratesToShow(rates: FxRates | undefined): readonly FxRateView[] {
  if (rates === undefined) {
    return [];
  }
  return Object.entries(rates.rates)
    .filter(([currency]) => currency !== rates.base)
    .map(([currency, rate]) => ({ currency: currency as CurrencyCode, rate }));
}

export function useSettings(): SettingsView {
  const queryClient = useQueryClient();

  const profile = useQuery({ queryKey: userQueryKey, queryFn: fetchUser });
  const rates = useQuery({ queryKey: fxRatesQueryKey, queryFn: fetchFxRates });

  const mutation = useMutation({
    mutationFn: updateUser,

    onMutate: async (changes) => {
      await queryClient.cancelQueries({ queryKey: userQueryKey });
      const previous = queryClient.getQueryData<User>(userQueryKey);

      if (previous !== undefined) {
        queryClient.setQueryData<User>(userQueryKey, { ...previous, ...changes });
      }

      return { previous };
    },

    onError: (_error, _variables, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(userQueryKey, context.previous);
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: userQueryKey });
    },
  });

  return {
    user: profile.data,
    save: (changes) => {
      mutation.mutate(changes);
    },
    fxRates: ratesToShow(rates.data),
    fxFetchedAt: rates.data?.fetchedAt,
    isLoading: profile.isPending,
    error: profile.error,
  };
}
