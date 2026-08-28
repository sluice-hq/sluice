import { NextRequest, NextResponse } from 'next/server';
import { API_URL, hasValidCsrfToken, serverAuthHeaders } from '@/lib/server-session';

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext) {
  if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method) && !hasValidCsrfToken(request)) {
    return NextResponse.json(
      { status: 403, code: 'csrf_rejected', detail: 'The request is missing valid CSRF protection.' },
      { status: 403, headers: { 'content-type': 'application/problem+json' } },
    );
  }

  const { path } = await context.params;
  const target = `${API_URL}/${path.join('/')}${request.nextUrl.search}`;
  const headers = new Headers(await serverAuthHeaders());
  const contentType = request.headers.get('content-type');
  const accept = request.headers.get('accept');
  const idempotencyKey = request.headers.get('idempotency-key');
  if (contentType) headers.set('content-type', contentType);
  if (accept) headers.set('accept', accept);
  if (idempotencyKey) headers.set('idempotency-key', idempotencyKey);

  const response = await fetch(target, {
    method: request.method,
    headers,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.arrayBuffer(),
    cache: 'no-store',
  });
  return new NextResponse(response.body, {
    status: response.status,
    headers: { 'content-type': response.headers.get('content-type') || 'application/json' },
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
