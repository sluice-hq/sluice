import { fetchApi } from './client';
import { PublishedPipeline } from './types';

export async function getPublishedPipelines(): Promise<PublishedPipeline[]> {
  return fetchApi<PublishedPipeline[]>('/pipelines/published');
}
