'use client';

import { useQuery } from '@tanstack/react-query';
import { getAsset } from '@/api/assets';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button, buttonVariants } from '@/components/ui/button';
import { ArrowLeft, FileVideo, HardDrive, Calendar, Download, ImageIcon, GitBranch, Activity } from 'lucide-react';
import Link from 'next/link';
import { use } from 'react';
import { formatBytes, formatFact } from '@/lib/utils';

export default function AssetDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  
  const { data: asset, isLoading, error } = useQuery({
    queryKey: ['asset', resolvedParams.id],
    queryFn: () => getAsset(resolvedParams.id),
    refetchInterval: (query) => (query.state.data?.uploadStatus === 'PENDING' ? 3000 : false),
  });
  const isCompleted = asset?.uploadStatus === 'COMPLETED';

  if (error) {
    return (
      <div className="space-y-6">
        <Link href="/assets">
          <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Assets
          </Button>
        </Link>
        <EmptyState title="Error Loading Asset" description={error.message} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link href="/assets">
        <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Assets
        </Button>
      </Link>

      {isLoading ? (
        <div className="h-64 bg-card border border-border rounded-xl shadow-sm animate-pulse"></div>
      ) : asset ? (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-card p-6 rounded-xl border border-border shadow-sm">
            <div>
              <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2 text-white">
                <FileVideo className="w-6 h-6 text-muted-foreground" />
                {asset.filename}
              </h2>
              <p className="text-sm font-mono text-muted-foreground mt-2">{asset.id}</p>
            </div>
            <StatusBadge className="text-sm" status={asset.uploadStatus} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-card p-6 rounded-xl border border-border shadow-sm flex items-start gap-4">
               <HardDrive className="w-5 h-5 text-muted-foreground mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">File Size</h3>
                  <p className="text-lg font-semibold mt-1 text-white">{formatBytes(asset.size)}</p>
               </div>
            </div>
            
            <div className="bg-card p-6 rounded-xl border border-border shadow-sm flex items-start gap-4">
               <FileVideo className="w-5 h-5 text-muted-foreground mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">Content Type</h3>
                  <p className="text-lg font-semibold mt-1 text-white">{asset.contentType}</p>
               </div>
            </div>

            <div className="bg-card p-6 rounded-xl border border-border shadow-sm flex items-start gap-4">
               <Calendar className="w-5 h-5 text-muted-foreground mt-0.5" />
               <div>
                  <h3 className="text-sm font-medium text-muted-foreground">Uploaded At</h3>
                  <p className="text-lg font-semibold mt-1 text-white">{new Date(asset.createdAt).toLocaleString()}</p>
               </div>
            </div>
          </div>

          {isCompleted && asset.contentType.toLowerCase().startsWith('image/') && (
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="asset-preview-heading">
              <div className="flex items-center gap-2">
                <ImageIcon className="size-5 text-primary" />
                <h3 id="asset-preview-heading" className="font-semibold">Image preview</h3>
              </div>
              {/* This route keeps storage URLs and short-lived links on the server. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                className="mt-4 max-h-[32rem] w-auto max-w-full rounded-lg border border-border bg-black/20 object-contain"
                src={`/api/downloads/assets/${asset.id}?inline=1`}
                alt={`Preview of ${asset.filename}`}
              />
            </section>
          )}

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="asset-actions-heading">
              <h3 id="asset-actions-heading" className="font-semibold">Safe actions</h3>
              {isCompleted ? <>
                <p className="mt-1 text-sm text-muted-foreground">Downloads are streamed through the authenticated dashboard; the storage location is never shown.</p>
                <a className={`${buttonVariants({ size: 'sm' })} mt-4`} href={`/api/downloads/assets/${asset.id}`}>
                  <Download className="mr-2 size-4" />Download {asset.filename}
                </a>
              </> : <p className="mt-1 text-sm text-muted-foreground">This asset is pending upload verification. Preview and download actions will be available after it is completed.</p>}
            </section>

            <section className="rounded-xl border border-border bg-card p-6 shadow-sm" aria-labelledby="asset-lineage-heading">
              <div className="flex items-center gap-2">
                <GitBranch className="size-5 text-primary" />
                <h3 id="asset-lineage-heading" className="font-semibold">Lineage</h3>
              </div>
              <dl className="mt-4 space-y-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">Parent asset</dt>
                  <dd className="mt-1">{asset.parentAssetId ? <Link className="font-mono text-primary hover:underline" href={`/assets/${asset.parentAssetId}`}>{asset.parentAssetId}</Link> : 'Not available'}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Producing run</dt>
                  <dd className="mt-1">{asset.producingJobId ? <Link className="inline-flex items-center gap-1 font-mono text-primary hover:underline" href={`/jobs/${asset.producingJobId}`}><Activity className="size-3.5" />{asset.producingJobId}</Link> : 'Not available'}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Upload state</dt>
                  <dd className="mt-1">{formatFact(asset.uploadStatus)}</dd>
                </div>
              </dl>
            </section>
          </div>
        </div>
      ) : (
        <EmptyState title="Asset Not Found" description="The requested asset does not exist." />
      )}
    </div>
  );
}
