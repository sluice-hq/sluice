import { NextRequest, NextResponse } from 'next/server';
import { API_URL, serverAuthHeaders } from '@/lib/server-session';

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  const target = `${API_URL}/${path.join('/')}${request.nextUrl.search}`;
  const headers = new Headers(await serverAuthHeaders());
  const contentType = request.headers.get('content-type');
  const accept = request.headers.get('accept');
  if (contentType) headers.set('content-type', contentType);
  if (accept) headers.set('accept', accept);

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
