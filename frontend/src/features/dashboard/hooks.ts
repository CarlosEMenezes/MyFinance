import { useQuery } from '@tanstack/react-query';

import type { PeriodKind } from '../../types/api';

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
