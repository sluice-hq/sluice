import { fetchApi } from './client';

export interface ProcessorContract {
  slug: string;
  version: string;
  displayName: string;
  description: string;
  category: string;
  input: { kind: string; mimeTypes: string[]; maxBytes: number; maxPixels: number };
  output: { kind: string; mimeTypes: string[]; maxBytes: number; maxPixels: number };
  limits: { timeoutSeconds: number; memoryMb: number; maxOutputBytes: number };
  configSchema: Record<string, unknown>;
  permissions: string[];
  status: string;
  releaseNotes: string;
  publisher: string;
  visibility: string;
  publishedAt: string;
}

export function listProcessors(): Promise<ProcessorContract[]> {
  return fetchApi<ProcessorContract[]>('/processors');
}

export interface ProjectProcessorRelease {
  processor: ProcessorContract;
  enabled: boolean;
  enabledAt: string | null;
  updatedAt: string | null;
}

export interface ProcessorReleaseState {
  slug: string;
  version: string;
  enabled: boolean;
  enabledAt: string;
  updatedAt: string;
}

export function listProjectProcessors(projectId: string): Promise<ProjectProcessorRelease[]> {
  return fetchApi<ProjectProcessorRelease[]>(`/projects/${projectId}/processor-releases`);
}

export function enableProcessorRelease(projectId: string, slug: string, version: string): Promise<ProcessorReleaseState> {
  return fetchApi<ProcessorReleaseState>(
    `/projects/${projectId}/processor-releases/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}`,
    { method: 'PUT' },
  );
}

export function disableProcessorRelease(projectId: string, slug: string, version: string): Promise<void> {
  return fetchApi<void>(
    `/projects/${projectId}/processor-releases/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}`,
    { method: 'DELETE' },
  );
}
