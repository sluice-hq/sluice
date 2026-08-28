'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Shield } from 'lucide-react';
import { getRuns } from '@/api/runs';
import type { RunDetails } from '@/api/types';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusBadge } from '@/components/domain/StatusBadge';
import { TableSkeleton } from '@/components/domain/SkeletonLoader';

export default function GovernancePage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['runs', 'governance'],
    queryFn: () => getRuns(0, 100),
    refetchInterval: 5000,
  });
  const decisions = data?.content.filter((run): run is RunDetails & {
    pipeline: NonNullable<RunDetails['pipeline']>;
    governance: NonNullable<RunDetails['governance']>;
  } => run.governance != null && run.pipeline != null) ?? [];

  return <div className="space-y-6">
    <header>
      <h1 className="flex items-center gap-2 text-2xl font-bold"><Shield className="h-6 w-6 text-primary" />Governance decisions</h1>
      <p className="mt-1 text-muted-foreground">Review the persisted policy result for media processed by a governance pipeline.</p>
    </header>
    {isLoading ? <TableSkeleton /> : error ? <EmptyState title="Could not load governance decisions" description={error.message} />
      : decisions.length === 0 ? <EmptyState title="No governance decisions yet" description="Run a pipeline containing governance.content-safety to record an ALLOW, REVIEW, or BLOCK decision." />
        : <div className="overflow-hidden rounded-xl border border-border bg-card">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-background/50 text-xs uppercase text-muted-foreground"><tr>
              <th className="px-5 py-3">Run</th><th className="px-5 py-3">Pipeline</th><th className="px-5 py-3">Decision</th><th className="px-5 py-3">Provider</th><th className="px-5 py-3">Created</th>
            </tr></thead>
            <tbody className="divide-y divide-border">{decisions.map((run) => <tr key={run.id}>
              <td className="px-5 py-4 font-mono text-xs"><Link className="text-primary hover:underline" href={`/jobs/${run.id}`}>{run.id}</Link></td>
              <td className="px-5 py-4">{run.pipeline.slug} · v{run.pipeline.version}</td>
              <td className="px-5 py-4"><StatusBadge status={run.governance!.decision} /></td>
              <td className="px-5 py-4">{run.governance!.provider}</td>
              <td className="px-5 py-4 text-muted-foreground">{new Date(run.createdAt).toLocaleString()}</td>
            </tr>)}</tbody>
          </table>
        </div>}
  </div>;
}
