import { getJson, postJson } from '../../lib/http';
import type { Account, CreateAccountRequest, CreatePocketRequest } from '../../types/api';

export function fetchAccounts(): Promise<readonly Account[]> {
  return getJson<readonly Account[]>('/accounts');
}

export function createAccount(body: CreateAccountRequest): Promise<Account> {
  return postJson<Account>('/accounts', body);
}

/**
 * BR-13: a pocket is created against its parent, not beside it. The path says
 * so, which is why this cannot be called without an account to put it in.
 */
export function createPocket(accountId: string, body: CreatePocketRequest): Promise<Account> {
  return postJson<Account>(`/accounts/${accountId}/pockets`, body);
}
