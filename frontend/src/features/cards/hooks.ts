import { useQuery } from '@tanstack/react-query';

import type { Card } from '../../types/api';

import { fetchCards } from './api';

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
