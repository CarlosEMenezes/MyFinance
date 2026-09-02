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

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' },
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

export async function patchJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
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
