import { getJson } from '../../lib/http';
import type { Goal } from '../../types/api';

export function fetchGoals(): Promise<readonly Goal[]> {
  return getJson<readonly Goal[]>('/goals');
}
