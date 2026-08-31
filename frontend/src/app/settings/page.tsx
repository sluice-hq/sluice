'use client';

import { FormEvent, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { createApiKey, createProject, listApiKeys, revokeApiKey, type ApiKeySummary } from '@/api/identity';
import { csrfFetch } from '@/lib/csrf';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ActionStatus } from '@/components/ui/action-status';

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
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);

  const runAction = async (name: string, action: () => Promise<void>) => {
    if (pending) return false;
    setPending(name); setError(''); setMessage('');
    try { await action(); return true; } catch (cause) { setError(cause instanceof Error ? cause.message : 'Request failed. Please try again.'); return false; }
    finally { setPending(null); }
  };

  async function addProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    await runAction('project', async () => { const project = await createProject(String(data.get('name')));
      const response = await csrfFetch('/api/session/project', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectId: project.id }) });
      if (!response.ok) throw new Error('Project was created, but could not be selected. Your session remains usable.');
      const sessionResponse = await fetch('/api/session');
      if (!sessionResponse.ok) throw new Error('Project was selected, but fresh project data could not be loaded. Refresh to continue.');
      queryClient.setQueryData<Session>(['session'], await sessionResponse.json());
      await queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'session' });
      form.reset(); setMessage('Project created and selected.'); });
  }

  async function addKey(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!projectId) return;
    const form = event.currentTarget; const data = new FormData(form);
    await runAction('key', async () => { const created = await createApiKey(projectId, String(data.get('name')));
      setRevealedKey(created.value); setMessage('Copy this key now. It cannot be shown again.'); form.reset(); await refetch(); });
  }

  async function revoke(keyId: string): Promise<boolean> {
    if (!projectId) return false;
    return runAction(`revoke-${keyId}`, async () => { await revokeApiKey(projectId, keyId); await refetch(); setMessage('API key revoked.'); });
  }

  async function copyKey() {
    if (!revealedKey) return;
    setError('');
    try { await navigator.clipboard.writeText(revealedKey); setMessage('API key copied to clipboard.'); }
    catch { const input = document.querySelector<HTMLInputElement>('#revealed-api-key'); input?.focus(); input?.select(); setMessage('Clipboard unavailable. The key is selected; copy it manually.'); }
  }

  const pendingMessage = pending === 'project'
    ? 'Creating and selecting project...'
    : pending === 'key'
      ? 'Creating API key...'
      : pending?.startsWith('revoke-')
        ? 'Revoking API key...'
        : '';
  const statusMessage = error || pendingMessage || message;

  return <div className="max-w-4xl space-y-8">
    <header><h1 className="text-2xl font-bold">Developer settings</h1><p className="mt-1 text-muted-foreground">Manage projects and application credentials.</p></header>
    {statusMessage && <ActionStatus kind={error ? 'error' : pending ? 'pending' : 'success'} message={statusMessage} />}

    <section className="rounded-xl border border-border bg-card p-6 space-y-4">
      <h2 className="text-lg font-semibold">Projects</h2>
      <div className="grid gap-2">{session?.projects.map((project) =>
        <div key={project.id} className="flex justify-between rounded-lg border border-border px-4 py-3">
          <span>{project.name}</span><span className="text-xs text-muted-foreground">{project.role}{project.id === projectId ? ' · selected' : ''}</span>
        </div>)}</div>
      <form onSubmit={addProject} className="flex flex-col gap-2 sm:flex-row"><Input name="name" placeholder="New project name" maxLength={100} required disabled={pending !== null} /><Button type="submit" disabled={pending !== null}>{pending === 'project' ? 'Creating…' : 'Create project'}</Button></form>
    </section>

    <section className="rounded-xl border border-border bg-card p-6 space-y-4">
      <div><h2 className="text-lg font-semibold">API keys</h2><p className="text-sm text-muted-foreground">Use these from your applications. Only hashes are stored by Sluice.</p></div>
      {revealedKey && <div className="rounded-lg border border-primary/40 bg-primary/10 p-4 space-y-2"><div className="flex gap-2"><Input id="revealed-api-key" readOnly value={revealedKey} /><Button type="button" onClick={copyKey}>Copy</Button></div></div>}
      <form onSubmit={addKey} className="flex flex-col gap-2 sm:flex-row"><Input name="name" placeholder="Key name, e.g. storefront-dev" maxLength={100} required disabled={pending !== null} /><Button type="submit" disabled={pending !== null}>{pending === 'key' ? 'Creating…' : 'Create API key'}</Button></form>
      <div className="divide-y divide-border">{keys.map((key) => <div key={key.id} className="flex items-center justify-between py-3">
        <div><p className="font-medium">{key.name}</p><p className="text-xs text-muted-foreground">Created {new Date(key.createdAt).toLocaleDateString()} · {key.lastUsedAt ? `last used ${new Date(key.lastUsedAt).toLocaleString()}` : 'never used'}</p></div>
        {key.revokedAt ? <span className="text-xs text-muted-foreground">Revoked</span> : <Button variant="destructive" disabled={pending !== null} onClick={() => setRevokeTarget(key.id)}>Revoke</Button>}
      </div>)}</div>
    </section>
    <Dialog open={Boolean(revokeTarget)} onOpenChange={(open) => { if (!open && !pending) setRevokeTarget(null); }}>
      <DialogContent><DialogHeader><DialogTitle>Revoke API key?</DialogTitle><DialogDescription>Applications using this key will stop working immediately. This action cannot be undone.</DialogDescription></DialogHeader><DialogFooter><Button variant="outline" disabled={pending !== null} onClick={() => setRevokeTarget(null)}>Cancel</Button><Button variant="destructive" disabled={pending !== null} onClick={async () => { if (revokeTarget && await revoke(revokeTarget)) setRevokeTarget(null); }}>{pending?.startsWith('revoke-') ? 'Revoking…' : 'Revoke key'}</Button></DialogFooter></DialogContent>
    </Dialog>
  </div>;
}
