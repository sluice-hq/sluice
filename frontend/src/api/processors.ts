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
