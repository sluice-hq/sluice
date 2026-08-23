'use client';

import { useQuery } from '@tanstack/react-query';
import { listProcessors, type ProcessorContract } from '@/api/processors';

function Contract({ title, value }: { title: string; value: ProcessorContract['input'] }) {
  return <div className="rounded-lg border border-border bg-background/40 p-3">
    <p className="text-xs uppercase tracking-wide text-muted-foreground">{title}</p>
    <p className="mt-1 text-sm font-medium">{value.kind} · {value.mimeTypes.join(', ')}</p>
  </div>;
}

export default function ProcessorsPage() {
  const { data = [], isLoading, isError } = useQuery({ queryKey: ['processors'], queryFn: listProcessors });

  return <div className="max-w-6xl space-y-8">
    <header>
      <p className="text-sm font-medium text-primary">Processor Market</p>
      <h1 className="mt-1 text-2xl font-bold">Versioned building blocks</h1>
      <p className="mt-2 text-muted-foreground">Every processor exposes the input, output, limits, and permissions that pipelines will validate.</p>
    </header>
    {isLoading && <p className="text-muted-foreground">Loading processor releases…</p>}
    {isError && <p className="rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm">The processor catalog could not be loaded.</p>}
    <div className="grid gap-4 md:grid-cols-2">
      {data.map((processor) => <article key={`${processor.slug}@${processor.version}`} className="rounded-xl border border-border bg-card p-5 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div><h2 className="text-lg font-semibold">{processor.displayName}</h2><p className="text-sm text-muted-foreground">{processor.description}</p></div>
          <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-1 text-xs text-primary">v{processor.version}</span>
        </div>
        <div className="flex gap-2 text-xs text-muted-foreground"><span>{processor.category}</span><span>·</span><span>{processor.status}</span></div>
        <div className="grid gap-3 sm:grid-cols-2"><Contract title="Input" value={processor.input} /><Contract title="Output" value={processor.output} /></div>
        <div className="border-t border-border pt-3 text-xs text-muted-foreground">{processor.limits.timeoutSeconds}s timeout · {processor.limits.memoryMb}MB memory · {processor.permissions.join(', ')}</div>
      </article>)}
    </div>
  </div>;
}
