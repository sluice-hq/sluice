'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { getRuns } from '@/api/runs';
import type { RunDetails } from '@/api/types';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { EmptyState } from '@/components/domain/EmptyState';
import { Button } from '@/components/ui/button';
import { formatBytes, formatDuration } from '@/lib/utils';
import { Activity } from 'lucide-react';
import Link from 'next/link';
import type { FormEvent } from 'react';

const terminalStatuses = new Set(['COMPLETED', 'FAILED', 'REVIEW_REQUIRED']);

function toDateTimeLocalValue(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const part = (number: number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(date.getMinutes())}`;
}

function terminalSummary(run: RunDetails): string {
  if (run.error) return `${run.error.code}: ${run.error.message}`;
  const facts = [
    run.metrics.processingMs == null ? null : formatDuration(run.metrics.processingMs),
    run.metrics.bytesSaved == null ? null : `Saved ${formatBytes(run.metrics.bytesSaved)}`,
    run.metrics.compressionRatio == null ? null : `Output ${(run.metrics.compressionRatio * 100).toFixed(1)}% of input`,
  ].filter((fact): fact is string => fact != null);
  return facts.length ? facts.join(' · ') : terminalStatuses.has(run.status) ? 'Not available' : 'In progress';
}

export default function JobsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const requestedPage = Number(searchParams.get('page') ?? '0');
  const page = Number.isSafeInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
  const filterQuery = searchParams.toString();
  const filters = { status: searchParams.get('status') ?? '', pipeline: searchParams.get('pipeline') ?? '', from: searchParams.get('from') ?? '', to: searchParams.get('to') ?? '' };
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['runs', page, 20, filters],
    queryFn: () => getRuns(page, 20, filters),
    refetchInterval: (query) => query.state.data?.content.some((run) => !terminalStatuses.has(run.status)) ? 2000 : false,
  });

  if (error) {
    return <EmptyState title="Error loading runs" description={error.message} />;
  }

  const updateFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = new FormData(event.currentTarget); const params = new URLSearchParams();
    const values = ['status', 'pipeline', 'from', 'to']; values.forEach((key) => { const value = String(form.get(key) ?? '').trim(); if (value) params.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value); });
    const query = params.toString();
    router.push(query ? `/jobs?${query}` : '/jobs');
  };
  const resetFilters = () => router.push('/jobs');
  const goToPage = (nextPage: number) => { const params = new URLSearchParams(searchParams.toString()); params.set('page', String(nextPage)); router.push(`/jobs?${params.toString()}`); };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight flex items-center gap-2">
          <Activity className="w-6 h-6 text-gray-500" />
          Runs
        </h2>
        <p className="text-muted-foreground mt-1">Inspect durable pipeline executions, step results, and outputs.</p>
      </div>

      <form key={filterQuery} onSubmit={updateFilters} className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-2 lg:grid-cols-5" aria-label="Filter runs">
        <select name="status" aria-label="Status" defaultValue={filters.status} className="rounded-lg border border-border bg-background px-3 py-2 text-sm"><option value="">Any status</option>{['QUEUED','RUNNING','COMPLETED','FAILED','REVIEW_REQUIRED','RETRY_WAIT'].map((value) => <option key={value}>{value}</option>)}</select>
        <input name="pipeline" aria-label="Pipeline slug" defaultValue={filters.pipeline} placeholder="Pipeline slug" className="rounded-lg border border-border bg-background px-3 py-2 text-sm" />
        <input type="datetime-local" name="from" defaultValue={filters.from ? toDateTimeLocalValue(filters.from) : ''} aria-label="From" className="rounded-lg border border-border bg-background px-3 py-2 text-sm" />
        <input type="datetime-local" name="to" defaultValue={filters.to ? toDateTimeLocalValue(filters.to) : ''} aria-label="To" className="rounded-lg border border-border bg-background px-3 py-2 text-sm" />
        <div className="flex gap-2"><Button type="submit" size="sm">Apply</Button><Button type="button" size="sm" variant="outline" onClick={resetFilters}>Reset</Button></div>
      </form>

      {isLoading ? (
        <TableSkeleton />
      ) : !pageData?.content?.length ? (
        <EmptyState
          title={Object.values(filters).some(Boolean) ? 'No runs match these filters' : 'No runs found'}
          description={Object.values(filters).some(Boolean) ? 'Try broadening the filters or reset them.' : 'Runs are created by the developer API or the pipeline test console.'}
          action={Object.values(filters).some(Boolean) ? <Button variant="outline" onClick={resetFilters}>Reset filters</Button> : <Link href="/assets/upload"><Button variant="outline">Upload an asset</Button></Link>}
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-white/5 bg-card/20 shadow-lg shadow-black/20 backdrop-blur-md">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="border-b border-white/5 bg-white/5 text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-6 py-4 font-medium tracking-wider">Pipeline</th>
                  <th className="px-6 py-4 font-medium tracking-wider">Input asset</th>
                  <th className="px-6 py-4 font-medium tracking-wider">Created</th>
                  <th className="px-6 py-4 font-medium tracking-wider">Terminal summary</th>
                  <th className="px-6 py-4 text-right font-medium tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {pageData.content.map((run) => (
                  <tr key={run.id} className="group transition-colors hover:bg-white/5">
                    <td className="px-6 py-4">
                      <Link href={filterQuery ? `/jobs/${run.id}?${filterQuery}` : `/jobs/${run.id}`} className="font-medium text-primary hover:underline">
                        {run.pipeline?.slug ?? 'Not available'} <span className="font-mono text-xs text-muted-foreground">{run.pipeline?.version != null ? `v${run.pipeline.version}` : 'Not available'}</span>
                      </Link>
                      <p className="mt-1 font-mono text-xs text-muted-foreground">{run.id}</p>
                    </td>
                    <td className="px-6 py-4 font-mono text-xs text-muted-foreground">
                      {run.inputAssetId ? <Link href={`/assets/${run.inputAssetId}`} className="hover:text-primary hover:underline">{run.inputAssetId}</Link> : 'Not available'}
                    </td>
                    <td className="px-6 py-4 text-muted-foreground">{run.createdAt ? new Date(run.createdAt).toLocaleString() : 'Not available'}</td>
                    <td className="max-w-sm px-6 py-4 text-muted-foreground">{terminalSummary(run)}</td>
                    <td className="px-6 py-4 text-right"><StatusBadge status={run.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-white/5 bg-black/20 px-6 py-4 text-xs text-muted-foreground">
            <span>Showing {pageData.numberOfElements} of {pageData.totalElements} runs</span>
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
