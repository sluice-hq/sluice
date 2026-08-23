import { NextResponse } from 'next/server';
import { PROJECT_COOKIE, SESSION_COOKIE } from '@/lib/server-session';

export async function POST() {
  const response = new NextResponse(null, { status: 204 });
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(PROJECT_COOKIE);
  return response;
}
