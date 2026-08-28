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
  const response = await fetchApi<unknown>('/pipelines/published');
  if (!Array.isArray(response)) return [];
  return response.flatMap((value) => normalizePublishedPipeline(value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringArray(value: unknown): string[] | null {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string' && item.trim())
    ? value.map((item) => item.toLowerCase())
    : null;
}

function normalizePublishedPipeline(value: unknown): PublishedPipeline[] {
  if (!isRecord(value) || typeof value.id !== 'string' || typeof value.slug !== 'string'
    || typeof value.name !== 'string' || typeof value.versionId !== 'string'
    || typeof value.versionNumber !== 'number') return [];

  const contract = isRecord(value.inputContract) ? value.inputContract : {};
  const constraints = isRecord(value.uploadConstraints) ? value.uploadConstraints : {};
  const mimeTypes = stringArray(contract.mimeTypes);
  const allowedContentTypes = stringArray(constraints.allowedContentTypes);
  const contractMaxBytes = typeof contract.maxBytes === 'number' && Number.isFinite(contract.maxBytes)
    && contract.maxBytes > 0 ? contract.maxBytes : 0;
  const globalMaxBytes = typeof constraints.maxBytes === 'number' && Number.isFinite(constraints.maxBytes)
    && constraints.maxBytes > 0 ? constraints.maxBytes : 0;
  const contractUsable = Boolean(mimeTypes && allowedContentTypes && contractMaxBytes && globalMaxBytes);

  return [{
    id: value.id,
    slug: value.slug,
    name: value.name,
    description: typeof value.description === 'string' ? value.description : null,
    versionId: value.versionId,
    versionNumber: value.versionNumber,
    expectedInputMimeType: typeof value.expectedInputMimeType === 'string' ? value.expectedInputMimeType : '*/*',
    inputContract: {
      kind: typeof contract.kind === 'string' ? contract.kind : 'unknown',
      mimeTypes: mimeTypes ?? [],
      maxBytes: contractMaxBytes,
      maxPixels: typeof contract.maxPixels === 'number' && Number.isFinite(contract.maxPixels) && contract.maxPixels > 0 ? contract.maxPixels : 0,
      alphaSupported: contract.alphaSupported === true,
      animationSupported: contract.animationSupported === true,
    },
    uploadConstraints: { maxBytes: globalMaxBytes, allowedContentTypes: allowedContentTypes ?? [] },
    contractUsable,
    contractIssue: contractUsable ? null : 'This published version has no usable resolved input contract. Republish it before testing files.',
  }];
}
