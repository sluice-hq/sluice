import { fetchApi } from './client';
import { Page, RunDetails } from './types';

export interface RunFilters {
  status?: string;
  pipeline?: string;
  from?: string;
  to?: string;
  decision?: 'ALLOW' | 'REVIEW' | 'BLOCK' | string;
}

export function getRuns(page = 0, size = 20, filters: RunFilters = {}): Promise<Page<RunDetails>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'createdAt,desc' });
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return fetchApi<Page<RunDetails>>(`/runs?${params.toString()}`);
}

export function getRun(id: string): Promise<RunDetails> {
  return fetchApi<RunDetails>(`/runs/${id}`);
}

export function startRun(pipeline: string, inputAssetId: string, idempotencyKey = crypto.randomUUID()): Promise<RunDetails> {
  return fetchApi<RunDetails>('/runs', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ pipeline, alias: 'stable', inputAssetId }),
  });
}
