'use client';

import { useQuery } from '@tanstack/react-query';
import { getRun } from '@/api/runs';
import type { Asset, RunDetails } from '@/api/types';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button, buttonVariants } from '@/components/ui/button';
import { formatBytes, formatDuration, formatFact } from '@/lib/utils';
import { ArrowLeft, Activity, Download, FileVideo, RotateCcw, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { use } from 'react';
import { useSearchParams } from 'next/navigation';
import { useJobEvents } from '@/hooks/useJobEvents';

const settledStatuses = new Set(['FAILED', 'REVIEW_REQUIRED']);

function structuredFact(value: unknown): string {
  if (value == null) return 'Not available';
  if (typeof value === 'string') return value || 'Not available';
  try {
    return JSON.stringify(value);
  } catch {
    return 'Not available';
  }
}

function ErrorFact({ error }: { error: RunDetails['error'] }) {
  return error ? <p className="mt-1 break-words text-sm"><span className="font-mono font-medium">{error.code}</span>: {error.message}</p> : <p className="mt-1 text-sm text-muted-foreground">Not available</p>;
}

function DownloadOutputButton({ output }: { output: Asset }) {
  return <a aria-label={`Download output ${output.filename}`} className={buttonVariants({ size: 'sm' })} href={`/api/downloads/assets/${output.id}`}>
    <Download className="mr-2 size-4" />Download output
  </a>;
}

function Stat({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border border-border/70 bg-background/20 p-3">
    <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
    <dd className="mt-1 break-words text-sm font-semibold">{value}</dd>
  </div>;
}

export default function JobDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  const searchParams = useSearchParams();
  const returnToGovernance = searchParams.get('returnTo') === 'governance';
  const backParams = new URLSearchParams(searchParams.toString());
  backParams.delete('returnTo');
  const backQuery = backParams.toString();
  const backBase = returnToGovernance ? '/governance' : '/jobs';
  const backHref = backQuery ? `${backBase}?${backQuery}` : backBase;
  const backLabel = returnToGovernance ? 'Back to Governance' : 'Back to Runs';
  const { data: run, isLoading, error } = useQuery({
    queryKey: ['run', resolvedParams.id],
    queryFn: () => getRun(resolvedParams.id),
    refetchInterval: (query) => {
      const currentRun = query.state.data;
      if (!currentRun || settledStatuses.has(currentRun.status)) return false;
      // Keep the UX-07 safeguard: a completed run remains live until its output is visible.
      return currentRun.status === 'COMPLETED' && currentRun.outputs.length > 0 ? false : 2000;
    },
  });

  useJobEvents(run?.id, run?.status);

  if (error) {
    return <div className="space-y-6">
      <Link href={backHref}><Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground"><ArrowLeft className="mr-2 size-4" />{backLabel}</Button></Link>
      <EmptyState title="Error loading run" description={error.message} />
    </div>;
  }

  return <div className="space-y-6">
    <Link href={backHref}><Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground"><ArrowLeft className="mr-2 size-4" />{backLabel}</Button></Link>

    {isLoading ? <div className="h-64 animate-pulse rounded-xl border border-border bg-card" /> : run ? <div className="space-y-6">
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6 shadow-[0_18px_40px_rgb(0_0_0_/_0.16)] sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-2xl font-bold tracking-tight"><Activity className="size-6 text-primary" />Run details</h2>
          <p className="mt-2 font-mono text-sm text-muted-foreground">{run.id}</p>
          <p className="mt-2 text-sm text-muted-foreground">Pipeline <span className="font-medium text-foreground">{formatFact(run.pipeline?.slug)}</span> · exact version <span className="font-mono text-foreground">v{run.pipeline?.version ?? 'Not available'}</span></p>
        </div>
        <StatusBadge className="text-sm" status={run.status} />
      </div>

      <dl className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Queue wait" value={formatDuration(run.metrics.queueWaitMs)} />
        <Stat label="Processing time" value={formatDuration(run.metrics.processingMs)} />
        <Stat label="Input bytes" value={formatBytes(run.metrics.inputBytes)} />
        <Stat label="Output bytes" value={formatBytes(run.metrics.outputBytes)} />
        <Stat label="Saved bytes" value={formatBytes(run.metrics.bytesSaved)} />
        <Stat label="Compression" value={run.metrics.compressionRatio == null ? 'Not available' : `${(run.metrics.compressionRatio * 100).toFixed(1)}% output/input`} />
        <Stat label="Created" value={run.createdAt ? new Date(run.createdAt).toLocaleString() : 'Not available'} />
        <Stat label="Updated" value={run.updatedAt ? new Date(run.updatedAt).toLocaleString() : 'Not available'} />
      </dl>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="run-input-heading">
          <div className="flex items-center gap-2"><FileVideo className="size-5 text-primary" /><h3 id="run-input-heading" className="font-semibold">Input asset</h3></div>
          {run.inputAssetId ? <Link href={`/assets/${run.inputAssetId}`} className="mt-4 inline-block font-mono text-sm text-primary hover:underline">View {run.inputAssetId}</Link> : <p className="mt-4 text-sm text-muted-foreground">Not available</p>}
        </section>
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="run-error-heading">
          <h3 id="run-error-heading" className="font-semibold">Run error</h3>
          <ErrorFact error={run.error} />
        </section>
      </div>

      <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="step-details-heading">
        <h3 id="step-details-heading" className="font-semibold">Step details</h3>
        {run.steps.length ? <ol className="mt-4 space-y-3">
          {run.steps.map((step) => <li key={step.id} className="rounded-lg border border-border/70 p-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="font-medium">{formatFact(step.stepId)} <span className="font-mono text-sm text-muted-foreground">{formatFact(step.processor)}@{formatFact(step.version)}</span></p>
                <p className="mt-1 text-sm text-muted-foreground">Attempt {step.attempt ?? 'Not available'} · {step.inputMimeType ?? 'Not available'} → {step.outputMimeType ?? 'Not available'}</p>
              </div>
              <StatusBadge status={step.status} />
            </div>
            <dl className="mt-3 grid gap-3 sm:grid-cols-3">
              <Stat label="Duration" value={formatDuration(step.durationMs)} />
              <Stat label="Input" value={formatBytes(step.inputBytes)} />
              <Stat label="Output" value={formatBytes(step.outputBytes)} />
            </dl>
            <div className="mt-3 text-sm"><span className="font-medium">Safe error</span><ErrorFact error={step.error} /></div>
            {step.outputAssetId ? <Link href={`/assets/${step.outputAssetId}`} className="mt-3 inline-block text-sm text-primary hover:underline">View step output asset</Link> : null}
          </li>)}
        </ol> : <p className="mt-3 text-sm text-muted-foreground">Not available</p>}
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="outputs-heading">
          <h3 id="outputs-heading" className="font-semibold">Outputs and compression</h3>
          {run.outputs.length ? <div className="mt-4 space-y-3">
            {run.outputs.map((output) => <div key={output.id} className="rounded-lg border border-border/70 p-4">
              <Link href={`/assets/${output.id}`} className="font-medium hover:text-primary hover:underline">View {output.filename}</Link>
              <p className="mt-1 text-sm text-muted-foreground">{formatFact(output.contentType)} · {formatBytes(output.size)}</p>
              <div className="mt-3"><DownloadOutputButton output={output} /></div>
            </div>)}
          </div> : <p className="mt-3 text-sm text-muted-foreground">Not available</p>}
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="attempts-heading">
          <div className="flex items-center gap-2"><RotateCcw className="size-5 text-primary" /><h3 id="attempts-heading" className="font-semibold">Retry attempts</h3></div>
          {run.attempts.length ? <ol className="mt-4 space-y-3">
            {run.attempts.map((attempt) => <li key={attempt.attempt} className="rounded-lg border border-border/70 p-3 text-sm">
              <div className="flex items-center justify-between gap-3"><span className="font-medium">Attempt {attempt.attempt}</span><StatusBadge status={attempt.status} /></div>
              <p className="mt-2 text-muted-foreground">{!['COMPLETED', 'FAILED'].includes(attempt.status)
                ? 'In progress; no failure recorded yet.'
                : attempt.error == null
                ? 'Completed without a recorded failure.'
                : attempt.transientFailure === true
                  ? 'Transient failure — eligible for retry.'
                  : attempt.transientFailure === false
                    ? 'Permanent failure — not automatically retried.'
                    : 'Failure semantics: Not available'}</p>
              <p className="mt-1 text-muted-foreground">Started: {attempt.startedAt ? new Date(attempt.startedAt).toLocaleString() : 'Not available'} · Completed: {attempt.completedAt ? new Date(attempt.completedAt).toLocaleString() : 'Not available'}</p>
              <ErrorFact error={attempt.error} />
            </li>)}
          </ol> : <p className="mt-3 text-sm text-muted-foreground">Not available</p>}
        </section>
      </div>

      <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="governance-heading">
        <div className="flex items-center gap-2"><ShieldCheck className="size-5 text-primary" /><h3 id="governance-heading" className="font-semibold">Governance</h3></div>
        {run.governance ? <dl className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <Stat label="Decision" value={formatFact(run.governance.decision)} />
          <Stat label="Policy version" value={formatFact(run.governance.policyVersion)} />
          <Stat label="Provider" value={formatFact(run.governance.provider)} />
          <Stat label="Model" value={formatFact(run.governance.modelVersion)} />
          <Stat label="Categories" value={structuredFact(run.governance.categoryScores)} />
          <Stat label="Reasons" value={structuredFact(run.governance.reasonCodes)} />
        </dl> : <p className="mt-3 text-sm text-muted-foreground">Not available</p>}
      </section>
    </div> : <EmptyState title="Run not found" description="The requested run does not exist." />}
  </div>;
}
