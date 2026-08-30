import { NextRequest, NextResponse } from 'next/server';
import { API_URL, CSRF_COOKIE, PROJECT_COOKIE, SESSION_COOKIE, backendResponseHeaders, csrfCookieOptions, forwardedClientHeaders, hasTrustedBrowserOrigin, newCsrfToken, readBackendError, sessionCookieOptions } from '@/lib/server-session';

export async function POST(request: NextRequest) {
  if (!hasTrustedBrowserOrigin(request)) {
    return NextResponse.json(
      { status: 403, code: 'csrf_rejected', detail: 'The request must originate from Sluice.' },
      { status: 403 },
    );
  }
  try {
    const response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...forwardedClientHeaders(request) },
      body: await request.text(),
      cache: 'no-store',
    });
    if (!response.ok) {
      return NextResponse.json(await readBackendError(response), {
        status: response.status,
        headers: backendResponseHeaders(response),
      });
    }

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
