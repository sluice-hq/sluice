'use client';

import Link from 'next/link';
import { FormEvent, Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { AuthShell, PasswordInput } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/button';

export default function ResetPasswordPage() {
  return <Suspense fallback={<AuthShell eyebrow="CHOOSE A NEW PASSWORD" title="Reset your password" subtitle="Loading reset details…"><p role="status" className="mt-9 text-sm text-muted-foreground">Loading…</p></AuthShell>}><ResetPasswordForm /></Suspense>;
}

function ResetPasswordForm() {
  const token = useSearchParams().get('token') ?? '';
  const [error, setError] = useState(token ? '' : 'This reset link is incomplete. Request a new reset link to continue.');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || busy) return;
    const data = new FormData(event.currentTarget);
    const password = String(data.get('password') ?? '');
    const confirmation = String(data.get('passwordConfirmation') ?? '');
    if (password.length < 12 || password.length > 128) { setError('Use a password between 12 and 128 characters.'); return; }
    if (password !== confirmation) { setError('The passwords do not match.'); return; }

    setBusy(true); setError(''); setMessage('');
    try {
      const response = await fetch('/api/session/reset', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }) });
      if (!response.ok) {
        if (response.status === 429) setError('Too many reset attempts. Wait before trying again or requesting a new link.');
        else if (response.status >= 500) setError('Password reset is temporarily unavailable. Please try again shortly.');
        else setError('This reset link is invalid or has expired. Request a new reset link to continue.');
        return;
      }
      setMessage('Your password was reset. Existing dashboard sessions have been signed out. Sign in with your new password.');
    } catch {
      setError('We could not reach Sluice. Check your connection and try again.');
    } finally { setBusy(false); }
  }

  return <AuthShell eyebrow="CHOOSE A NEW PASSWORD" title="Reset your password" subtitle="This reset link can be used once and expires automatically for your protection.">
    <form noValidate onSubmit={submit} className="mt-9 space-y-5">
      {error && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">{error}</div>}
      {message && <div role="status" className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2.5 text-sm">{message}</div>}
      <div className="space-y-2"><label htmlFor="password" className="text-sm font-medium">New password</label><PasswordInput id="password" autoComplete="new-password" minLength={12} maxLength={128} invalid={Boolean(error)} describedBy="password-help" onChange={() => setError('')} /><p id="password-help" className="text-xs text-muted-foreground">Use 12 to 128 characters.</p></div>
      <div className="space-y-2"><label htmlFor="passwordConfirmation" className="text-sm font-medium">Confirm new password</label><PasswordInput id="passwordConfirmation" name="passwordConfirmation" autoComplete="new-password" minLength={12} maxLength={128} invalid={Boolean(error)} describedBy="password-confirmation-help" onChange={() => setError('')} /><p id="password-confirmation-help" className="text-xs text-muted-foreground">Enter the same new password again.</p></div>
      <Button type="submit" className="h-11 w-full text-sm font-semibold" disabled={!token || busy || Boolean(message)} aria-busy={busy}>{busy ? 'Resetting password…' : 'Reset password'}</Button>
      <p className="text-center text-sm text-muted-foreground">{message ? <Link className="text-primary underline-offset-4 hover:underline" href="/login">Continue to sign in</Link> : <Link className="text-primary underline-offset-4 hover:underline" href="/forgot-password">Request a new reset link</Link>}</p>
    </form>
  </AuthShell>;
}
