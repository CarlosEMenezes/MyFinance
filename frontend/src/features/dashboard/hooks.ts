import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { Dashboard, PeriodKind, PlanRow } from '../../types/api';
import { updateCategoryPlan } from '../categories/api';

import { fetchDashboard } from './api';

export const dashboardQueryKey = (period: PeriodKind) => ['dashboard', period] as const;

/**
 * Spec §4: the whole overview is one call, computed server-side. Earnings,
 * Expenses and Overview all read from it rather than each assembling its own
 * figures, so the three can never disagree about what a period contains.
 */
export function useDashboard(period: PeriodKind) {
  const { data, isPending, error } = useQuery({
    queryKey: dashboardQueryKey(period),
    queryFn: () => fetchDashboard(period),
  });

  return { dashboard: data, isLoading: isPending, error };
}

/**
 * BR-14 inline plan editing from the Earnings and Expenses tables.
 *
 * The optimistic figure is the per-occurrence amount times how many times it
 * lands, which is BR-10 arithmetic on a value the user has not saved — the
 * case ADR-7 allows. The variance is not recomputed here: `VarianceText`
 * derives it from planned and real, so it follows on its own and BR-9 stays in
 * one place.
 */
export function useUpdatePlanAmount(period: PeriodKind) {
  const queryClient = useQueryClient();
  const key = dashboardQueryKey(period);

  const applyLocally = (
    dashboard: Dashboard,
    categoryId: string,
    perOccurrence: number,
  ): Dashboard => {
    const update = (rows: readonly PlanRow[]): PlanRow[] =>
      rows.map((row) =>
        row.categoryId === categoryId
          ? { ...row, perOccurrence, planned: perOccurrence * row.occurrencesInPeriod }
          : row,
      );

    return {
      ...dashboard,
      earnings: update(dashboard.earnings),
      expenses: update(dashboard.expenses),
    };
  };

  return useMutation({
    mutationFn: ({ categoryId, perOccurrence }: { categoryId: string; perOccurrence: number }) =>
      updateCategoryPlan(categoryId, { plannedAmount: perOccurrence }),

    onMutate: async ({ categoryId, perOccurrence }) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<Dashboard>(key);

      if (previous !== undefined) {
        queryClient.setQueryData<Dashboard>(key, applyLocally(previous, categoryId, perOccurrence));
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
