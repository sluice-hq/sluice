'use client';

import { useQuery } from '@tanstack/react-query';
import { getJob } from '@/api/jobs';
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
          
          {/* Pipeline execution logs will go here in the future */}
          <div className="bg-gray-900 rounded-lg p-6 font-mono text-sm text-gray-300 shadow-inner">
            <p className="text-gray-500 mb-4">// Execution Logs</p>
            <p className="text-green-400">{new Date(job.createdAt).toISOString()} - Job created</p>
            {job.status !== 'QUEUED' && (
              <p className="text-green-400">{new Date(job.updatedAt).toISOString()} - Job status changed to {job.status}</p>
            )}
            {(job.status === 'RUNNING' || job.status === 'QUEUED') && (
              <p className="text-yellow-400 animate-pulse mt-2">Waiting for worker...</p>
            )}
          </div>
        </div>
      ) : (
        <EmptyState title="Job Not Found" description="The requested job does not exist." />
      )}
    </div>
  );
}
