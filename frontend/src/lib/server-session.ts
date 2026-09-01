import { cookies } from 'next/headers';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import type { NextRequest } from 'next/server';

export const SESSION_COOKIE = 'sluice_session';
export const PROJECT_COOKIE = 'sluice_project';
export const CSRF_COOKIE = 'sluice_csrf';
export const CSRF_HEADER = 'X-Sluice-CSRF';
const DEFAULT_API_URL = 'http://localhost:8080/api/v1';
const secureCookieSetting = process.env.SLUICE_SECURE_COOKIES;
const secureCookies = secureCookieSetting === 'false'
  ? false
  : secureCookieSetting === 'true' || process.env.NODE_ENV === 'production';
export const API_URL = process.env.API_BASE_URL
  || process.env.SLUICE_PUBLIC_API_BASE_URL
  || process.env.NEXT_PUBLIC_API_BASE_URL
  || DEFAULT_API_URL;

export function getPublicApiUrl(): string {
  return (process.env.SLUICE_PUBLIC_API_BASE_URL
    || process.env.NEXT_PUBLIC_API_BASE_URL
    || DEFAULT_API_URL).replace(/\/+$/, '');
}

export function getInternalStorageUrl(publicUrl: string): string {
  const publicBase = process.env.SLUICE_STORAGE_PUBLIC_BASE_URL?.replace(/\/+$/, '');
  const internalBase = process.env.SLUICE_STORAGE_INTERNAL_BASE_URL?.replace(/\/+$/, '');
  if (!publicBase && !internalBase) return publicUrl;
  if (!publicBase || !internalBase || !publicUrl.startsWith(`${publicBase}/`)) {
    throw new Error('The storage download URL does not match the configured public endpoint.');
  }
  return internalBase + publicUrl.slice(publicBase.length);
}

export const sessionCookieOptions = {
  httpOnly: true,
  secure: secureCookies,
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

export function hasTrustedBrowserOrigin(request: NextRequest): boolean {
  const fetchSite = request.headers.get('sec-fetch-site')?.toLowerCase();
  if (fetchSite && fetchSite !== 'same-origin' && fetchSite !== 'none') return false;

  const origin = request.headers.get('origin');
  if (!origin) return true;
  try {
    const trustedOrigins = new Set([request.nextUrl.origin]);
    if (process.env.SLUICE_DASHBOARD_URL) {
      trustedOrigins.add(new URL(process.env.SLUICE_DASHBOARD_URL).origin);
    }
    return trustedOrigins.has(new URL(origin).origin);
  } catch {
    return false;
  }
}

export function backendResponseHeaders(response: Response): Headers {
  const headers = new Headers();
  const retryAfter = response.headers.get('Retry-After');
  if (retryAfter) headers.set('Retry-After', retryAfter);
  return headers;
}

export function forwardedClientHeaders(request: NextRequest): Record<string, string> {
  const forwarded = request.headers.get('x-forwarded-for')?.split(',').pop()?.trim()
    || request.headers.get('x-real-ip')?.trim();
  return forwarded ? { 'X-Forwarded-For': forwarded } : {};
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
