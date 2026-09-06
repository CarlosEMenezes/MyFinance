import { getJson, postJson } from '../../lib/http';
import type { Card, CreateCardRequest } from '../../types/api';

export function fetchCards(): Promise<readonly Card[]> {
  return getJson<readonly Card[]>('/cards');
}

export function createCard(body: CreateCardRequest): Promise<Card> {
  return postJson<Card>('/cards', body);
}
