import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { Card, CreateCardRequest } from '../../types/api';

import { createCard, fetchCards } from './api';

export const cardsQueryKey = ['cards'] as const;

export interface CardsView {
  /** Credit first: they are the ones with a bill to plan around (BR-4). */
  readonly cards: readonly Card[];
  readonly isLoading: boolean;
  readonly error: Error | null;
}

export function useCards(): CardsView {
  const { data, isPending, error } = useQuery({ queryKey: cardsQueryKey, queryFn: fetchCards });
  const cards = data ?? [];

  return {
    cards: [
      ...cards.filter((card) => card.kind === 'CREDIT'),
      ...cards.filter((card) => card.kind === 'DEBIT'),
    ],
    isLoading: isPending,
    error,
  };
}

/**
 * Creating a card.
 *
 * Not optimistic: BR-4's cycle dates come back computed by the server, and a
 * card drawn locally would have to invent them or show a card with none — the
 * exact degradation the Cards page exists to avoid.
 */
export function useCreateCard() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateCardRequest) => createCard(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cardsQueryKey });
      // A card is a payment method, and a credit card adds a bill to the queue.
      void queryClient.invalidateQueries({ queryKey: ['accounts'] });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
