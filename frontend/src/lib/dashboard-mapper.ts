import { Asset, DashboardOverview, Job } from '@/api/types';

// --- View Models ---

export interface TrendMetric {
  title: string;
  value: string;
  description: string;
}

export interface PlatformService {
  name: string;
  latencyMs: number;
  status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
}

export interface DashboardOverviewViewModel {
  kpis: {
    assets: TrendMetric;
    jobsRunning: TrendMetric;
    successRate: TrendMetric;
    queueDepth: TrendMetric;
    failedToday: TrendMetric;
  };
  systemHealth: PlatformService[];
  recentAssets: Asset[];
  recentJobs: Job[];
}

// --- Mapper ---

export function mapDashboardOverview(dto: DashboardOverview): DashboardOverviewViewModel {
  return {
    kpis: {
      assets: {
        title: 'Assets',
        value: dto.totalAssets?.toLocaleString() || '0',
        description: 'Stored in this project',
      },
      jobsRunning: {
        title: 'Jobs Running',
        value: dto.runningJobs?.toString() || '0',
        description: 'Currently processing',
      },
      successRate: {
        title: 'Success Rate',
        value: `${successRate(dto.completedJobs, dto.failedJobs)}%`,
        description: 'Across terminal runs',
      },
      queueDepth: {
        title: 'Queue Depth',
        value: dto.queuedJobs?.toString() || '0',
        description: 'Queued or waiting to retry',
      },
      failedToday: {
        title: 'Failed Runs',
        value: dto.failedJobs?.toString() || '0',
        description: 'Across this project',
      }
    },
    systemHealth: dto.systemHealth || [],
    recentAssets: dto.recentAssets || [],
    recentJobs: dto.recentJobs || [],
  };
}

function successRate(completed: number, failed: number): number {
  const terminal = completed + failed;
  return terminal === 0 ? 0 : Math.round((completed / terminal) * 100);
}
