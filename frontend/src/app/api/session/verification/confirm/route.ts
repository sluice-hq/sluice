import { NextRequest } from 'next/server';
import { forwardPublicAuth } from '@/lib/public-auth';

export async function POST(request: NextRequest) {
  return forwardPublicAuth(request, '/verification/confirm');
}
