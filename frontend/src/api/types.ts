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
