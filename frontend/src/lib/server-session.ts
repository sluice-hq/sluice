import { cookies } from 'next/headers';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import type { NextRequest } from 'next/server';

export const SESSION_COOKIE = 'sluice_session';
export const PROJECT_COOKIE = 'sluice_project';
export const CSRF_COOKIE = 'sluice_csrf';
export const CSRF_HEADER = 'X-Sluice-CSRF';
const DEFAULT_API_URL = 'http://localhost:8080/api/v1';
export const API_URL = process.env.API_BASE_URL
  || process.env.SLUICE_PUBLIC_API_BASE_URL
  || process.env.NEXT_PUBLIC_API_BASE_URL
  || DEFAULT_API_URL;

export function getPublicApiUrl(): string {
  return (process.env.SLUICE_PUBLIC_API_BASE_URL
    || process.env.NEXT_PUBLIC_API_BASE_URL
    || DEFAULT_API_URL).replace(/\/+$/, '');
}

export const sessionCookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === 'production',
  sameSite: 'lax' as const,
  path: '/',
  maxAge: 24 * 60 * 60,
};

export const csrfCookieOptions = {
  ...sessionCookieOptions,
  httpOnly: false,
  sameSite: 'strict' as const,
};

export function newCsrfToken(): string {
  return randomBytes(32).toString('base64url');
}

export function hasValidCsrfToken(request: NextRequest): boolean {
  const cookieToken = request.cookies.get(CSRF_COOKIE)?.value;
  const headerToken = request.headers.get(CSRF_HEADER);
  if (!cookieToken || !headerToken) return false;

  const cookieBytes = Buffer.from(cookieToken);
  const headerBytes = Buffer.from(headerToken);
  return cookieBytes.length === headerBytes.length && timingSafeEqual(cookieBytes, headerBytes);
}

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
