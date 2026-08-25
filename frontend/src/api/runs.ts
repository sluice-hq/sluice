import { fetchApi } from './client';
import { Page, RunDetails } from './types';

export function getRuns(page = 0, size = 20): Promise<Page<RunDetails>> {
  return fetchApi<Page<RunDetails>>(`/runs?page=${page}&size=${size}&sort=createdAt,desc`);
}

export function getRun(id: string): Promise<RunDetails> {
  return fetchApi<RunDetails>(`/runs/${id}`);
}

export function startRun(pipeline: string, inputAssetId: string): Promise<RunDetails> {
  return fetchApi<RunDetails>('/runs', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ pipeline, alias: 'stable', inputAssetId }),
  });
}
