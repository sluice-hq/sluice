import { NextRequest, NextResponse } from 'next/server';
import { API_URL, PROJECT_COOKIE, SESSION_COOKIE, readBackendError, sessionCookieOptions } from '@/lib/server-session';

export async function POST(request: NextRequest) {
  const response = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: await request.text(),
    cache: 'no-store',
  });
  if (!response.ok) return NextResponse.json(await readBackendError(response), { status: response.status });

  const body = await response.json();

  const { token, ...safeBody } = body;
  const result = NextResponse.json(safeBody);
  result.cookies.set(SESSION_COOKIE, token, sessionCookieOptions);
  if (body.selectedProjectId) {
    result.cookies.set(PROJECT_COOKIE, body.selectedProjectId, sessionCookieOptions);
  }
  return result;
}
