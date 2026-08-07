export interface Page<T> {
  content: T[];
  pageable: any;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  sort: any;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}

export interface Asset {
  id: string;
  filename: string;
  size: number;
  contentType: string;
  storageUrl: string;
  uploadStatus: 'PENDING' | 'COMPLETED';
  createdAt: string;
}

export interface Job {
  id: string;
  assetId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

export interface DashboardOverview {
  totalAssets: number;
  totalJobs: number;
  runningJobs: number;
  completedJobs: number;
  failedJobs: number;
  recentAssets: Asset[];
  recentJobs: Job[];
  metrics?: {
    assetsSparkline: number[];
    jobsSparkline: number[];
    successRateSparkline: number[];
    queueDepthSparkline: number[];
    failedSparkline: number[];
    throughput: {
      timestamp: string;
      completed: number;
      running: number;
      failed: number;
    }[];
    pipelineActivity: {
      name: string;
      assetsProcessed: number;
      status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
    }[];
    systemHealth: {
      name: string;
      latencyMs: number;
      status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
    }[];
  };
}

export interface UploadUrlRequest {
  filename: string;
  contentType: string;
  size: number;
}

export interface UploadUrlResponse {
  assetId: string;
  uploadUrl: string;
  blobName: string;
}
