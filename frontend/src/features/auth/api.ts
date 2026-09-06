import { getJson, postJson, postNothing } from '../../lib/http';
import type { User } from '../../types/api';

/**
 * ADR-11: none of these return a token, and none of them take one.
 *
 * The session travels as an HttpOnly cookie the browser attaches by itself, so
 * there is nothing here for this code to store, refresh or accidentally log.
 */

export interface RegisterBody {
  readonly email: string;
  readonly password: string;
  readonly name: string;
}

export interface LoginBody {
  readonly email: string;
  readonly password: string;
}

export function register(body: RegisterBody): Promise<User> {
  return postJson<User>('/auth/register', body);
}

export function login(body: LoginBody): Promise<User> {
  return postJson<User>('/auth/login', body);
}

export function logout(): Promise<void> {
  return postNothing('/auth/logout');
}

/** Who is signed in, if anyone. The 401 is the answer, not an error. */
export function fetchSignedInUser(): Promise<User> {
  return getJson<User>('/users/me');
}
