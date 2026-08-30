import { NextRequest, NextResponse } from 'next/server';
import { API_URL, backendResponseHeaders, forwardedClientHeaders, hasTrustedBrowserOrigin, readBackendError } from '@/lib/server-session';

export async function forwardPublicAuth(request: NextRequest, path: string) {
  if (!hasTrustedBrowserOrigin(request)) {
    return NextResponse.json(
      { status: 403, code: 'csrf_rejected', detail: 'The request must originate from Sluice.' },
      { status: 403 },
    );
  }
  try {
    const response = await fetch(`${API_URL}/auth${path}`, {
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
    if (!text) return new NextResponse(null, { status: response.status });
    try {
      return NextResponse.json(JSON.parse(text), { status: response.status });
    } catch {
      return NextResponse.json({ detail: 'The account service returned an unexpected response.' }, { status: 502 });
    }
  } catch {
    return NextResponse.json({ detail: 'The account service is unavailable. Please try again.' }, { status: 503 });
  }
}
