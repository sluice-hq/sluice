'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { AuthShell } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/button';

export default function ConfirmEmailPage() {
  return <Suspense fallback={<AuthShell eyebrow="EMAIL VERIFICATION" title="Confirm your email" subtitle="Loading verification details…"><p role="status" className="mt-9 text-sm text-muted-foreground">Loading…</p></AuthShell>}><ConfirmEmailForm /></Suspense>;
}

function ConfirmEmailForm() {
  const token = useSearchParams().get('token') ?? '';
  const [message, setMessage] = useState('');
  const [error, setError] = useState(token ? '' : 'This verification link is incomplete. Request a new one from the verification page.');
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (!token || busy) return;
    setBusy(true); setError('');
    try {
      const response = await fetch('/api/session/verification/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) });
      if (!response.ok) {
        if (response.status === 429) setError('Too many verification attempts. Wait before trying again or requesting a new link.');
        else if (response.status >= 500) setError('Email verification is temporarily unavailable. Please try again shortly.');
        else setError('This verification link is invalid or has expired. Request a new verification link to continue.');
        return;
      }
      setMessage('Your email is verified. You can return to your dashboard.');
    } catch {
      setError('We could not reach Sluice. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  return <AuthShell eyebrow="EMAIL VERIFICATION" title="Confirm your email" subtitle="This link can be used once and expires automatically for your protection.">
    <div className="mt-9 space-y-5">
      {error && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">{error}</div>}
      {message && <div role="status" className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2.5 text-sm">{message}</div>}
      <Button type="button" className="h-11 w-full text-sm font-semibold" disabled={!token || busy || Boolean(message)} aria-busy={busy} onClick={confirm}>{busy ? 'Verifying email…' : 'Verify email'}</Button>
      <p className="text-center text-sm text-muted-foreground">{message ? <><Link className="text-primary underline-offset-4 hover:underline" href="/app">Open dashboard</Link> or <Link className="text-primary underline-offset-4 hover:underline" href="/login">sign in</Link></> : <Link className="text-primary underline-offset-4 hover:underline" href="/verify-email">Request a new link</Link>}</p>
    </div>
  </AuthShell>;
}
