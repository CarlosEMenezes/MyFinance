import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { ApiError, UNAUTHORISED } from '../../lib/http';
import type { User } from '../../types/api';

import { fetchSignedInUser, login, logout, register } from './api';
import type { LoginBody, RegisterBody } from './api';

export const signedInUserQueryKey = ['auth', 'me'] as const;

export interface AuthState {
  readonly user: User | undefined;
  readonly isSignedIn: boolean;
  /** True only until the first answer; a page must not flash before it. */
  readonly isChecking: boolean;
}

/**
 * Whether anyone is signed in.
 *
 * A 401 is the expected answer for a signed-out visitor, not a failure to
 * report: it resolves to "nobody" rather than throwing, so the shell can send
 * them to the login page instead of showing an error about it.
 */
export function useAuth(): AuthState {
  const { data, isPending } = useQuery({
    queryKey: signedInUserQueryKey,
    queryFn: async () => {
      try {
        return await fetchSignedInUser();
      } catch (error) {
        if (error instanceof ApiError && error.status === UNAUTHORISED) {
          return null;
        }
        throw error;
      }
    },
    // Signing in and out invalidates this explicitly, and nothing else changes
    // who you are, so there is no reason to re-ask on every window focus.
    staleTime: Infinity,
    retry: false,
  });

  return {
    user: data ?? undefined,
    isSignedIn: data != null,
    isChecking: isPending,
  };
}

/** Registering and signing in differ only in which endpoint they call. */
function useSignIn<TBody>(action: (body: TBody) => Promise<User>) {
  const queryClient = useQueryClient();

  return useMutation<User, Error, TBody>({
    mutationFn: action,
    onSuccess: (user) => {
      // Seed rather than invalidate: the response already is the profile, and
      // a refetch would put a loading state between signing in and arriving.
      queryClient.setQueryData(signedInUserQueryKey, user);
    },
  });
}

export function useRegister() {
  return useSignIn<RegisterBody>(register);
}

export function useLogin() {
  return useSignIn<LoginBody>(login);
}

/**
 * Signs out and forgets everything, in that order.
 *
 * The order is the whole of it. `queryClient.clear()` empties the cache but
 * does NOT notify observers that are already mounted — they stay bound to the
 * query object it removed — so clearing first left the shell rendering the
 * previous session's figures until something else happened to trigger a
 * refetch, which on a signed-out app is nothing at all.
 *
 * So: write "nobody" to the auth query first. The guard reacts, the signed-in
 * tree unmounts, and every query inside it stops being observed. Only then
 * drop what belonged to that session — everything except the answer just
 * written, which is why this is `removeQueries` with a predicate rather than
 * `clear()`.
 *
 * Found by a test. By hand it would have looked like a slow sign-out.
 */
export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(signedInUserQueryKey, null);
      queryClient.removeQueries({
        predicate: (query) => query.queryKey[0] !== signedInUserQueryKey[0],
      });
    },
  });
}
