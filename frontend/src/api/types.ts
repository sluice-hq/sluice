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
  uploadStatus: 'PENDING' | 'COMPLETED';
  createdAt: string;
  parentAssetId: string | null;
  producingJobId: string | null;
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
  systemHealth: {
    name: string;
    latencyMs: number;
    status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  }[];
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

export interface UploadAssetResponse {
  assetId: string;
  filename: string;
  size: number;
  contentType: string;
  assetCreatedAt: string;
  jobId: string;
  jobStatus: Job['status'];
  jobCreatedAt: string;
}

export interface PublishedPipeline {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  versionId: string;
  versionNumber: number;
  expectedInputMimeType: string;
  inputContract: {
    kind: string;
    mimeTypes: string[];
    maxBytes: number;
    maxPixels: number;
    alphaSupported: boolean;
    animationSupported: boolean;
  };
  uploadConstraints: {
    maxBytes: number;
    allowedContentTypes: string[];
  };
  contractUsable: boolean;
  contractIssue: string | null;
}

export interface DownloadUrlResponse { downloadUrl: string }

export interface RunDetails {
  id: string;
  status: string;
  pipeline: { slug: string; version: number | null } | null;
  inputAssetId: string;
  steps: Array<{
    id: string;
    stepId: string;
    processor: string;
    version: string;
    status: string;
    createdAt: string;
    updatedAt: string;
    attempt: number | null;
    startedAt: string | null;
    completedAt: string | null;
    durationMs: number | null;
    inputBytes: number | null;
    outputBytes: number | null;
    inputMimeType: string | null;
    outputMimeType: string | null;
    metadata: unknown;
    outputAssetId: string | null;
    error: { code: string; message: string } | null;
  }>;
  outputs: Asset[];
  metrics: { queueWaitMs: number | null; processingMs: number | null; inputBytes: number | null; outputBytes: number | null; bytesSaved: number | null; compressionRatio: number | null };
  error: { code: string; message: string } | null;
  attempts: Array<{
    attempt: number;
    status: string;
    startedAt: string | null;
    completedAt: string | null;
    error: { code: string; message: string } | null;
    transientFailure: boolean | null;
  }>;
  governance: { decision: 'ALLOW' | 'REVIEW' | 'BLOCK'; policyVersion: string | null; provider: string | null; modelVersion: string | null; categoryScores: unknown; reasonCodes: unknown } | null;
  createdAt: string;
  updatedAt: string;
}
