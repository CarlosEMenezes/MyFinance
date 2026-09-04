import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { daysBetween, fromIso, today as todayFrom } from '../../lib/dates';
import type { ContributionFrequency } from '../../lib/goals';
import { fromMinorUnits, type Money } from '../../lib/money';
import type { Goal } from '../../types/api';
import { useDashboard } from '../dashboard/hooks';

import { fetchGoals } from './api';

export const goalsQueryKey = ['goals'] as const;

/** BR-11 measures a horizon in months, and the slider moves in months. */
const DAYS_PER_MONTH = 30.4;
const EARLIEST_MONTHS = 1;

export interface GoalsView {
  readonly goals: readonly Goal[];
  readonly selected: Goal | undefined;
  readonly select: (id: string) => void;
  /** What the what-if is currently proposing, not what is saved. */
  readonly months: number;
  readonly frequency: ContributionFrequency;
  readonly setMonths: (months: number) => void;
  readonly setFrequency: (frequency: ContributionFrequency) => void;
  /**
   * BR-11: planned in minus planned out. It comes from the dashboard because
   * it depends on the whole plan, not on any one goal.
   */
  readonly monthlySpare: Money;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

/** Whole months from today to the target, floored at one — BR-11's range. */
export function monthsUntil(targetDate: string, from = todayFrom()): number {
  const days = daysBetween(from, fromIso(targetDate));
  return Math.max(EARLIEST_MONTHS, Math.round(days / DAYS_PER_MONTH));
}

export function useGoals(): GoalsView {
  const { data, isPending, error } = useQuery({ queryKey: goalsQueryKey, queryFn: fetchGoals });
  const { dashboard } = useDashboard('MONTH');

  const goals = useMemo(() => [...(data ?? [])].sort((a, b) => a.rank - b.rank), [data]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [monthsOverride, setMonths] = useState<number | null>(null);
  const [frequency, setFrequency] = useState<ContributionFrequency>('MONTHLY');

  const selected = goals.find((goal) => goal.id === selectedId) ?? goals[0];

  return {
    goals,
    selected,
    select: (id) => {
      setSelectedId(id);
      // A new goal brings its own horizon; the previous slider position was
      // about a different target.
      setMonths(null);
    },
    months: monthsOverride ?? (selected === undefined ? 1 : monthsUntil(selected.targetDate)),
    frequency,
    setMonths,
    setFrequency,
    monthlySpare: fromMinorUnits(dashboard?.totals.netPlanned ?? 0),
    isLoading: isPending,
    error,
  };
}
