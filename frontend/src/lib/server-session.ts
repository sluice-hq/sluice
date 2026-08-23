import { cookies } from 'next/headers';

export const SESSION_COOKIE = 'sluice_session';
export const PROJECT_COOKIE = 'sluice_project';
export const API_URL = process.env.API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';

export const sessionCookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === 'production',
  sameSite: 'lax' as const,
  path: '/',
  maxAge: 24 * 60 * 60,
};

export async function serverAuthHeaders(): Promise<Record<string, string>> {
  const cookieStore = await cookies();
  const token = cookieStore.get(SESSION_COOKIE)?.value;
  const projectId = cookieStore.get(PROJECT_COOKIE)?.value;
  if (!token) return {};
  return {
    Authorization: `Bearer ${token}`,
    ...(projectId ? { 'X-Project-ID': projectId } : {}),
  };
}

export async function readBackendError(response: Response): Promise<unknown> {
  const body = await response.text();
  if (!body) return { detail: 'The backend request failed.' };
  try {
    return JSON.parse(body);
  } catch {
    return { detail: body };
  }
}
