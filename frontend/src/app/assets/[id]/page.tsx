'use client';

import { useQuery } from '@tanstack/react-query';
import { getAsset } from '@/api/assets';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { ArrowLeft, FileVideo, HardDrive, Calendar } from 'lucide-react';
import Link from 'next/link';
import { use } from 'react';

export default function AssetDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  
  const { data: asset, isLoading, error } = useQuery({
    queryKey: ['asset', resolvedParams.id],
    queryFn: () => getAsset(resolvedParams.id),
    refetchInterval: (query) => (query.state.data?.uploadStatus === 'PENDING' ? 3000 : false),
  });

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
                  <p className="text-lg font-semibold mt-1 text-white">{(asset.size / 1024 / 1024).toFixed(2)} MB</p>
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
        </div>
      ) : (
        <EmptyState title="Asset Not Found" description="The requested asset does not exist." />
      )}
    </div>
  );
}
