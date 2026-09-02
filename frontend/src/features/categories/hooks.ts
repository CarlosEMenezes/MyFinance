import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { fromIso } from '../../lib/dates';
import { fromMinorUnits, type Money } from '../../lib/money';
import { occurrencesIn, plannedAmountIn, type Frequency } from '../../lib/period';
import type {
  Category,
  CategoryList,
  PeriodKind,
  UpdateCategoryPlanRequest,
} from '../../types/api';

import { fetchCategories, updateCategoryPlan } from './api';

export const categoriesQueryKey = (period: PeriodKind) => ['categories', period] as const;

export interface CategoryRowView {
  readonly id: string;
  readonly name: string;
  readonly group: string;
  readonly type: Category['type'];
  readonly perOccurrence: Money;
  readonly frequency: Frequency;
  readonly anchorDate: string;
  /**
   * BR-10, counted against the window the server sent. Recomputed locally as
   * the user edits a frequency or an anchor, which is the optimistic case
   * ADR-7 allows — the saved figure still comes from the server.
   */
  readonly occurrencesInPeriod: number;
  readonly plannedInPeriod: Money;
}

export interface CategoriesView {
  readonly expenses: readonly CategoryRowView[];
  readonly earnings: readonly CategoryRowView[];
  readonly periodLabel: string;
  readonly periodFrom: string;
  readonly periodTo: string;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

function toRow(category: Category, list: CategoryList): CategoryRowView {
  const range = { start: fromIso(list.period.from), end: fromIso(list.period.to) };
  const frequency = category.plannedFrequency;
  const anchor = fromIso(category.anchorDate);
  const perOccurrence = fromMinorUnits(category.plannedAmount);

  return {
    id: category.id,
    name: category.name,
    group: category.group,
    type: category.type,
    perOccurrence,
    frequency,
    anchorDate: category.anchorDate,
    occurrencesInPeriod: occurrencesIn(frequency, anchor, range),
    plannedInPeriod: plannedAmountIn(perOccurrence, frequency, anchor, range),
  };
}

export function useCategories(period: PeriodKind = 'MONTH'): CategoriesView {
  const { data, isPending, error } = useQuery({
    queryKey: categoriesQueryKey(period),
    queryFn: () => fetchCategories(period),
  });

  const rows =
    data === undefined ? [] : data.categories.filter((c) => !c.archived).map((c) => toRow(c, data));

  return {
    expenses: rows.filter((row) => row.type === 'EXPENSE'),
    earnings: rows.filter((row) => row.type === 'EARNING'),
    periodLabel: data?.period.label ?? '',
    periodFrom: data?.period.from ?? '',
    periodTo: data?.period.to ?? '',
    isLoading: isPending,
    error,
  };
}

export function useUpdateCategoryPlan(period: PeriodKind = 'MONTH') {
  const queryClient = useQueryClient();
  const key = categoriesQueryKey(period);

  return useMutation({
    mutationFn: ({ id, changes }: { id: string; changes: UpdateCategoryPlanRequest }) =>
      updateCategoryPlan(id, changes),

    // The plan is edited inline, so the row has to answer immediately. The
    // previous list is kept so a failed save puts the old figure back rather
    // than leaving a number on screen that was never stored.
    onMutate: async ({ id, changes }) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<CategoryList>(key);

      if (previous !== undefined) {
        queryClient.setQueryData<CategoryList>(key, {
          ...previous,
          categories: previous.categories.map((category) =>
            category.id === id ? { ...category, ...changes } : category,
          ),
        });
      }

      return { previous };
    },

    onError: (_error, _variables, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(key, context.previous);
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: key });
    },
  });
}
