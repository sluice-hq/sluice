import { fetchApi } from './client';
import { Page, Asset, DownloadUrlResponse, UploadUrlRequest, UploadUrlResponse } from './types';

export async function getAssets(page = 0, size = 10, sort = 'createdAt,desc'): Promise<Page<Asset>> {
  return fetchApi<Page<Asset>>(`/assets?page=${page}&size=${size}&sort=${sort}`);
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
