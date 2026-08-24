import { fetchApi } from './client';
import { RunDetails } from './types';

export function getRun(id: string): Promise<RunDetails> {
  return fetchApi<RunDetails>(`/runs/${id}`);
}
