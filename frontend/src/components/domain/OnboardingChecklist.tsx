'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Check, Circle, KeyRound, Rocket, Workflow } from 'lucide-react';
import { listApiKeys } from '@/api/identity';
import { getPublishedPipelines } from '@/api/pipelines';
import { getDashboardOverview } from '@/api/dashboard';

interface Session {
  projects: Array<{ id: string; name: string }>;
  selectedProjectId?: string;
}

export function OnboardingChecklist() {
  const sessionQuery = useQuery<Session>({
    queryKey: ['session'],
    queryFn: async () => {
      const response = await fetch('/api/session', { cache: 'no-store' });
      if (!response.ok) throw new Error('Could not load the selected project.');
      return response.json();
    },
  });
  const projectId = sessionQuery.data?.selectedProjectId;
  const keysQuery = useQuery({
    queryKey: ['api-keys', projectId],
    queryFn: () => listApiKeys(projectId!),
    enabled: Boolean(projectId),
  });
  const pipelinesQuery = useQuery({ queryKey: ['published-pipelines', projectId], queryFn: getPublishedPipelines, enabled: Boolean(projectId) });
  const runsQuery = useQuery({ queryKey: ['dashboard', 'onboarding', projectId], queryFn: getDashboardOverview, enabled: Boolean(projectId) });

  const loading = sessionQuery.isLoading || keysQuery.isLoading || pipelinesQuery.isLoading || runsQuery.isLoading;
  const failed = sessionQuery.isError || keysQuery.isError || pipelinesQuery.isError || runsQuery.isError;
  if (loading) return <div className="h-40 animate-pulse rounded-xl border border-border bg-card/60" aria-label="Loading onboarding progress" />;
  if (failed || !projectId) {
    return <div role="alert" className="rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">Onboarding progress could not be loaded. Refresh to try again.</div>;
  }

  const steps = [
    { title: 'Create an API key', description: 'Generate a project-scoped key and save its one-time secret.', done: (keysQuery.data ?? []).some((key) => !key.revokedAt), href: '/settings', icon: KeyRound },
    { title: 'Publish a pipeline', description: 'Publish an immutable version with the stable alias.', done: (pipelinesQuery.data ?? []).length > 0, href: '/pipelines', icon: Workflow },
    { title: 'Complete your first run', description: 'Use the API flow and wait for a successful terminal result.', done: (runsQuery.data?.completedJobs ?? 0) > 0, href: '/quick-start', icon: Rocket },
  ];
  if (steps.every((step) => step.done)) return null;
  const project = sessionQuery.data?.projects.find((candidate) => candidate.id === projectId);

  return (
    <section aria-labelledby="onboarding-title" className="rounded-2xl border border-primary/25 bg-[linear-gradient(135deg,rgb(35_149_255_/_0.12),transparent_65%)] p-5 sm:p-6">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div><p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">First-run checklist</p><h2 id="onboarding-title" className="mt-2 text-xl font-semibold">Start your first integration</h2><p className="mt-1 text-sm text-muted-foreground">Live progress for {project?.name ?? 'the selected project'}.</p></div>
        <Link href="/quick-start" className="text-sm font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">Open API Quick Start</Link>
      </div>
      <ol className="mt-5 grid gap-3 lg:grid-cols-3">
        {steps.map((step) => <li key={step.title} className="rounded-xl border border-border/80 bg-card/80 p-4"><div className="flex items-start gap-3">{step.done ? <Check className="mt-0.5 size-5 text-emerald-400" aria-label="Complete" /> : <Circle className="mt-0.5 size-5 text-muted-foreground" aria-label="Not complete" />}<div><div className="flex items-center gap-2"><step.icon className="size-4 text-primary" aria-hidden="true" /><Link href={step.href} className="font-medium hover:text-primary">{step.title}</Link></div><p className="mt-1 text-xs leading-5 text-muted-foreground">{step.description}</p></div></div></li>)}
      </ol>
    </section>
  );
}
