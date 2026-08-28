import { NextResponse } from 'next/server';
import { API_URL, readBackendError, serverAuthHeaders } from '@/lib/server-session';

type RouteContext = { params: Promise<{ id: string }> };

function attachmentHeader(filename: string): string {
  const fallback = filename.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 180) || 'sluice-output';
  return `attachment; filename="${fallback}"; filename*=UTF-8''${encodeURIComponent(filename)}`;
}

export async function GET(_request: Request, context: RouteContext) {
  const { id } = await context.params;
  const auth = await serverAuthHeaders();
  const [assetResponse, linkResponse] = await Promise.all([
    fetch(`${API_URL}/assets/${encodeURIComponent(id)}`, { headers: auth, cache: 'no-store' }),
    fetch(`${API_URL}/assets/${encodeURIComponent(id)}/download`, { headers: auth, cache: 'no-store' }),
  ]);
  if (!assetResponse.ok) return NextResponse.json(await readBackendError(assetResponse), { status: assetResponse.status });
  if (!linkResponse.ok) return NextResponse.json(await readBackendError(linkResponse), { status: linkResponse.status });

  const asset = await assetResponse.json() as { filename?: unknown; contentType?: unknown };
  const link = await linkResponse.json() as { downloadUrl?: unknown };
  if (typeof asset.filename !== 'string' || typeof link.downloadUrl !== 'string') {
    return NextResponse.json({ detail: 'The output download response was incomplete.' }, { status: 502 });
  }

  const downloadResponse = await fetch(link.downloadUrl, { cache: 'no-store' });
  if (!downloadResponse.ok || !downloadResponse.body) {
    return NextResponse.json({ detail: 'The output could not be downloaded from storage.' }, { status: 502 });
  }
  return new NextResponse(downloadResponse.body, {
    status: 200,
    headers: {
      'cache-control': 'private, no-store',
      'content-disposition': attachmentHeader(asset.filename),
      'content-type': typeof asset.contentType === 'string'
        ? asset.contentType
        : downloadResponse.headers.get('content-type') || 'application/octet-stream',
    },
  });
}
