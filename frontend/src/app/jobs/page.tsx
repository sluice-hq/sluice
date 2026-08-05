'use client';

import { useQuery } from '@tanstack/react-query';
import { getJobs } from '@/api/jobs';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { Activity } from 'lucide-react';
import Link from 'next/link';

export default function JobsPage() {
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['jobs', 0, 20],
    queryFn: () => getJobs(0, 20),
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
          Jobs
        </h2>
        <p className="text-muted-foreground mt-1">Monitor background processing tasks across the platform.</p>
      </div>

      {isLoading ? (
        <TableSkeleton />
      ) : !pageData?.content?.length ? (
        <EmptyState 
          title="No jobs found" 
          description="Jobs are created automatically when you upload an asset." 
          action={
            <Link href="/assets/upload">
              <Button variant="outline">Upload an asset</Button>
            </Link>
          }
        />
      ) : (
        <div className="border rounded-md bg-white overflow-hidden shadow-sm">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-500 uppercase bg-gray-50/50 border-b">
              <tr>
                <th className="px-6 py-4 font-medium">Job ID</th>
                <th className="px-6 py-4 font-medium">Asset ID</th>
                <th className="px-6 py-4 font-medium">Created At</th>
                <th className="px-6 py-4 font-medium">Updated At</th>
                <th className="px-6 py-4 font-medium text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {pageData.content.map((job) => (
                <tr key={job.id} className="hover:bg-gray-50/80 transition-colors">
                  <td className="px-6 py-4 font-medium text-blue-600 font-mono text-xs">
                    <Link href={`/jobs/${job.id}`}>{job.id}</Link>
                  </td>
                  <td className="px-6 py-4 text-gray-500 font-mono text-xs">
                    <Link href={`/assets/${job.assetId}`} className="hover:underline">{job.assetId}</Link>
                  </td>
                  <td className="px-6 py-4 text-gray-500">{new Date(job.createdAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-gray-500">{new Date(job.updatedAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <StatusBadge status={job.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-6 py-4 border-t bg-gray-50/50 text-xs text-gray-500 flex justify-between items-center">
            <span>Showing {pageData.numberOfElements} of {pageData.totalElements} jobs</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled>Previous</Button>
              <Button variant="outline" size="sm" disabled>Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
