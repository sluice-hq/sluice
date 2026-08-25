'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getJobs } from '@/api/jobs';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { Activity } from 'lucide-react';
import Link from 'next/link';

export default function JobsPage() {
  const [page, setPage] = useState(0);
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['jobs', page, 20],
    queryFn: () => getJobs(page, 20),
    refetchInterval: 5000,
  });

  if (error) {
    return <EmptyState title="Error Loading Jobs" description={error.message} />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2">
          <Activity className="w-6 h-6 text-gray-500" />
          Runs
        </h2>
        <p className="text-muted-foreground mt-1">Inspect durable pipeline executions, step results, and outputs.</p>
      </div>

      {isLoading ? (
        <TableSkeleton />
      ) : !pageData?.content?.length ? (
        <EmptyState 
          title="No runs found"
          description="Runs are created by the developer API or the pipeline test console."
          action={
            <Link href="/assets/upload">
              <Button variant="outline">Upload an asset</Button>
            </Link>
          }
        />
      ) : (
        <div className="border border-white/5 rounded-xl bg-card/20 backdrop-blur-md overflow-hidden shadow-lg shadow-black/20">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-white/5 border-b border-white/5">
              <tr>
                <th className="px-6 py-4 font-medium tracking-wider">Run ID</th>
                <th className="px-6 py-4 font-medium tracking-wider">Asset ID</th>
                <th className="px-6 py-4 font-medium tracking-wider">Created At</th>
                <th className="px-6 py-4 font-medium tracking-wider">Updated At</th>
                <th className="px-6 py-4 font-medium tracking-wider text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {pageData.content.map((job) => (
                <tr key={job.id} className="hover:bg-white/5 transition-colors group">
                  <td className="px-6 py-4 font-medium text-primary font-mono text-xs group-hover:text-primary-foreground transition-colors">
                    <Link href={`/jobs/${job.id}`}>{job.id}</Link>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground font-mono text-xs group-hover:text-foreground/80 transition-colors">
                    <Link href={`/assets/${job.assetId}`} className="hover:underline hover:text-primary transition-colors">{job.assetId}</Link>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{new Date(job.createdAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{new Date(job.updatedAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <StatusBadge status={job.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-6 py-4 border-t border-white/5 bg-black/20 text-xs text-muted-foreground flex justify-between items-center">
            <span>Showing {pageData.numberOfElements} of {pageData.totalElements} runs</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={pageData.first} onClick={() => setPage((current) => current - 1)} className="border-white/10 bg-white/5 text-muted-foreground">Previous</Button>
              <Button variant="outline" size="sm" disabled={pageData.last} onClick={() => setPage((current) => current + 1)} className="border-white/10 bg-white/5 text-muted-foreground">Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
