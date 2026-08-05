'use client';

import { useQuery } from '@tanstack/react-query';
import { getAssets } from '@/api/assets';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { Upload, FileVideo } from 'lucide-react';
import Link from 'next/link';

export default function AssetsPage() {
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['assets', 0, 20],
    queryFn: () => getAssets(0, 20),
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
            <FileVideo className="w-6 h-6 text-gray-500" />
            Assets
          </h2>
          <p className="text-muted-foreground mt-1">Manage and monitor all uploaded media assets.</p>
        </div>
        <Link href="/assets/upload">
          <Button className="w-full sm:w-auto">
            <Upload className="w-4 h-4 mr-2" />
            Upload Asset
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
        <div className="border rounded-md bg-white overflow-hidden shadow-sm">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-500 uppercase bg-gray-50/50 border-b">
              <tr>
                <th className="px-6 py-4 font-medium">Filename</th>
                <th className="px-6 py-4 font-medium">Size</th>
                <th className="px-6 py-4 font-medium">Type</th>
                <th className="px-6 py-4 font-medium">Uploaded</th>
                <th className="px-6 py-4 font-medium text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {pageData.content.map((asset) => (
                <tr key={asset.id} className="hover:bg-gray-50/80 transition-colors">
                  <td className="px-6 py-4 font-medium text-blue-600">
                    <Link href={`/assets/${asset.id}`}>{asset.filename}</Link>
                  </td>
                  <td className="px-6 py-4 text-gray-500">{(asset.size / 1024 / 1024).toFixed(2)} MB</td>
                  <td className="px-6 py-4 text-gray-500">{asset.contentType}</td>
                  <td className="px-6 py-4 text-gray-500">{new Date(asset.createdAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <StatusBadge status={asset.uploadStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-6 py-4 border-t bg-gray-50/50 text-xs text-gray-500 flex justify-between items-center">
            <span>Showing {pageData.numberOfElements} of {pageData.totalElements} assets</span>
            {/* Pagination placeholder */}
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled>Previous</Button>
              <Button variant="outline" size="sm" disabled>Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
