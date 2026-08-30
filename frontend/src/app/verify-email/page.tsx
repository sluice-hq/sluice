'use client';

import Link from 'next/link';
import { FormEvent, Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { AuthShell } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function VerifyEmailPage() {
  return <Suspense fallback={<AuthShell eyebrow="SECURE YOUR ACCOUNT" title="Verify your email" subtitle="Loading verification details…"><p role="status" className="mt-9 text-sm text-muted-foreground">Loading…</p></AuthShell>}><VerifyEmailForm /></Suspense>;
}

function VerifyEmailForm() {
  const searchParams = useSearchParams();
  const signupEmail = searchParams.get('email') ?? '';
  const [email, setEmail] = useState(signupEmail);
  const [message, setMessage] = useState(signupEmail ? 'We sent a verification link to this address. Check your inbox and follow the link to finish verification.' : '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedEmail = email.trim();
    if (!emailPattern.test(normalizedEmail)) {
      setError('Enter a valid email address.');
      setMessage('');
      return;
    }

    setBusy(true); setError(''); setMessage('');
    try {
      const response = await fetch('/api/session/verification/request', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: normalizedEmail }) });
      if (!response.ok) {
        if (response.status === 429) {
          const retryAfter = response.headers.get('Retry-After');
          setError(retryAfter
            ? `Too many verification requests. Try again in ${retryAfter} seconds.`
            : 'Too many verification requests. Wait before trying again.');
          return;
        }
        throw new Error();
      }
      setMessage('If this address has an unverified Sluice account, we sent a verification link. Check your inbox and follow the link to finish verification.');
    } catch {
      setError('We could not send a verification message right now. Please try again shortly.');
    } finally {
      setBusy(false);
    }
  }

  return <AuthShell eyebrow="SECURE YOUR ACCOUNT" title="Verify your email" subtitle="Confirm an address you control before relying on this account for production work.">
    <form noValidate onSubmit={submit} className="mt-9 space-y-5">
      {error && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">{error}</div>}
      {message && <div role="status" className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2.5 text-sm">{message}</div>}
      <div className="space-y-2"><label htmlFor="email" className="text-sm font-medium">Email address</label><Input id="email" name="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} className="h-11" /><p className="text-xs text-muted-foreground">We will never show whether an account exists for an address.</p></div>
      <Button type="submit" className="h-11 w-full text-sm font-semibold" disabled={busy} aria-busy={busy}>{busy ? 'Sending verification link…' : message ? 'Send another verification link' : 'Send verification link'}</Button>
      <p className="text-center text-sm text-muted-foreground">You can continue using the dashboard while verification is pending. <Link className="text-primary underline-offset-4 hover:underline" href="/app">Open dashboard</Link></p>
    </form>
  </AuthShell>;
}
