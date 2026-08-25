'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getAssets } from '@/api/assets';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { Upload, FileVideo } from 'lucide-react';
import Link from 'next/link';

export default function AssetsPage() {
  const [page, setPage] = useState(0);
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['assets', page, 20],
    queryFn: () => getAssets(page, 20),
    refetchInterval: 5000,
  });

  if (error) {
    return <EmptyState title="Error Loading Assets" description={error.message} />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2">
            <FileVideo className="w-6 h-6 text-muted-foreground" />
            Assets
          </h2>
          <p className="text-muted-foreground mt-1">Manage and monitor all uploaded media assets.</p>
        </div>
        <Link href="/assets/upload">
          <Button className="w-full sm:w-auto">
            <Upload className="w-4 h-4 mr-2" />
            Test a pipeline
          </Button>
        </Link>
      </div>

      {isLoading ? (
        <TableSkeleton />
      ) : !pageData?.content?.length ? (
        <EmptyState 
          title="No assets found" 
          description="You haven't uploaded any assets yet." 
          action={
            <Link href="/assets/upload">
              <Button variant="outline">Upload your first asset</Button>
            </Link>
          }
        />
      ) : (
        <div className="border border-white/5 rounded-xl bg-card/20 backdrop-blur-md overflow-hidden shadow-lg shadow-black/20">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-white/5 border-b border-white/5">
              <tr>
                <th className="px-6 py-4 font-medium tracking-wider">Filename</th>
                <th className="px-6 py-4 font-medium tracking-wider">Size</th>
                <th className="px-6 py-4 font-medium tracking-wider">Type</th>
                <th className="px-6 py-4 font-medium tracking-wider">Uploaded</th>
                <th className="px-6 py-4 font-medium tracking-wider text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {pageData.content.map((asset) => (
                <tr key={asset.id} className="hover:bg-white/5 transition-colors group">
                  <td className="px-6 py-4 font-medium text-primary group-hover:text-primary-foreground transition-colors">
                    <Link href={`/assets/${asset.id}`}>{asset.filename}</Link>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{(asset.size / 1024 / 1024).toFixed(2)} MB</td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{asset.contentType}</td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{new Date(asset.createdAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <StatusBadge status={asset.uploadStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-6 py-4 border-t border-white/5 bg-black/20 text-xs text-muted-foreground flex justify-between items-center">
            <span>Showing {pageData.numberOfElements} of {pageData.totalElements} assets</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={pageData.first} onClick={() => setPage((current) => current - 1)} className="border-white/10 bg-white/5 text-muted-foreground">Previous</Button>
              <Button variant="outline" size="sm" disabled={pageData.last} onClick={() => setPage((current) => current + 1)} className="border-white/10 bg-white/5 text-muted-foreground">Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
