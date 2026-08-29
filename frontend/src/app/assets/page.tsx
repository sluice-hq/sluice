'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { getAssets, type AssetFilters } from '@/api/assets';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Upload, FileVideo, Search, X } from 'lucide-react';
import Link from 'next/link';

const pageSize = 20;

export default function AssetsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [filterError, setFilterError] = useState('');
  const requestedPage = Number(searchParams.get('page') ?? '0');
  const page = Number.isSafeInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
  const filterQuery = searchParams.toString();
  const filters: AssetFilters = {
    filename: searchParams.get('q') ?? '',
    status: searchParams.get('status') ?? '',
    mediaType: searchParams.get('type') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
    externalSubjectId: searchParams.get('externalSubjectId') ?? '',
    externalReference: searchParams.get('externalReference') ?? '',
  };
  const hasFilters = Object.values(filters).some(Boolean);
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['assets', page, pageSize, filterQuery],
    queryFn: () => getAssets(page, pageSize, filters),
    refetchInterval: (query) => query.state.data?.content.some((asset) => asset.uploadStatus === 'PENDING') ? 3000 : false,
  });

  const navigate = (params: URLSearchParams) => {
    const query = params.toString();
    router.push(query ? `/assets?${query}` : '/assets');
  };

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const from = String(form.get('from') ?? '');
    const to = String(form.get('to') ?? '');
    if (from && to && from > to) {
      setFilterError('From date must not be after To date.');
      return;
    }
    setFilterError('');
    const params = new URLSearchParams();
    const mappings = [
      ['q', 'q'], ['status', 'status'], ['type', 'type'], ['from', 'from'], ['to', 'to'],
      ['externalSubjectId', 'externalSubjectId'], ['externalReference', 'externalReference'],
    ];
    mappings.forEach(([formName, parameter]) => {
      const value = String(form.get(formName) ?? '').trim();
      if (value) params.set(parameter, value);
    });
    navigate(params);
  };

  const resetFilters = () => { setFilterError(''); router.push('/assets'); };
  const removeFilter = (name: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete(name);
    params.delete('page');
    navigate(params);
  };
  const goToPage = (nextPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('page', String(nextPage));
    navigate(params);
  };
  const activeFilters = [
    filters.filename ? ['q', `Filename: ${filters.filename}`] : null,
    filters.status ? ['status', `Status: ${filters.status}`] : null,
    filters.mediaType ? ['type', `Type: ${filters.mediaType}`] : null,
    filters.from ? ['from', `From: ${filters.from}`] : null,
    filters.to ? ['to', `To: ${filters.to}`] : null,
    filters.externalSubjectId ? ['externalSubjectId', `Subject: ${filters.externalSubjectId}`] : null,
    filters.externalReference ? ['externalReference', `Reference: ${filters.externalReference}`] : null,
  ].filter((entry): entry is string[] => entry !== null);

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

      <form key={filterQuery} onSubmit={applyFilters} className="space-y-4 rounded-xl border border-border bg-card/60 p-4" aria-label="Filter assets">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            Filename
            <Input name="q" defaultValue={filters.filename} placeholder="Search filenames" maxLength={255} />
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            Status
            <select name="status" defaultValue={filters.status} className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm">
              <option value="">Any status</option>
              <option value="PENDING">Pending</option>
              <option value="COMPLETED">Completed</option>
            </select>
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            Media family
            <select name="type" defaultValue={filters.mediaType} className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm">
              <option value="">Any media</option>
              <option value="image">Images</option>
              <option value="video">Videos</option>
              <option value="application">Documents</option>
            </select>
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            External subject ID
            <Input name="externalSubjectId" defaultValue={filters.externalSubjectId} placeholder="user_123" maxLength={128} />
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            External reference
            <Input name="externalReference" defaultValue={filters.externalReference} placeholder="avatar_2026_08" maxLength={255} />
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            Created from
            <Input type="date" name="from" defaultValue={filters.from} />
          </label>
          <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
            Created through
            <Input type="date" name="to" defaultValue={filters.to} />
          </label>
          <div className="flex items-end gap-2">
            <Button type="submit" size="sm"><Search className="mr-2 size-4" />Apply filters</Button>
            <Button type="button" size="sm" variant="outline" onClick={resetFilters} disabled={!hasFilters}>Reset</Button>
          </div>
        </div>
        {filterError ? <p role="alert" className="text-sm text-destructive">{filterError}</p> : null}
        {activeFilters.length ? (
          <div className="flex flex-wrap items-center gap-2" aria-label="Applied asset filters">
            <span className="text-xs text-muted-foreground">Applied:</span>
            {activeFilters.map(([name, label]) => (
              <button key={name} type="button" onClick={() => removeFilter(name)} className="inline-flex cursor-pointer items-center gap-1 rounded-full border border-primary/20 bg-primary/10 px-2.5 py-1 text-xs text-primary transition-colors hover:bg-primary/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`Remove ${label}`}>
                {label}<X className="size-3" />
              </button>
            ))}
          </div>
        ) : null}
      </form>

      {error ? (
        <EmptyState title="Error loading assets" description={error.message} action={<Button variant="outline" onClick={resetFilters}>Reset filters</Button>} />
      ) : isLoading ? (
        <TableSkeleton />
      ) : !pageData?.content?.length ? (
        <EmptyState
          title={hasFilters ? 'No assets match these filters' : 'No assets found'}
          description={hasFilters ? 'Try removing a filter or broadening your search.' : 'Assets appear here after an API upload or pipeline test.'}
          action={
            hasFilters
              ? <Button variant="outline" onClick={resetFilters}>Reset filters</Button>
              : <Link href="/assets/upload"><Button variant="outline">Test your first pipeline</Button></Link>
          }
        />
      ) : (
        <div className="border border-white/5 rounded-xl bg-card/20 backdrop-blur-md overflow-hidden shadow-lg shadow-black/20">
          <div className="overflow-x-auto">
          <table className="w-full min-w-[980px] text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-white/5 border-b border-white/5">
              <tr>
                <th className="px-6 py-4 font-medium tracking-wider">Filename</th>
                <th className="px-6 py-4 font-medium tracking-wider">Size</th>
                <th className="px-6 py-4 font-medium tracking-wider">Type</th>
                <th className="px-6 py-4 font-medium tracking-wider">Application reference</th>
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
                  <td className="px-6 py-4 text-muted-foreground">
                    {asset.externalSubjectId || asset.externalReference ? <><p className="font-mono text-xs">{asset.externalSubjectId ?? 'No subject'}</p><p className="mt-1 font-mono text-xs text-muted-foreground/70">{asset.externalReference ?? 'No reference'}</p></> : 'Not provided'}
                  </td>
                  <td className="px-6 py-4 text-muted-foreground group-hover:text-foreground/80 transition-colors">{new Date(asset.createdAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <StatusBadge status={asset.uploadStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <div className="px-6 py-4 border-t border-white/5 bg-black/20 text-xs text-muted-foreground flex justify-between items-center">
            <span>Showing {page * pageSize + 1}-{page * pageSize + pageData.numberOfElements} of {pageData.totalElements} assets</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={pageData.first} onClick={() => goToPage(page - 1)} className="border-white/10 bg-white/5 text-muted-foreground">Previous</Button>
              <Button variant="outline" size="sm" disabled={pageData.last} onClick={() => goToPage(page + 1)} className="border-white/10 bg-white/5 text-muted-foreground">Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
