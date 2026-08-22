import { Asset, Job } from '@/api/types';
import { StatusBadge } from './StatusBadge';
import { FileIcon, FileVideo, FileAudio, FileImage, FileText } from 'lucide-react';

export function RecentAssetsTable({ assets }: { assets: Asset[] }) {
  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden flex flex-col h-full w-full">
      <div className="p-6 border-b border-border flex justify-between items-center bg-card">
        <div>
          <h3 className="text-lg font-semibold text-white tracking-tight">Recent Assets</h3>
          <p className="text-sm text-muted-foreground">Latest media uploaded for processing</p>
        </div>
        <button className="text-sm text-primary hover:text-primary/80 font-medium transition-colors">
          View All
        </button>
      </div>
      
      <div className="overflow-x-auto flex-1">
        <table className="w-full text-sm text-left h-full">
          <thead className="text-xs text-muted-foreground uppercase bg-background/50 border-b border-border">
            <tr>
              <th className="px-6 py-4 font-medium">Asset Name</th>
              <th className="px-6 py-4 font-medium">Size</th>
              <th className="px-6 py-4 font-medium">Upload Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50 bg-card">
            {assets.length === 0 ? (
              <tr>
                <td colSpan={3} className="px-6 py-8 text-center text-muted-foreground">
                  No recent assets found
                </td>
              </tr>
            ) : (
              assets.slice(0, 5).map((asset) => (
                <tr key={asset.id} className="hover:bg-white/[0.02] transition-colors group">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded bg-background flex items-center justify-center border border-border group-hover:border-primary/50 transition-colors">
                        <FileTypeIcon contentType={asset.contentType} />
                      </div>
                      <span className="font-medium text-white truncate max-w-[200px]" title={asset.filename}>
                        {asset.filename}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground whitespace-nowrap">
                    {formatBytes(asset.size)}
                  </td>
                  <td className="px-6 py-4 text-muted-foreground whitespace-nowrap">
                    {new Date(asset.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function RecentJobsTable({ jobs }: { jobs: Job[] }) {
  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden flex flex-col h-full w-full">
      <div className="p-6 border-b border-border flex justify-between items-center bg-card">
        <div>
          <h3 className="text-lg font-semibold text-white tracking-tight">Recent Jobs</h3>
          <p className="text-sm text-muted-foreground">Latest pipeline executions</p>
        </div>
        <button className="text-sm text-primary hover:text-primary/80 font-medium transition-colors">
          View All
        </button>
      </div>
      
      <div className="overflow-x-auto flex-1">
        <table className="w-full text-sm text-left h-full">
          <thead className="text-xs text-muted-foreground uppercase bg-background/50 border-b border-border">
            <tr>
              <th className="px-6 py-4 font-medium">Job ID</th>
              <th className="px-6 py-4 font-medium">Status</th>
              <th className="px-6 py-4 font-medium">Started</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50 bg-card">
            {jobs.length === 0 ? (
              <tr>
                <td colSpan={3} className="px-6 py-8 text-center text-muted-foreground">
                  No recent jobs found
                </td>
              </tr>
            ) : (
              jobs.slice(0, 5).map((job) => (
                <tr key={job.id} className="hover:bg-white/[0.02] transition-colors group">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-muted-foreground bg-background px-2 py-1 rounded border border-border group-hover:border-primary/30 transition-colors">
                          {job.id.substring(0, 8)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={job.status} />
                    </td>
                    <td className="px-6 py-4 text-muted-foreground whitespace-nowrap">
                      {new Date(job.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
  );
}

function FileTypeIcon({ contentType }: { contentType: string }) {
  const type = contentType.toLowerCase();
  if (type.includes('video')) return <FileVideo className="w-4 h-4 text-primary" />;
  if (type.includes('audio')) return <FileAudio className="w-4 h-4 text-status-warning" />;
  if (type.includes('image')) return <FileImage className="w-4 h-4 text-status-success" />;
  if (type.includes('text') || type.includes('json')) return <FileText className="w-4 h-4 text-muted-foreground" />;
  return <FileIcon className="w-4 h-4 text-muted-foreground" />;
}

function formatBytes(bytes: number, decimals = 2) {
  if (!+bytes) return '0 Bytes';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}
