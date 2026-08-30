'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { AuthShell } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedEmail = email.trim();
    if (!emailPattern.test(normalizedEmail)) { setError('Enter a valid email address.'); setMessage(''); return; }
    setBusy(true); setError(''); setMessage('');
    try {
      const response = await fetch('/api/session/recovery', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: normalizedEmail }) });
      if (!response.ok) {
        if (response.status === 429) {
          const retryAfter = response.headers.get('Retry-After');
          setError(retryAfter
            ? `Too many recovery requests. Try again in ${retryAfter} seconds.`
            : 'Too many recovery requests. Wait before trying again.');
          return;
        }
        throw new Error();
      }
      setMessage('If an account exists for this address, we sent a password reset link. Check your inbox and follow the link to continue.');
    } catch {
      setError('We could not start password recovery right now. Please try again shortly.');
    } finally { setBusy(false); }
  }

  return <AuthShell eyebrow="ACCOUNT RECOVERY" title="Reset your password" subtitle="Enter your email and we will send a single-use reset link if an account is eligible.">
    <form noValidate onSubmit={submit} className="mt-9 space-y-5">
      {error && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">{error}</div>}
      {message && <div role="status" className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2.5 text-sm">{message}</div>}
      <div className="space-y-2"><label htmlFor="email" className="text-sm font-medium">Email address</label><Input id="email" name="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} className="h-11" /><p className="text-xs text-muted-foreground">For privacy, the response is the same whether or not an account exists.</p></div>
      <Button type="submit" className="h-11 w-full text-sm font-semibold" disabled={busy} aria-busy={busy}>{busy ? 'Sending reset link…' : 'Send reset link'}</Button>
      <p className="text-center text-sm text-muted-foreground">Remembered it? <Link className="text-primary underline-offset-4 hover:underline" href="/login">Back to sign in</Link></p>
    </form>
  </AuthShell>;
}
