import { fetchApi } from './client';
import { Page, Asset, DownloadUrlResponse, UploadUrlRequest, UploadUrlResponse } from './types';

export interface AssetFilters {
  filename?: string;
  status?: string;
  mediaType?: string;
  from?: string;
  to?: string;
  externalSubjectId?: string;
  externalReference?: string;
}

function localDateBoundary(value: string, nextDay: boolean): string {
  const boundary = new Date(`${value}T00:00:00`);
  if (nextDay) boundary.setDate(boundary.getDate() + 1);
  return boundary.toISOString();
}

export async function getAssets(
  page = 0,
  size = 10,
  filters: AssetFilters = {},
  sort: string[] = ['createdAt,desc', 'id,desc'],
): Promise<Page<Asset>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  sort.forEach((value) => params.append('sort', value));
  if (filters.filename) params.set('filename', filters.filename);
  if (filters.status) params.set('status', filters.status);
  if (filters.mediaType) params.set('mediaType', filters.mediaType);
  if (filters.from) params.set('createdFrom', localDateBoundary(filters.from, false));
  if (filters.to) params.set('createdBefore', localDateBoundary(filters.to, true));
  if (filters.externalSubjectId) params.set('externalSubjectId', filters.externalSubjectId);
  if (filters.externalReference) params.set('externalReference', filters.externalReference);
  return fetchApi<Page<Asset>>(`/assets?${params.toString()}`);
}

export async function getAsset(id: string): Promise<Asset> {
  return fetchApi<Asset>(`/assets/${id}`);
}

export async function requestUploadUrl(request: UploadUrlRequest, idempotencyKey: string): Promise<UploadUrlResponse> {
  return fetchApi<UploadUrlResponse>('/uploads', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(request),
  });
}

export async function completeUpload(assetId: string, idempotencyKey = crypto.randomUUID()): Promise<unknown> {
  return fetchApi<unknown>(`/uploads/${assetId}/complete`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export async function getDownloadUrl(assetId: string): Promise<DownloadUrlResponse> {
  return fetchApi<DownloadUrlResponse>(`/assets/${assetId}/download`);
}
