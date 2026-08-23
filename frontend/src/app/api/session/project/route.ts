import { NextRequest, NextResponse } from 'next/server';
import { API_URL, PROJECT_COOKIE, readBackendError, serverAuthHeaders, sessionCookieOptions } from '@/lib/server-session';

export async function POST(request: NextRequest) {
  const { projectId } = await request.json();
  const response = await fetch(`${API_URL}/projects`, {
    headers: await serverAuthHeaders(),
    cache: 'no-store',
  });
  if (!response.ok) return NextResponse.json(await readBackendError(response), { status: response.status });
  const projects = await response.json() as Array<{ id: string }>;
  if (!projects.some((project) => project.id === projectId)) {
    return NextResponse.json({ detail: 'Project is not available.' }, { status: 403 });
  }
  const result = NextResponse.json({ selectedProjectId: projectId });
  result.cookies.set(PROJECT_COOKIE, projectId, sessionCookieOptions);
  return result;
}
