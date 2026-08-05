import { fetchApi } from './client';
import { Page, Job } from './types';

export async function getJobs(page = 0, size = 10, sort = 'createdAt,desc'): Promise<Page<Job>> {
  return fetchApi<Page<Job>>(`/jobs?page=${page}&size=${size}&sort=${sort}`);
}

export async function getJob(id: string): Promise<Job> {
  return fetchApi<Job>(`/jobs/${id}`);
}
