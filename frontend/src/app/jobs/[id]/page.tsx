'use client';

import { useQuery } from '@tanstack/react-query';
import { getJob } from '@/api/jobs';
import { getRun } from '@/api/runs';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Activity, Calendar, FileVideo, Clock } from 'lucide-react';
import Link from 'next/link';
import { use } from 'react';
import { useJobEvents } from '@/hooks/useJobEvents';

export default function JobDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);

  const { data: job, isLoading, error } = useQuery({
    queryKey: ['job', resolvedParams.id],
    queryFn: () => getJob(resolvedParams.id),
    refetchInterval: (query) => {
      // Don't refetch if completed or failed, otherwise poll every 2 seconds until SSE is implemented
      const status = query.state.data?.status;
      return (status === 'COMPLETED' || status === 'FAILED') ? false : 2000;
    },
  });
  const { data: run } = useQuery({
    queryKey: ['run', resolvedParams.id],
    queryFn: () => getRun(resolvedParams.id),
    refetchInterval: job && !['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(job.status) ? false : 2000,
  });

  useJobEvents(job?.id, job?.status);

  if (error) {
    return (
      <div className="space-y-6">
        <Link href="/jobs">
          <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Runs
          </Button>
        </Link>
        <EmptyState title="Error Loading Job" description={error.message} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link href="/jobs">
        <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Runs
        </Button>
      </Link>

      {isLoading ? (
        <div className="h-64 animate-pulse rounded-xl border border-border bg-card"></div>
      ) : job ? (
        <div className="space-y-6">
          <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6 shadow-[0_18px_40px_rgb(0_0_0_/_0.16)] sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2">
                <Activity className="size-6 text-primary" />
                Run Details
              </h2>
              <p className="text-sm font-mono text-muted-foreground mt-2">{job.id}</p>
            </div>
            <StatusBadge className="text-sm" status={job.status} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="flex items-start gap-4 rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
               <FileVideo className="mt-0.5 size-5 text-muted-foreground" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">Asset</h3>
                  <Link href={`/assets/${job.assetId}`} className="mt-1 inline-block font-mono text-sm text-primary hover:underline">
                    {job.assetId.substring(0, 8)}...
                  </Link>
               </div>
            </div>

            <div className="flex items-start gap-4 rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
               <Calendar className="mt-0.5 size-5 text-muted-foreground" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">Created At</h3>
                  <p className="text-sm font-semibold mt-1">{new Date(job.createdAt).toLocaleString()}</p>
               </div>
            </div>

            <div className="flex items-start gap-4 rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
               <Clock className="mt-0.5 size-5 text-muted-foreground" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">Last Updated</h3>
                  <p className="text-sm font-semibold mt-1">{new Date(job.updatedAt).toLocaleString()}</p>
               </div>
            </div>
          </div>
          
          {run && <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
              <h3 className="font-semibold">Pipeline execution</h3>
              <p className="mt-1 text-sm text-muted-foreground">{run.pipeline.slug} · v{run.pipeline.version}</p>
              <div className="mt-4 space-y-2">
                {run.steps.map((step) => <div key={step.id} className="flex items-center justify-between rounded border p-3 text-sm">
                  <span>{step.stepId} · {step.processor}@{step.version}</span><StatusBadge status={step.status} />
                </div>)}
              </div>
            </section>
            <section className="rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
              <h3 className="font-semibold">Outputs and compression</h3>
              {run.outputs.length ? <div className="mt-3 space-y-2 text-sm">
                {run.outputs.map((output) => <Link key={output.id} href={`/assets/${output.id}`} className="block rounded border p-3 hover:border-primary">
                  <span className="font-medium">{output.filename}</span><span className="ml-2 text-muted-foreground">{output.contentType} · {output.size.toLocaleString()} bytes</span>
                </Link>)}
                <p className="text-muted-foreground">Saved {run.metrics.bytesSaved?.toLocaleString() ?? '—'} bytes · ratio {run.metrics.compressionRatio ?? '—'}</p>
              </div> : <p className="mt-3 text-sm text-muted-foreground">No normal output was produced.</p>}
            </section>
            <section className="rounded-xl border border-border bg-card p-6 shadow-[0_12px_30px_rgb(0_0_0_/_0.12)]">
              <h3 className="font-semibold">Governance</h3>
              {run.governance ? <div className="mt-3 space-y-2 text-sm"><p><span className="font-medium">Decision:</span> {run.governance.decision}</p><p><span className="font-medium">Provider:</span> {run.governance.provider}</p><p><span className="font-medium">Model:</span> {run.governance.modelVersion}</p><p><span className="font-medium">Reasons:</span> {Array.isArray(run.governance.reasonCodes) ? run.governance.reasonCodes.join(', ') : 'Recorded'}</p></div> : <p className="mt-3 text-sm text-muted-foreground">No governance decision was recorded for this run.</p>}
            </section>
          </div>}
        </div>
      ) : (
        <EmptyState title="Job Not Found" description="The requested job does not exist." />
      )}
    </div>
  );
}
