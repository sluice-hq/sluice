import { NextRequest, NextResponse } from 'next/server';
import { API_URL, CSRF_COOKIE, PROJECT_COOKIE, SESSION_COOKIE, csrfCookieOptions, newCsrfToken, readBackendError, sessionCookieOptions } from '@/lib/server-session';

export async function POST(request: NextRequest) {
  try {
    const response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: await request.text(),
      cache: 'no-store',
    });
    if (!response.ok) return NextResponse.json(await readBackendError(response), { status: response.status });

    const text = await response.text();
    let body: { token?: unknown; selectedProjectId?: unknown };
    try {
      body = JSON.parse(text);
    } catch {
      return NextResponse.json({ detail: 'The account service returned an unexpected response.' }, { status: 502 });
    }

    if (typeof body.token !== 'string') {
      return NextResponse.json({ detail: 'The account service returned an incomplete response.' }, { status: 502 });
    }

    const { token, ...safeBody } = body;
    const result = NextResponse.json(safeBody);
    result.cookies.set(SESSION_COOKIE, token, sessionCookieOptions);
    result.cookies.set(CSRF_COOKIE, newCsrfToken(), csrfCookieOptions);
    if (typeof body.selectedProjectId === 'string') {
      result.cookies.set(PROJECT_COOKIE, body.selectedProjectId, sessionCookieOptions);
    }
    return result;
  } catch {
    return NextResponse.json({ detail: 'The account service is unavailable. Please try again.' }, { status: 503 });
  }
}
