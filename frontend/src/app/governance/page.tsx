'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { Search, Shield, X } from 'lucide-react';
import { getGovernanceRuns, type GovernanceRunFilters } from '@/api/runs';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

const pageSize = 20;

export default function GovernancePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [filterError, setFilterError] = useState('');
  const requestedPage = Number(searchParams.get('page') ?? '0');
  const page = Number.isSafeInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
  const filterQuery = searchParams.toString();
  const filters: GovernanceRunFilters = {
    decision: searchParams.get('decision') ?? '',
    pipeline: searchParams.get('pipeline') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
  };
  const hasFilters = Object.values(filters).some(Boolean);
  const hasQueryState = hasFilters || page > 0;
  const { data: pageData, isLoading, error } = useQuery({
    queryKey: ['runs', 'governance', page, pageSize, filterQuery],
    queryFn: () => getGovernanceRuns(page, pageSize, filters),
  });

  const navigate = (params: URLSearchParams) => {
    setFilterError('');
    const query = params.toString();
    router.push(query ? `/governance?${query}` : '/governance');
  };

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const rawFrom = String(form.get('from') ?? '');
    const rawTo = String(form.get('to') ?? '');
    if (rawFrom && rawTo && rawFrom > rawTo) {
      setFilterError('Created from must not be after Created through.');
      return;
    }
    setFilterError('');
    const params = new URLSearchParams();
    const decision = String(form.get('decision') ?? '').trim();
    const pipeline = String(form.get('pipeline') ?? '').trim();
    if (decision) params.set('decision', decision);
    if (pipeline) params.set('pipeline', pipeline);
    if (rawFrom) params.set('from', rawFrom);
    if (rawTo) params.set('to', rawTo);
    navigate(params);
  };

  const resetFilters = () => { setFilterError(''); router.push('/governance'); };
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
  const runHref = (runId: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('returnTo', 'governance');
    return `/jobs/${runId}?${params.toString()}`;
  };
  const activeFilters = [
    filters.decision ? ['decision', `Decision: ${filters.decision}`] : null,
    filters.pipeline ? ['pipeline', `Pipeline: ${filters.pipeline}`] : null,
    filters.from ? ['from', `From: ${filters.from}`] : null,
    filters.to ? ['to', `Through: ${filters.to}`] : null,
  ].filter((entry): entry is string[] => entry !== null);

  return <div className="space-y-6">
    <header>
      <h1 className="flex items-center gap-2 text-2xl font-bold"><Shield className="h-6 w-6 text-primary" />Governance decisions</h1>
      <p className="mt-1 text-muted-foreground">Review persisted policy results across every governed run in this project.</p>
    </header>

    <form key={filterQuery} onSubmit={applyFilters} className="space-y-4 rounded-xl border border-border bg-card/60 p-4" aria-label="Filter governance decisions">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <div className="space-y-1.5">
          <label htmlFor="governance-decision" className="block text-xs font-medium text-muted-foreground">Decision</label>
          <select id="governance-decision" name="decision" defaultValue={filters.decision} className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm">
            <option value="">Any decision</option><option value="ALLOW">ALLOW</option><option value="REVIEW">REVIEW</option><option value="BLOCK">BLOCK</option>
          </select>
        </div>
        <label className="space-y-1.5 text-xs font-medium text-muted-foreground">
          Pipeline slug
          <Input name="pipeline" defaultValue={filters.pipeline} placeholder="product-images" maxLength={100} />
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
          <Button type="button" size="sm" variant="outline" onClick={resetFilters} disabled={!hasQueryState}>Reset</Button>
        </div>
      </div>
      {filterError ? <p role="alert" className="text-sm text-destructive">{filterError}</p> : null}
      {activeFilters.length ? <div className="flex flex-wrap items-center gap-2" aria-label="Applied governance filters">
        <span className="text-xs text-muted-foreground">Applied:</span>
        {activeFilters.map(([name, label]) => <button key={name} type="button" onClick={() => removeFilter(name)} className="inline-flex cursor-pointer items-center gap-1 rounded-full border border-primary/20 bg-primary/10 px-2.5 py-1 text-xs text-primary transition-colors hover:bg-primary/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`Remove ${label}`}>
          {label}<X className="size-3" />
        </button>)}
      </div> : null}
    </form>

    {error ? <EmptyState title="Could not load governance decisions" description={error.message} action={<Button variant="outline" onClick={resetFilters}>Reset filters</Button>} />
      : isLoading ? <TableSkeleton />
      : !pageData?.content.length ? <EmptyState
        title={page > 0 ? 'No governance decisions on this page' : hasFilters ? 'No governance decisions match these filters' : 'No governance decisions yet'}
        description={page > 0 ? 'Return to the first page or adjust the filters.' : hasFilters ? 'Try removing a filter or broadening the created-date range.' : 'Run a pipeline containing governance.content-safety to record an ALLOW, REVIEW, or BLOCK decision.'}
        action={hasQueryState ? <Button variant="outline" onClick={resetFilters}>{page > 0 ? 'Return to first page' : 'Reset filters'}</Button> : undefined}
      />
      : <div className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px] text-left text-sm">
            <thead className="border-b border-border bg-background/50 text-xs uppercase text-muted-foreground"><tr>
              <th className="px-5 py-3">Run</th><th className="px-5 py-3">Pipeline</th><th className="px-5 py-3">Decision</th><th className="px-5 py-3">Provider</th><th className="px-5 py-3">Created</th>
            </tr></thead>
            <tbody className="divide-y divide-border">{pageData.content.map((run) => <tr key={run.id} className="transition-colors hover:bg-white/5">
              <td className="px-5 py-4 font-mono text-xs"><Link className="text-primary hover:underline" href={runHref(run.id)}>{run.id}</Link></td>
              <td className="px-5 py-4">{run.pipeline ? <>{run.pipeline.slug} <span className="font-mono text-xs text-muted-foreground">v{run.pipeline.version}</span></> : 'Not available'}</td>
              <td className="px-5 py-4">{run.governance ? <StatusBadge status={run.governance.decision} /> : 'Not available'}</td>
              <td className="px-5 py-4">{run.governance?.provider ?? 'Not available'}</td>
              <td className="px-5 py-4 text-muted-foreground">{new Date(run.createdAt).toLocaleString()}</td>
            </tr>)}</tbody>
          </table>
        </div>
        <div className="flex items-center justify-between border-t border-border bg-background/30 px-5 py-4 text-xs text-muted-foreground">
          <span>Showing {page * pageSize + 1}-{page * pageSize + pageData.numberOfElements} of {pageData.totalElements} decisions</span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={pageData.first} onClick={() => goToPage(page - 1)}>Previous</Button>
            <Button variant="outline" size="sm" disabled={pageData.last} onClick={() => goToPage(page + 1)}>Next</Button>
          </div>
        </div>
      </div>}
  </div>;
}
