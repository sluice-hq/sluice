import { fetchApi } from './client';
import { Page, Asset, UploadUrlRequest, UploadUrlResponse } from './types';

export async function getAssets(page = 0, size = 10, sort = 'createdAt,desc'): Promise<Page<Asset>> {
  return fetchApi<Page<Asset>>(`/assets?page=${page}&size=${size}&sort=${sort}`);
}

export async function getAsset(id: string): Promise<Asset> {
  return fetchApi<Asset>(`/assets/${id}`);
}

export async function requestUploadUrl(request: UploadUrlRequest): Promise<UploadUrlResponse> {
  return fetchApi<UploadUrlResponse>('/assets/upload-url', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function completeUpload(assetId: string): Promise<any> {
  return fetchApi<any>(`/assets/${assetId}/complete`, {
    method: 'POST',
  });
}
