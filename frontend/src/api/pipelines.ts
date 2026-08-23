import { fetchApi } from './client';
import { PublishedPipeline } from './types';

export type PipelineDefinition = Record<string, unknown> & {
  schemaVersion: string;
  slug: string;
  input: Record<string, unknown> & { kind: string; mimeTypes: string[] };
  steps: Array<Record<string, unknown> & { id: string; processor: string; version: string; config: Record<string, unknown> }>;
};

export interface ValidationError { path: string; code: string; message: string }
export interface ValidationReport { valid: boolean; errors: ValidationError[] }
export interface PipelineVersion {
  id: string; versionNumber: number; status: string; revision: number; definition: PipelineDefinition;
  validation: ValidationReport | null; createdAt: string; publishedAt: string | null;
}
export interface PipelineSummary {
  id: string; slug: string; name: string; description: string | null; status: string;
  draftVersion: number | null; draftRevision: number | null; stableVersion: number | null;
}
export interface PipelineDetail {
  pipeline: PipelineSummary; draft: PipelineVersion | null; aliases: Array<{ alias: string; versionNumber: number }>;
}

export const listPipelines = () => fetchApi<PipelineSummary[]>('/pipelines');
export const getPipeline = (slug: string) => fetchApi<PipelineDetail>(`/pipelines/${slug}`);
export const getPipelineHistory = (slug: string) => fetchApi<PipelineVersion[]>(`/pipelines/${slug}/versions`);
export const createPipeline = (name: string, description: string, definition: PipelineDefinition) =>
  fetchApi<PipelineDetail>('/pipelines', { method: 'POST', body: JSON.stringify({ name, description, definition }) });
export const saveDraft = (slug: string, revision: number, definition: PipelineDefinition) =>
  fetchApi<PipelineVersion>(`/pipelines/${slug}/draft`, { method: 'PUT', body: JSON.stringify({ revision, definition }) });
export const validatePipeline = (slug: string, definition: PipelineDefinition) =>
  fetchApi<ValidationReport>(`/pipelines/${slug}/validate`, { method: 'POST', body: JSON.stringify({ definition }) });
export const publishPipeline = (slug: string, revision: number) =>
  fetchApi<PipelineVersion>(`/pipelines/${slug}/publish`, { method: 'POST', body: JSON.stringify({ revision }) });

export async function getPublishedPipelines(): Promise<PublishedPipeline[]> {
  return fetchApi<PublishedPipeline[]>('/pipelines/published');
}
