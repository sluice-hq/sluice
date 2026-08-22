import { fetchApi } from './client';

export interface ProjectSummary { id: string; name: string; role: string }
export interface ApiKeySummary { id: string; name: string; createdAt: string; lastUsedAt?: string; revokedAt?: string }
export interface CreatedApiKey { key: ApiKeySummary; value: string }

export const listProjects = () => fetchApi<ProjectSummary[]>('/projects');
export const createProject = (name: string) => fetchApi<ProjectSummary>('/projects', {
  method: 'POST', body: JSON.stringify({ name }),
});
export const listApiKeys = (projectId: string) => fetchApi<ApiKeySummary[]>(`/projects/${projectId}/api-keys`);
export const createApiKey = (projectId: string, name: string) => fetchApi<CreatedApiKey>(`/projects/${projectId}/api-keys`, {
  method: 'POST', body: JSON.stringify({ name }),
});
export const revokeApiKey = (projectId: string, keyId: string) => fetchApi<void>(`/projects/${projectId}/api-keys/${keyId}`, {
  method: 'DELETE',
});
