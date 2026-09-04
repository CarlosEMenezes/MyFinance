/**
 * The one place a response crosses from JSON into the app.
 *
 * A non-2xx is thrown rather than returned, so a caller cannot accidentally
 * render an error body as data — spec §4 has the backend answer failures with
 * RFC 7807 problem documents, and those have a `title` worth showing.
 */

import type { ProblemDetail } from '../types/api';

export const API_BASE = '/api/v1';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, message: string, problem: ProblemDetail | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

interface RequestOptions {
  readonly method?: 'POST' | 'PATCH';
  /** A plain record, not `HeadersInit`: the union admits arrays, which spread into indices. */
  readonly headers?: Readonly<Record<string, string>>;
  readonly body?: string;
}

/**
 * Every verb fails the same way, so the failure path is written once. Three
 * copies of it was three places for the error handling to drift apart.
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  });

  if (!response.ok) {
    const problem = await readProblem(response);
    throw new ApiError(
      response.status,
      problem?.title ?? `Request to ${path} failed with ${String(response.status)}`,
      problem,
    );
  }

  return (await response.json()) as T;
}

const withBody = (method: 'POST' | 'PATCH', body: unknown): RequestOptions => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export function getJson<T>(path: string): Promise<T> {
  return request<T>(path);
}

export function patchJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, withBody('PATCH', body));
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, withBody('POST', body));
}
