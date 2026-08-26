'use client';

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { listProcessors, type ProcessorContract } from '@/api/processors';

const categoryLabels: Record<string, { title: string; detail: string }> = {
  transform: { title: 'Transform', detail: 'Change an asset while preserving its role in the pipeline.' },
  compression: { title: 'Optimize', detail: 'Reduce delivery cost while keeping a deliberate output format.' },
  metadata: { title: 'Privacy', detail: 'Inspect or remove embedded asset metadata.' },
  governance: { title: 'Governance', detail: 'Apply safety and policy decisions before delivery.' },
  validation: { title: 'Validation', detail: 'Check inputs and produce reusable processing facts.' },
};

type ConfigProperty = {
  type?: string;
  description?: string;
  default?: unknown;
  enum?: unknown[];
  minimum?: number;
  maximum?: number;
};

function bytes(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value % 1_000_000 === 0 ? 0 : 1)} MB`;
  if (value >= 1_000) return `${Math.round(value / 1_000)} KB`;
  return `${value} B`;
}

function contractLabel(contract: ProcessorContract['input']) {
  return `${contract.kind} · ${contract.mimeTypes.join(', ')}`;
}

function configProperties(processor: ProcessorContract) {
  return (processor.configSchema.properties ?? {}) as Record<string, ConfigProperty>;
}

function configExample(processor: ProcessorContract) {
  return Object.fromEntries(Object.entries(configProperties(processor))
    .filter(([, property]) => property.default !== undefined)
    .map(([key, property]) => [key, property.default]));
}

function releaseStatus(processor: ProcessorContract) {
  return processor.status.toLowerCase().replaceAll('_', ' ');
}

function VersionHistory({ releases }: { releases: ProcessorContract[] }) {
  if (!releases.length) return null;

  return <details className="rounded-lg border border-border bg-background/35 px-4 py-3">
    <summary className="cursor-pointer text-sm font-medium">Version history ({releases.length})</summary>
    <div className="mt-3 space-y-3 border-t border-border pt-3">
      {releases.map((release) => <div key={`${release.slug}@${release.version}`} className="text-sm">
        <div className="flex flex-wrap items-center gap-2"><span className="font-medium">v{release.version}</span><span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize text-muted-foreground">{releaseStatus(release)}</span></div>
        <p className="mt-1 text-muted-foreground">{release.releaseNotes || 'No release notes were supplied in this manifest.'}</p>
      </div>)}
    </div>
  </details>;
}

function ProcessorCard({ processor, history }: { processor: ProcessorContract; history: ProcessorContract[] }) {
  const properties = configProperties(processor);
  const example = JSON.stringify({
    id: processor.slug.replaceAll('.', '-'),
    processor: processor.slug,
    version: processor.version,
    config: configExample(processor),
  }, null, 2);

  return <article className="overflow-hidden rounded-2xl border border-border bg-card shadow-[0_16px_48px_rgb(0_0_0_/_0.12)]">
    <div className="border-b border-border bg-gradient-to-r from-primary/10 via-transparent to-transparent p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-2xl"><div className="flex flex-wrap items-center gap-2"><h2 className="text-xl font-semibold tracking-tight">{processor.displayName}</h2><span className="rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">Recommended · v{processor.version}</span></div>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{processor.description}</p></div>
        <div className="text-right text-xs text-muted-foreground"><p>Published by {processor.publisher}</p><p className="mt-1 capitalize">{processor.visibility.toLowerCase()} release</p></div>
      </div>
    </div>

    <div className="grid gap-4 p-5 sm:p-6 lg:grid-cols-2">
      <section className="space-y-3"><h3 className="text-sm font-semibold">Compatibility</h3>
        <div className="grid gap-3 sm:grid-cols-2"><div className="rounded-xl border border-border bg-background/45 p-3"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Accepts</p><p className="mt-1 text-sm font-medium">{contractLabel(processor.input)}</p><p className="mt-1 text-xs text-muted-foreground">Up to {bytes(processor.input.maxBytes)} · {processor.input.maxPixels.toLocaleString()} pixels</p></div>
          <div className="rounded-xl border border-border bg-background/45 p-3"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Produces</p><p className="mt-1 text-sm font-medium">{contractLabel(processor.output)}</p><p className="mt-1 text-xs text-muted-foreground">Up to {bytes(processor.output.maxBytes)} · {processor.output.maxPixels.toLocaleString()} pixels</p></div></div>
      </section>
      <section className="space-y-3"><h3 className="text-sm font-semibold">Runtime safeguards</h3>
        <div className="grid grid-cols-3 gap-2 text-center text-xs"><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{processor.limits.timeoutSeconds}s</p><p className="mt-1 text-muted-foreground">timeout</p></div><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{processor.limits.memoryMb} MB</p><p className="mt-1 text-muted-foreground">memory</p></div><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{bytes(processor.limits.maxOutputBytes)}</p><p className="mt-1 text-muted-foreground">max output</p></div></div>
        <p className="rounded-lg border border-border bg-background/35 p-3 text-xs leading-5 text-muted-foreground"><span className="font-medium text-foreground">Permissions:</span> {processor.permissions.length ? processor.permissions.join(', ') : 'None declared in this manifest.'}</p>
      </section>

      <section className="space-y-3"><h3 className="text-sm font-semibold">Configuration contract</h3>
        {Object.keys(properties).length ? <div className="space-y-2">{Object.entries(properties).map(([key, property]) => <div key={key} className="rounded-lg border border-border bg-background/35 p-3 text-sm"><div className="flex flex-wrap items-baseline justify-between gap-2"><code className="font-medium text-primary">{key}</code><span className="text-xs text-muted-foreground">{property.type ?? 'value'}{property.enum ? ` · ${property.enum.join(', ')}` : ''}</span></div><p className="mt-1 text-xs leading-5 text-muted-foreground">{property.description || 'Validated by this processor manifest.'}{property.default !== undefined ? ` Default: ${JSON.stringify(property.default)}.` : ''}{property.minimum !== undefined || property.maximum !== undefined ? ` Range: ${property.minimum ?? 'unbounded'} to ${property.maximum ?? 'unbounded'}.` : ''}</p></div>)}</div> : <p className="rounded-lg border border-border bg-background/35 p-3 text-sm text-muted-foreground">This processor has no configuration fields.</p>}
      </section>
      <section className="space-y-3"><h3 className="text-sm font-semibold">Pipeline step example</h3><pre className="overflow-x-auto rounded-xl border border-border bg-slate-950 p-4 text-xs leading-5 text-slate-100"><code>{example}</code></pre></section>
    </div>

    <div className="grid gap-4 border-t border-border bg-background/25 p-5 sm:p-6 lg:grid-cols-[1fr_auto]"><div><h3 className="text-sm font-semibold">Release notes</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{processor.releaseNotes || 'No release notes were supplied in this manifest.'}</p></div><div className="min-w-64"><VersionHistory releases={history} /></div></div>
  </article>;
}

export default function ProcessorsPage() {
  const { data = [], isLoading, isError } = useQuery({ queryKey: ['processors'], queryFn: listProcessors });
  const catalog = useMemo(() => {
    const bySlug = new Map<string, ProcessorContract[]>();
    data.forEach((processor) => bySlug.set(processor.slug, [...(bySlug.get(processor.slug) ?? []), processor]));
    const products = [...bySlug.values()].map((releases) => {
      const recommended = releases.find((release) => release.status === 'PUBLISHED') ?? releases[0];
      return { recommended, history: releases.filter((release) => release !== recommended) };
    });
    return products.reduce<Record<string, typeof products>>((groups, product) => {
      const category = product.recommended.category;
      groups[category] = [...(groups[category] ?? []), product];
      return groups;
    }, {});
  }, [data]);

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="max-w-3xl"><p className="text-sm font-medium text-primary">Processor Market</p><h1 className="mt-1 text-3xl font-bold tracking-tight">Composable media capabilities, with the facts upfront.</h1><p className="mt-3 leading-7 text-muted-foreground">Choose a recommended release, inspect its exact contract, and pin that version in a pipeline. Older releases remain visible for existing integrations without being mistaken for new products.</p></header>
    {isLoading && <p className="text-muted-foreground">Loading processor catalog…</p>}
    {isError && <p role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-sm">The processor catalog could not be loaded. Try again once the API is available.</p>}
    {!isLoading && !isError && !data.length && <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">No processor releases are published yet.</p>}
    {Object.entries(catalog).sort(([left], [right]) => (categoryLabels[left]?.title ?? left).localeCompare(categoryLabels[right]?.title ?? right)).map(([category, products]) => {
      const label = categoryLabels[category] ?? { title: category, detail: 'Processor releases from the Sluice catalog.' };
      return <section key={category} aria-labelledby={`${category}-heading`} className="space-y-4"><div className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-4"><div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{label.title}</p><h2 id={`${category}-heading`} className="mt-1 text-xl font-semibold">{products.length} {products.length === 1 ? 'processor' : 'processors'}</h2></div><p className="max-w-xl text-sm text-muted-foreground">{label.detail}</p></div><div className="space-y-5">{products.map(({ recommended, history }) => <ProcessorCard key={recommended.slug} processor={recommended} history={history} />)}</div></section>;
    })}
  </div>;
}
