'use client';

import { useQuery } from '@tanstack/react-query';
import { getDashboardOverview } from '@/api/dashboard';
import { mapDashboardOverview } from '@/lib/dashboard-mapper';
import { MetricsSkeleton, TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { TrendMetricCard } from '@/components/domain/TrendMetricCard';
import { RecentAssetsTable, RecentJobsTable } from '@/components/domain/DashboardTables';
import { SystemHealth } from '@/components/domain/SystemHealth';
import { OnboardingChecklist } from '@/components/domain/OnboardingChecklist';

export default function OverviewPage() {
  const { data: rawDashboard, isLoading, error } = useQuery({ queryKey: ['dashboard'], queryFn: getDashboardOverview, refetchInterval: 5000 });
  const dashboard = rawDashboard ? mapDashboardOverview(rawDashboard) : null;
  if (error) return <div className="p-8"><EmptyState title="Failed to load dashboard" description="There was an error connecting to the server. Please try again later." /></div>;
  return <div className="mx-auto flex h-full max-w-[1600px] flex-col space-y-8 p-8"><div><h1 className="text-2xl font-bold tracking-tight">Platform Overview</h1><p className="mt-1 text-sm text-muted-foreground">Real-time media processing pipeline metrics and health.</p></div><OnboardingChecklist />{isLoading || !dashboard ? <div className="space-y-8"><MetricsSkeleton /><div className="grid grid-cols-1 gap-8 lg:grid-cols-3"><div className="lg:col-span-2"><TableSkeleton /></div><TableSkeleton /></div></div> : <div className="flex flex-col gap-8 pb-12"><div className="grid grid-cols-2 gap-6 lg:grid-cols-5"><TrendMetricCard metric={dashboard.kpis.assets} /><TrendMetricCard metric={dashboard.kpis.jobsRunning} /><TrendMetricCard metric={dashboard.kpis.successRate} /><TrendMetricCard metric={dashboard.kpis.queueDepth} /><TrendMetricCard metric={dashboard.kpis.failedToday} /></div><div className="grid grid-cols-1 gap-8 xl:grid-cols-3"><RecentAssetsTable assets={dashboard.recentAssets} /><RecentJobsTable jobs={dashboard.recentJobs} /><SystemHealth services={dashboard.systemHealth} /></div></div>}</div>;
}
