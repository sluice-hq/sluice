'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError('');
    const data = new FormData(event.currentTarget);
    const response = await fetch('/api/session/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: data.get('email'), password: data.get('password') }),
    });
    if (!response.ok) {
      const body = await response.json(); setError(body.detail || 'Sign in failed.'); setBusy(false); return;
    }
    router.replace('/'); router.refresh();
  }

  return <AuthCard title="Welcome back" subtitle="Sign in to manage your media pipelines.">
    <form onSubmit={submit} className="space-y-4">
      <Input name="email" type="email" placeholder="you@example.com" required />
      <Input name="password" type="password" placeholder="Password" required />
      {error && <p className="text-sm text-red-400">{error}</p>}
      <Button className="w-full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</Button>
      <p className="text-sm text-center text-muted-foreground">New to Sluice? <Link className="text-primary" href="/signup">Create an account</Link></p>
    </form>
  </AuthCard>;
}

function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return <main className="min-h-screen grid place-items-center bg-background p-6"><section className="w-full max-w-md rounded-2xl border border-border bg-card p-8 shadow-2xl">
    <h1 className="text-3xl font-bold">{title}</h1><p className="mt-2 mb-8 text-muted-foreground">{subtitle}</p>{children}
  </section></main>;
}
