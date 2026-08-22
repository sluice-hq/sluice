import { Asset, DashboardOverview, Job } from '@/api/types';

// --- View Models ---

export interface TrendMetric {
  title: string;
  value: string;
  trend: string;
  trendUp: boolean;
  sparklineData: number[];
}

export interface ThroughputData {
  timestamp: string;
  completed: number;
  running: number;
  failed: number;
}

export interface PipelineStage {
  name: string;
  assetsProcessed: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
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
  throughput: ThroughputData[];
  pipelineActivity: PipelineStage[];
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
        trend: '--',
        trendUp: true,
        sparklineData: dto.metrics?.assetsSparkline || [],
      },
      jobsRunning: {
        title: 'Jobs Running',
        value: dto.runningJobs?.toString() || '0',
        trend: '--',
        trendUp: true,
        sparklineData: dto.metrics?.jobsSparkline || [],
      },
      successRate: {
        title: 'Success Rate',
        value: '--', // Not implemented on backend yet
        trend: '--',
        trendUp: true,
        sparklineData: dto.metrics?.successRateSparkline || [],
      },
      queueDepth: {
        title: 'Queue Depth',
        value: '--', // Not implemented on backend yet
        trend: '--',
        trendUp: false,
        sparklineData: dto.metrics?.queueDepthSparkline || [],
      },
      failedToday: {
        title: 'Failed Today',
        value: dto.failedJobs?.toString() || '0',
        trend: '--',
        trendUp: false,
        sparklineData: dto.metrics?.failedSparkline || [],
      }
    },
    throughput: dto.metrics?.throughput || [],
    pipelineActivity: dto.metrics?.pipelineActivity || [],
    systemHealth: dto.metrics?.systemHealth || [],
    recentAssets: dto.recentAssets || [],
    recentJobs: dto.recentJobs || [],
  };
}
