'use client';

import { FormEvent, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { createApiKey, createProject, listApiKeys, revokeApiKey, type ApiKeySummary } from '@/api/identity';
import { csrfFetch } from '@/lib/csrf';

interface Session {
  user: { email: string };
  projects: Array<{ id: string; name: string; role: string }>;
  selectedProjectId: string;
}

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const { data: session } = useQuery<Session>({ queryKey: ['session'], queryFn: () => fetch('/api/session').then((r) => r.json()) });
  const projectId = session?.selectedProjectId;
  const { data: keys = [], refetch } = useQuery<ApiKeySummary[]>({
    queryKey: ['api-keys', projectId], queryFn: () => listApiKeys(projectId!), enabled: Boolean(projectId),
  });
  const [revealedKey, setRevealedKey] = useState('');
  const [message, setMessage] = useState('');

  async function addProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    const project = await createProject(String(data.get('name')));
    await csrfFetch('/api/session/project', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectId: project.id }) });
    form.reset(); await queryClient.invalidateQueries({ queryKey: ['session'] }); window.location.reload();
  }

  async function addKey(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!projectId) return;
    const form = event.currentTarget; const data = new FormData(form);
    const created = await createApiKey(projectId, String(data.get('name')));
    setRevealedKey(created.value); setMessage('Copy this key now. It cannot be shown again.'); form.reset(); await refetch();
  }

  async function revoke(keyId: string) {
    if (!projectId || !window.confirm('Revoke this API key? Applications using it will stop working.')) return;
    await revokeApiKey(projectId, keyId); await refetch();
  }

  return <div className="max-w-4xl space-y-8">
    <header><h1 className="text-2xl font-bold">Developer settings</h1><p className="mt-1 text-muted-foreground">Manage projects and application credentials.</p></header>

    <section className="rounded-xl border border-border bg-card p-6 space-y-4">
      <h2 className="text-lg font-semibold">Projects</h2>
      <div className="grid gap-2">{session?.projects.map((project) =>
        <div key={project.id} className="flex justify-between rounded-lg border border-border px-4 py-3">
          <span>{project.name}</span><span className="text-xs text-muted-foreground">{project.role}{project.id === projectId ? ' · selected' : ''}</span>
        </div>)}</div>
      <form onSubmit={addProject} className="flex gap-2"><Input name="name" placeholder="New project name" maxLength={100} required /><Button type="submit">Create project</Button></form>
    </section>

    <section className="rounded-xl border border-border bg-card p-6 space-y-4">
      <div><h2 className="text-lg font-semibold">API keys</h2><p className="text-sm text-muted-foreground">Use these from your applications. Only hashes are stored by Sluice.</p></div>
      {revealedKey && <div className="rounded-lg border border-primary/40 bg-primary/10 p-4 space-y-2"><p className="text-sm">{message}</p><div className="flex gap-2"><Input readOnly value={revealedKey} /><Button type="button" onClick={() => navigator.clipboard.writeText(revealedKey)}>Copy</Button></div></div>}
      <form onSubmit={addKey} className="flex gap-2"><Input name="name" placeholder="Key name, e.g. storefront-dev" maxLength={100} required /><Button type="submit">Create API key</Button></form>
      <div className="divide-y divide-border">{keys.map((key) => <div key={key.id} className="flex items-center justify-between py-3">
        <div><p className="font-medium">{key.name}</p><p className="text-xs text-muted-foreground">Created {new Date(key.createdAt).toLocaleDateString()} · {key.lastUsedAt ? `last used ${new Date(key.lastUsedAt).toLocaleString()}` : 'never used'}</p></div>
        {key.revokedAt ? <span className="text-xs text-muted-foreground">Revoked</span> : <Button variant="destructive" onClick={() => revoke(key.id)}>Revoke</Button>}
      </div>)}</div>
    </section>
  </div>;
}
