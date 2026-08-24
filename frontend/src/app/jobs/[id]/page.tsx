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

  useJobEvents(job?.id);

  if (error) {
    return (
      <div className="space-y-6">
        <Link href="/jobs">
          <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Jobs
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
          Back to Jobs
        </Button>
      </Link>

      {isLoading ? (
        <div className="h-64 bg-white border rounded-md animate-pulse"></div>
      ) : job ? (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-6 rounded-lg border shadow-sm">
            <div>
              <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2">
                <Activity className="w-6 h-6 text-gray-500" />
                Job Details
              </h2>
              <p className="text-sm font-mono text-muted-foreground mt-2">{job.id}</p>
            </div>
            <StatusBadge className="text-sm" status={job.status} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-white p-6 rounded-lg border shadow-sm flex items-start gap-4">
               <FileVideo className="w-5 h-5 text-gray-400 mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-gray-500">Asset</h3>
                  <Link href={`/assets/${job.assetId}`} className="text-blue-600 hover:underline font-mono text-sm mt-1 inline-block">
                    {job.assetId.substring(0, 8)}...
                  </Link>
               </div>
            </div>

            <div className="bg-white p-6 rounded-lg border shadow-sm flex items-start gap-4">
               <Calendar className="w-5 h-5 text-gray-400 mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-gray-500">Created At</h3>
                  <p className="text-sm font-semibold mt-1">{new Date(job.createdAt).toLocaleString()}</p>
               </div>
            </div>

            <div className="bg-white p-6 rounded-lg border shadow-sm flex items-start gap-4">
               <Clock className="w-5 h-5 text-gray-400 mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-gray-500">Last Updated</h3>
                  <p className="text-sm font-semibold mt-1">{new Date(job.updatedAt).toLocaleString()}</p>
               </div>
            </div>
          </div>
          
          {run && <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-lg border bg-white p-6 shadow-sm">
              <h3 className="font-semibold">Pipeline execution</h3>
              <p className="mt-1 text-sm text-muted-foreground">{run.pipeline.slug} · v{run.pipeline.version}</p>
              <div className="mt-4 space-y-2">
                {run.steps.map((step) => <div key={step.id} className="flex items-center justify-between rounded border p-3 text-sm">
                  <span>{step.stepId} · {step.processor}@{step.version}</span><StatusBadge status={step.status} />
                </div>)}
              </div>
            </section>
            <section className="rounded-lg border bg-white p-6 shadow-sm">
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
