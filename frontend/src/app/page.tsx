'use client';

import { useQuery } from '@tanstack/react-query';
import { getDashboardOverview } from '@/api/dashboard';
import { MetricCard } from '@/components/domain/MetricCard';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { MetricsSkeleton, TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { FileVideo, Activity, Loader2, CheckCircle2, XCircle } from 'lucide-react';
import Link from 'next/link';

export default function OverviewPage() {
  const { data: dashboard, isLoading, error } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboardOverview,
    refetchInterval: 5000, // Refetch every 5 seconds for recent activity
  });

  if (error) {
    return <EmptyState title="Error Loading Dashboard" description={error.message} />;
  }

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Overview</h2>
        <p className="text-muted-foreground">High-level platform metrics and recent activity.</p>
      </div>

      {isLoading ? (
        <MetricsSkeleton />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
          <MetricCard title="Total Assets" value={dashboard?.totalAssets || 0} icon={FileVideo} />
          <MetricCard title="Total Jobs" value={dashboard?.totalJobs || 0} icon={Activity} />
          <MetricCard title="Running Jobs" value={dashboard?.runningJobs || 0} icon={Loader2} />
          <MetricCard title="Completed Jobs" value={dashboard?.completedJobs || 0} icon={CheckCircle2} />
          <MetricCard title="Failed Jobs" value={dashboard?.failedJobs || 0} icon={XCircle} />
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-4">
          <h3 className="text-lg font-medium">Recent Assets</h3>
          {isLoading ? (
            <TableSkeleton />
          ) : !dashboard?.recentAssets?.length ? (
            <EmptyState title="No assets" description="Upload an asset to get started." />
          ) : (
            <div className="border rounded-md bg-white">
              <table className="w-full text-sm text-left">
                <thead className="text-xs text-gray-500 uppercase bg-gray-50 border-b">
                  <tr>
                    <th className="px-4 py-3 font-medium">Filename</th>
                    <th className="px-4 py-3 font-medium">Size</th>
                    <th className="px-4 py-3 font-medium text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {dashboard.recentAssets.map((asset) => (
                    <tr key={asset.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-blue-600">
                        <Link href={`/assets/${asset.id}`}>{asset.filename}</Link>
                      </td>
                      <td className="px-4 py-3 text-gray-500">{(asset.size / 1024 / 1024).toFixed(2)} MB</td>
                      <td className="px-4 py-3 text-right">
                        <StatusBadge status={asset.uploadStatus} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="space-y-4">
          <h3 className="text-lg font-medium">Recent Jobs</h3>
          {isLoading ? (
            <TableSkeleton />
          ) : !dashboard?.recentJobs?.length ? (
            <EmptyState title="No jobs" description="Jobs will appear here once an asset is uploaded." />
          ) : (
            <div className="border rounded-md bg-white">
              <table className="w-full text-sm text-left">
                <thead className="text-xs text-gray-500 uppercase bg-gray-50 border-b">
                  <tr>
                    <th className="px-4 py-3 font-medium">Job ID</th>
                    <th className="px-4 py-3 font-medium">Created At</th>
                    <th className="px-4 py-3 font-medium text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {dashboard.recentJobs.map((job) => (
                    <tr key={job.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-blue-600">
                        <Link href={`/jobs/${job.id}`}>{job.id.substring(0, 8)}</Link>
                      </td>
                      <td className="px-4 py-3 text-gray-500">{new Date(job.createdAt).toLocaleString()}</td>
                      <td className="px-4 py-3 text-right">
                        <StatusBadge status={job.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
