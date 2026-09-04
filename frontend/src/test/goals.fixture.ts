import type { Goal } from '../types/api';

/**
 * The prototype's three goals, against a "today" of 31-08-2026.
 *
 * Every derived field — gap, the contribution, progress and pace — is computed
 * server-side per ADR-7. The page renders them; only the what-if slider
 * recomputes, and only for a horizon the user has not saved.
 */
export const goals: readonly Goal[] = [
  {
    id: 'macbook',
    name: 'MacBook Air M4',
    targetAmount: 134900,
    targetDate: '2026-12-20',
    savedAmount: 41000,
    contributionFrequency: 'MONTHLY',
    pocketId: 'p-macbook',
    rank: 1,
    gap: 93900,
    contributionPerPeriod: 23475,
    monthlyRequirement: 23475,
    progressPercent: 30,
    pacePercent: 67,
    onPace: false,
  },
  {
    id: 'emergency',
    name: 'Emergency fund',
    targetAmount: 200000,
    targetDate: '2027-06-30',
    savedAmount: 64000,
    contributionFrequency: 'MONTHLY',
    pocketId: 'p-emergency',
    rank: 2,
    gap: 136000,
    contributionPerPeriod: 13600,
    monthlyRequirement: 13600,
    progressPercent: 32,
    pacePercent: 17,
    onPace: true,
  },
  {
    id: 'interrail',
    name: 'Interrail summer',
    targetAmount: 90000,
    targetDate: '2027-06-01',
    savedAmount: 8000,
    contributionFrequency: 'MONTHLY',
    pocketId: 'p-interrail',
    rank: 3,
    gap: 82000,
    contributionPerPeriod: 9111,
    monthlyRequirement: 9111,
    progressPercent: 9,
    pacePercent: 25,
    onPace: false,
  },
];
