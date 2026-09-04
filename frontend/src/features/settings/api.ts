import { getJson, patchJson } from '../../lib/http';
import type { FxRates, UpdateUserRequest, User } from '../../types/api';

export function fetchUser(): Promise<User> {
  return getJson<User>('/users/me');
}

export function updateUser(body: UpdateUserRequest): Promise<User> {
  return patchJson<User>('/users/me', body);
}

export function fetchFxRates(): Promise<FxRates> {
  return getJson<FxRates>('/fx/rates');
}
