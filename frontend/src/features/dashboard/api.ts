import { getJson } from '../../lib/http';
import type { Dashboard, PeriodKind } from '../../types/api';

export function fetchDashboard(period: PeriodKind): Promise<Dashboard> {
  return getJson<Dashboard>(`/dashboard?period=${period}`);
}
