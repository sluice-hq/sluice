'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function SignupPage() {
  const router = useRouter();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    const data = new FormData(event.currentTarget);
    const response = await fetch('/api/session/signup', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: data.get('email'), password: data.get('password'), projectName: data.get('projectName') }),
    });
    if (!response.ok) {
      const body = await response.json(); setError(body.detail || 'Account creation failed.'); setBusy(false); return;
    }
    router.replace('/'); router.refresh();
  }

  return <main className="min-h-screen grid place-items-center bg-background p-6"><section className="w-full max-w-md rounded-2xl border border-border bg-card p-8 shadow-2xl">
    <h1 className="text-3xl font-bold">Create your workspace</h1><p className="mt-2 mb-8 text-muted-foreground">Get an API key and run your first pipeline.</p>
    <form onSubmit={submit} className="space-y-4">
      <Input name="email" type="email" placeholder="you@example.com" required />
      <Input name="password" type="password" minLength={12} placeholder="Password (12+ characters)" required />
      <Input name="projectName" placeholder="Project name" maxLength={100} required />
      {error && <p className="text-sm text-red-400">{error}</p>}
      <Button className="w-full" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</Button>
      <p className="text-sm text-center text-muted-foreground">Already registered? <Link className="text-primary" href="/login">Sign in</Link></p>
    </form>
  </section></main>;
}
