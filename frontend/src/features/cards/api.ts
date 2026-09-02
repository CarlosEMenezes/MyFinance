import { getJson } from '../../lib/http';
import type { Card } from '../../types/api';

export function fetchCards(): Promise<readonly Card[]> {
  return getJson<readonly Card[]>('/cards');
}
