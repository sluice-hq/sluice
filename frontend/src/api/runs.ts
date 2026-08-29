import { fetchApi } from './client';
import { Page, RunDetails } from './types';

export interface RunFilters {
  status?: string;
  pipeline?: string;
  from?: string;
  to?: string;
  decision?: 'ALLOW' | 'REVIEW' | 'BLOCK' | string;
  governanceOnly?: boolean;
}

export type GovernanceRunFilters = Pick<RunFilters, 'decision' | 'pipeline' | 'from' | 'to'>;

function localDateBoundary(value: string, nextDay: boolean): string {
  const boundary = new Date(`${value}T00:00:00`);
  if (nextDay) boundary.setDate(boundary.getDate() + 1);
  return boundary.toISOString();
}

export function getRuns(page = 0, size = 20, filters: RunFilters = {}): Promise<Page<RunDetails>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  params.append('sort', 'createdAt,desc');
  params.append('sort', 'id,desc');
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, String(value)); });
  return fetchApi<Page<RunDetails>>(`/runs?${params.toString()}`);
}

export function getGovernanceRuns(page = 0, size = 20, filters: GovernanceRunFilters = {}): Promise<Page<RunDetails>> {
  return getRuns(page, size, {
    ...filters,
    from: filters.from ? localDateBoundary(filters.from, false) : undefined,
    to: filters.to ? localDateBoundary(filters.to, true) : undefined,
    governanceOnly: true,
  });
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
