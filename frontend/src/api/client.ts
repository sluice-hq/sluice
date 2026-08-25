import { csrfHeaders } from '@/lib/csrf';

export const BASE_URL = '/api/backend';

export async function fetchApi<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...csrfHeaders(options.method),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorData = await response.text();
    let message = errorData || 'The request failed';
    try {
      const problem = JSON.parse(errorData) as { detail?: string };
      message = problem.detail || message;
    } catch { /* Non-JSON error response. */ }
    throw new Error(message);
  }

  // Handle empty responses
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return {} as T;
  }

  return response.json();
}
