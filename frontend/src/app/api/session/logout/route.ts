import { NextRequest, NextResponse } from 'next/server';
import { CSRF_COOKIE, PROJECT_COOKIE, SESSION_COOKIE, hasValidCsrfToken } from '@/lib/server-session';

export async function POST(request: NextRequest) {
  if (!hasValidCsrfToken(request)) {
    return NextResponse.json({ status: 403, code: 'csrf_rejected', detail: 'The request is missing valid CSRF protection.' }, { status: 403 });
  }
  const response = new NextResponse(null, { status: 204 });
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(PROJECT_COOKIE);
  response.cookies.delete(CSRF_COOKIE);
  return response;
}
