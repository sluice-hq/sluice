import { fetchApi } from './client';
import { DashboardOverview } from './types';

export async function getDashboardOverview(): Promise<DashboardOverview> {
  return fetchApi<DashboardOverview>('/dashboard');
}
