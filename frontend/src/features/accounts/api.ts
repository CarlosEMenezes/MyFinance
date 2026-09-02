import { getJson } from '../../lib/http';
import type { Account } from '../../types/api';

export function fetchAccounts(): Promise<readonly Account[]> {
  return getJson<readonly Account[]>('/accounts');
}
