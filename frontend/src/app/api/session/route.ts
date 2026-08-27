import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { API_URL, CSRF_COOKIE, PROJECT_COOKIE, SESSION_COOKIE, csrfCookieOptions, getPublicApiUrl, newCsrfToken, readBackendError, serverAuthHeaders, sessionCookieOptions } from '@/lib/server-session';

export async function GET() {
  const cookieStore = await cookies();
  if (!cookieStore.get(SESSION_COOKIE)?.value) {
    return NextResponse.json({ detail: 'Not signed in.' }, { status: 401 });
  }
  const response = await fetch(`${API_URL}/auth/me`, {
    headers: await serverAuthHeaders(),
    cache: 'no-store',
  });
  if (!response.ok) return NextResponse.json(await readBackendError(response), { status: response.status });

  const body = await response.json();
  const current = cookieStore.get(PROJECT_COOKIE)?.value;
  const selectedProjectId = body.projects.some((project: { id: string }) => project.id === current)
    ? current
    : body.projects[0]?.id;
  const result = NextResponse.json({ ...body, selectedProjectId, apiBaseUrl: getPublicApiUrl() });
  if (selectedProjectId !== current && selectedProjectId) {
    result.cookies.set(PROJECT_COOKIE, selectedProjectId, sessionCookieOptions);
  }
  if (!cookieStore.get(CSRF_COOKIE)?.value) {
    result.cookies.set(CSRF_COOKIE, newCsrfToken(), csrfCookieOptions);
  }
  return result;
}
