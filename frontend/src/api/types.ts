export interface Page<T> {
  content: T[];
  pageable: unknown;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  sort: unknown;
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
  status: 'QUEUED' | 'RUNNING' | 'RETRY_WAIT' | 'REVIEW_REQUIRED' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

export interface DashboardOverview {
  totalAssets: number;
  totalJobs: number;
  runningJobs: number;
  queuedJobs: number;
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

export interface PublishedPipeline {
  id: string;
  name: string;
  description: string | null;
  versionId: string;
  versionNumber: number;
  expectedInputMimeType: string;
}

export interface RunDetails {
  id: string;
  status: string;
  pipeline: { slug: string; version: number };
  inputAssetId: string;
  steps: Array<{
    id: string;
    stepId: string;
    processor: string;
    version: string;
    status: string;
    durationMs: number | null;
    inputBytes: number | null;
    outputBytes: number | null;
    error: { code: string; message: string } | null;
  }>;
  outputs: Asset[];
  metrics: { queueWaitMs: number | null; processingMs: number | null; inputBytes: number | null; outputBytes: number | null; bytesSaved: number | null; compressionRatio: number | null };
  governance: { decision: 'ALLOW' | 'REVIEW' | 'BLOCK'; policyVersion: string; provider: string; modelVersion: string; categoryScores: unknown; reasonCodes: unknown } | null;
}
