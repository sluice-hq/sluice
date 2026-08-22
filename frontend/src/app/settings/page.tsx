'use client';

import { FormEvent, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { clearAuthConfig, getAuthConfig, saveAuthConfig, type AuthMode } from '@/lib/auth';

export default function SettingsPage() {
  const [initialConfig] = useState(() => getAuthConfig());
  const [mode, setMode] = useState<AuthMode>(initialConfig?.mode || 'apiKey');
  const [apiKey, setApiKey] = useState(initialConfig?.apiKey || '');
  const [token, setToken] = useState(initialConfig?.token || '');
  const [projectId, setProjectId] = useState(initialConfig?.projectId || '');
  const [message, setMessage] = useState('');

  function handleSave(event: FormEvent) {
    event.preventDefault();
    saveAuthConfig(mode === 'apiKey' ? { mode, apiKey } : { mode, token, projectId });
    setMessage('Connection saved for this browser tab.');
  }

  function handleClear() {
    clearAuthConfig();
    setApiKey('');
    setToken('');
    setProjectId('');
    setMessage('Connection cleared.');
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">API Connection</h2>
        <p className="text-muted-foreground mt-1">
          Configure credentials for the current tab while the Phase 9 login flow is being built.
        </p>
      </div>

      <form onSubmit={handleSave} className="bg-card border border-border rounded-xl p-6 space-y-5">
        <div className="space-y-2">
          <label htmlFor="auth-mode" className="text-sm font-medium">Authentication method</label>
          <select
            id="auth-mode"
            value={mode}
            onChange={(event) => setMode(event.target.value as AuthMode)}
            className="w-full h-10 rounded-md border border-border bg-background px-3 text-sm"
          >
            <option value="apiKey">Project API key</option>
            <option value="jwt">User JWT and project ID</option>
          </select>
        </div>

        {mode === 'apiKey' ? (
          <div className="space-y-2">
            <label htmlFor="api-key" className="text-sm font-medium">API key</label>
            <Input id="api-key" type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} required />
          </div>
        ) : (
          <>
            <div className="space-y-2">
              <label htmlFor="jwt" className="text-sm font-medium">JWT</label>
              <Input id="jwt" type="password" value={token} onChange={(event) => setToken(event.target.value)} required />
            </div>
            <div className="space-y-2">
              <label htmlFor="project-id" className="text-sm font-medium">Project ID</label>
              <Input id="project-id" value={projectId} onChange={(event) => setProjectId(event.target.value)} required />
            </div>
          </>
        )}

        <p className="text-xs text-muted-foreground">
          Credentials are stored in session storage and disappear when this browser tab closes.
        </p>
        {message && <p className="text-sm text-primary">{message}</p>}
        <div className="flex gap-3">
          <Button type="submit">Save Connection</Button>
          <Button type="button" variant="outline" onClick={handleClear}>Clear</Button>
        </div>
      </form>
    </div>
  );
}
