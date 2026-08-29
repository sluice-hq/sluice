'use client';

import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  disableProcessorRelease, enableProcessorRelease, listProjectProcessors,
  type ProcessorContract, type ProjectProcessorRelease,
} from '@/api/processors';
import { Button } from '@/components/ui/button';

const categoryLabels: Record<string, { title: string; detail: string }> = {
  transform: { title: 'Transform', detail: 'Change an asset while preserving its role in the pipeline.' },
  compression: { title: 'Optimize', detail: 'Reduce delivery cost while keeping a deliberate output format.' },
  metadata: { title: 'Privacy', detail: 'Inspect or remove embedded asset metadata.' },
  governance: { title: 'Governance', detail: 'Apply safety and policy decisions before delivery.' },
  validation: { title: 'Validation', detail: 'Check inputs and produce reusable processing facts.' },
};

type Session = { selectedProjectId?: string };
type ConfigProperty = { type?: string; description?: string; default?: unknown; enum?: unknown[]; minimum?: number; maximum?: number };
type Product = { recommended: ProjectProcessorRelease; history: ProjectProcessorRelease[]; releases: ProjectProcessorRelease[] };

function configProperties(processor: ProcessorContract) {
  return (processor.configSchema.properties ?? {}) as Record<string, ConfigProperty>;
}

function configExample(processor: ProcessorContract) {
  return Object.fromEntries(Object.entries(configProperties(processor))
    .filter(([, property]) => property.default !== undefined)
    .map(([key, property]) => [key, property.default]));
}

function stepJson(processor: ProcessorContract) {
  return JSON.stringify({ id: processor.slug.replaceAll('.', '-'), processor: processor.slug, version: processor.version, config: configExample(processor) }, null, 2);
}

function bytes(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value % 1_000_000 === 0 ? 0 : 1)} MB`;
  if (value >= 1_000) return `${Math.round(value / 1_000)} KB`;
  return `${value} B`;
}

function mediaLabel(mime: string) {
  const labels: Record<string, string> = { 'image/png': 'PNG', 'image/jpeg': 'JPEG', 'image/webp': 'WebP', 'application/pdf': 'PDF', 'video/mp4': 'MP4' };
  return labels[mime] ?? mime;
}

function searchText(release: ProjectProcessorRelease) {
  const processor = release.processor;
  return [processor.displayName, processor.slug, processor.description, processor.publisher, processor.category,
    processor.version, processor.releaseNotes, ...processor.input.mimeTypes, ...processor.output.mimeTypes,
    ...Object.keys(configProperties(processor))].join(' ').toLowerCase();
}

function releaseKey(release: ProjectProcessorRelease) {
  return `${release.processor.slug}@${release.processor.version}`;
}

function ReleaseActions({ release, projectId, pendingKey, confirming, onEnable, onAskDisable, onCancelDisable, onDisable, onCopy }: {
  release: ProjectProcessorRelease; projectId: string; pendingKey?: string; confirming: boolean;
  onEnable: () => void; onAskDisable: () => void; onCancelDisable: () => void; onDisable: () => void; onCopy: () => void;
}) {
  const processor = release.processor;
  const key = releaseKey(release);
  const pending = pendingKey === key;
  const href = `/pipelines?processor=${encodeURIComponent(processor.slug)}&version=${encodeURIComponent(processor.version)}`;
  return <div className="space-y-2">
    <div className="flex flex-wrap gap-2">
      {release.enabled
        ? <><Link href={href} className="inline-flex h-8 items-center rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground hover:bg-primary/80">Use in pipeline</Link><Button type="button" variant="outline" disabled={pending} onClick={onAskDisable}>Disable</Button></>
        : <Button type="button" disabled={pending || !projectId} onClick={onEnable}>{pending ? 'Enabling…' : 'Enable for project'}</Button>}
      <Button type="button" variant="ghost" onClick={onCopy}>Copy step JSON</Button>
    </div>
    {confirming && <div role="alertdialog" aria-label={`Disable ${processor.displayName} ${processor.version}`} className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
      <p>Disable this release for new pipeline authoring? Published pipelines remain unchanged.</p>
      <div className="mt-2 flex gap-2"><Button type="button" variant="destructive" disabled={pending} onClick={onDisable}>Confirm disable</Button><Button type="button" variant="ghost" onClick={onCancelDisable}>Cancel</Button></div>
    </div>}
  </div>;
}

function ProcessorCard({ product, projectId, pendingKey, confirmingKey, onEnable, onAskDisable, onCancelDisable, onDisable, onCopy }: {
  product: Product; projectId: string; pendingKey?: string; confirmingKey: string | null;
  onEnable: (release: ProjectProcessorRelease) => void; onAskDisable: (release: ProjectProcessorRelease) => void;
  onCancelDisable: () => void; onDisable: (release: ProjectProcessorRelease) => void; onCopy: (release: ProjectProcessorRelease) => void;
}) {
  const release = product.recommended;
  const processor = release.processor;
  const properties = configProperties(processor);
  return <article id={`processor-${processor.slug}`} className="overflow-hidden rounded-2xl border border-border bg-card shadow-[0_16px_48px_rgb(0_0_0_/_0.12)]">
    <div className="border-b border-border bg-gradient-to-r from-primary/10 via-transparent to-transparent p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4"><div className="max-w-2xl"><div className="flex flex-wrap items-center gap-2"><h2 className="text-xl font-semibold tracking-tight">{processor.displayName}</h2><span className="rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">Recommended · v{processor.version}</span><span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${release.enabled ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300' : 'border-border text-muted-foreground'}`}>{release.enabled ? 'Enabled' : 'Not enabled'}</span></div><p className="mt-2 text-sm leading-6 text-muted-foreground">{processor.description}</p></div><div className="text-right text-xs text-muted-foreground"><p>Published by {processor.publisher}</p><p className="mt-1 capitalize">{processor.visibility.toLowerCase()} release</p></div></div>
      <div className="mt-4"><ReleaseActions release={release} projectId={projectId} pendingKey={pendingKey} confirming={confirmingKey === releaseKey(release)} onEnable={() => onEnable(release)} onAskDisable={() => onAskDisable(release)} onCancelDisable={onCancelDisable} onDisable={() => onDisable(release)} onCopy={() => onCopy(release)} /></div>
    </div>
    <div className="grid gap-4 p-5 sm:p-6 lg:grid-cols-2">
      <section className="space-y-3"><h3 className="text-sm font-semibold">Compatibility</h3><div className="grid gap-3 sm:grid-cols-2"><div className="rounded-xl border border-border bg-background/45 p-3"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Accepts</p><p className="mt-1 text-sm font-medium">{processor.input.kind} · {processor.input.mimeTypes.join(', ')}</p><p className="mt-1 text-xs text-muted-foreground">Up to {bytes(processor.input.maxBytes)} · {processor.input.maxPixels.toLocaleString()} pixels</p></div><div className="rounded-xl border border-border bg-background/45 p-3"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Produces</p><p className="mt-1 text-sm font-medium">{processor.output.kind} · {processor.output.mimeTypes.join(', ')}</p><p className="mt-1 text-xs text-muted-foreground">Up to {bytes(processor.output.maxBytes)} · {processor.output.maxPixels.toLocaleString()} pixels</p></div></div></section>
      <section className="space-y-3"><h3 className="text-sm font-semibold">Runtime safeguards</h3><div className="grid grid-cols-3 gap-2 text-center text-xs"><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{processor.limits.timeoutSeconds}s</p><p className="mt-1 text-muted-foreground">timeout</p></div><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{processor.limits.memoryMb} MB</p><p className="mt-1 text-muted-foreground">memory</p></div><div className="rounded-xl border border-border bg-background/45 p-3"><p className="font-semibold">{bytes(processor.limits.maxOutputBytes)}</p><p className="mt-1 text-muted-foreground">max output</p></div></div><p className="rounded-lg border border-border bg-background/35 p-3 text-xs leading-5 text-muted-foreground"><span className="font-medium text-foreground">Permissions:</span> {processor.permissions.length ? processor.permissions.join(', ') : 'None declared in this manifest.'}</p></section>
      <section className="space-y-3"><h3 className="text-sm font-semibold">Configuration contract</h3>{Object.keys(properties).length ? <div className="space-y-2">{Object.entries(properties).map(([key, property]) => <div key={key} className="rounded-lg border border-border bg-background/35 p-3 text-sm"><div className="flex flex-wrap items-baseline justify-between gap-2"><code className="font-medium text-primary">{key}</code><span className="text-xs text-muted-foreground">{property.type ?? 'value'}{property.enum ? ` · ${property.enum.join(', ')}` : ''}</span></div><p className="mt-1 text-xs leading-5 text-muted-foreground">{property.description || 'Validated by this processor manifest.'}{property.default !== undefined ? ` Default: ${JSON.stringify(property.default)}.` : ''}</p></div>)}</div> : <p className="rounded-lg border border-border bg-background/35 p-3 text-sm text-muted-foreground">This processor has no configuration fields.</p>}</section>
      <section className="space-y-3"><h3 className="text-sm font-semibold">Pipeline step example</h3><pre className="overflow-x-auto rounded-xl border border-border bg-slate-950 p-4 text-xs leading-5 text-slate-100"><code>{stepJson(processor)}</code></pre></section>
    </div>
    <div className="grid gap-4 border-t border-border bg-background/25 p-5 sm:p-6 lg:grid-cols-[1fr_minmax(18rem,0.8fr)]"><div><h3 className="text-sm font-semibold">Release notes</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{processor.releaseNotes || 'No release notes were supplied in this manifest.'}</p></div>{product.history.length > 0 && <details className="rounded-lg border border-border bg-background/35 px-4 py-3"><summary className="cursor-pointer text-sm font-medium">Version history ({product.history.length})</summary><div className="mt-3 space-y-4 border-t border-border pt-3">{product.history.map((older) => <div key={releaseKey(older)} className="space-y-2 border-b border-border/60 pb-4 last:border-0 last:pb-0"><div className="flex flex-wrap items-center gap-2"><span className="font-medium">v{older.processor.version}</span><span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize text-muted-foreground">{older.processor.status.toLowerCase()}</span><span className="text-xs text-muted-foreground">{older.enabled ? 'Enabled' : 'Not enabled'}</span></div><p className="text-sm text-muted-foreground">{older.processor.releaseNotes || 'No release notes supplied.'}</p><ReleaseActions release={older} projectId={projectId} pendingKey={pendingKey} confirming={confirmingKey === releaseKey(older)} onEnable={() => onEnable(older)} onAskDisable={() => onAskDisable(older)} onCancelDisable={onCancelDisable} onDisable={() => onDisable(older)} onCopy={() => onCopy(older)} /></div>)}</div></details>}</div>
  </article>;
}

export default function ProcessorsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const [feedback, setFeedback] = useState('');
  const [confirmingKey, setConfirmingKey] = useState<string | null>(null);
  const session = useQuery<Session>({ queryKey: ['session'], queryFn: async () => { const response = await fetch('/api/session'); if (!response.ok) throw new Error('Session unavailable'); return response.json(); } });
  const projectId = session.data?.selectedProjectId ?? '';
  const catalogQuery = useQuery({ queryKey: ['project-processor-releases', projectId], queryFn: () => listProjectProcessors(projectId), enabled: !!projectId });
  const releases = useMemo(() => catalogQuery.data ?? [], [catalogQuery.data]);
  const mutation = useMutation({
    mutationFn: async ({ release, enable }: { release: ProjectProcessorRelease; enable: boolean }) => {
      if (enable) await enableProcessorRelease(projectId, release.processor.slug, release.processor.version);
      else await disableProcessorRelease(projectId, release.processor.slug, release.processor.version);
    },
    onSuccess: async (_, variables) => { setConfirmingKey(null); setFeedback(`${variables.release.processor.displayName} v${variables.release.processor.version} ${variables.enable ? 'enabled for this project.' : 'disabled for new authoring. Published pipelines remain unchanged.'}`); await queryClient.invalidateQueries({ queryKey: ['project-processor-releases', projectId] }); },
    onError: (error) => setFeedback(error instanceof Error ? error.message : 'The processor setting could not be changed.'),
  });
  const q = searchParams.get('q') ?? '';
  const category = searchParams.get('category') ?? '';
  const input = searchParams.get('input') ?? '';
  const output = searchParams.get('output') ?? '';
  const sort = searchParams.get('sort') ?? 'name';
  function setParam(name: string, value: string) { const next = new URLSearchParams(searchParams.toString()); if (value) next.set(name, value); else next.delete(name); const query = next.toString(); router.replace(`${pathname}${query ? `?${query}` : ''}`, { scroll: false }); }
  function reset() { router.replace(pathname, { scroll: false }); }

  const products = useMemo(() => {
    const bySlug = new Map<string, ProjectProcessorRelease[]>();
    releases.forEach((release) => bySlug.set(release.processor.slug, [...(bySlug.get(release.processor.slug) ?? []), release]));
    return [...bySlug.values()].map((items): Product => ({ recommended: items.find((item) => item.processor.status === 'PUBLISHED') ?? items[0], history: items.filter((item) => item !== (items.find((candidate) => candidate.processor.status === 'PUBLISHED') ?? items[0])), releases: items }));
  }, [releases]);
  const filtered = useMemo(() => products.filter((product) => {
    const matchesQuery = !q || product.releases.some((release) => searchText(release).includes(q.trim().toLowerCase()));
    const matchesCategory = !category || product.releases.some((release) => release.processor.category === category);
    const matchesInput = !input || product.releases.some((release) => release.processor.input.mimeTypes.includes(input));
    const matchesOutput = !output || product.releases.some((release) => release.processor.output.mimeTypes.includes(output));
    return matchesQuery && matchesCategory && matchesInput && matchesOutput;
  }).sort((left, right) => {
    if (sort === 'category') return left.recommended.processor.category.localeCompare(right.recommended.processor.category) || left.recommended.processor.displayName.localeCompare(right.recommended.processor.displayName);
    if (sort === 'newest') return Date.parse(right.recommended.processor.publishedAt) - Date.parse(left.recommended.processor.publishedAt);
    if (sort === 'input') return mediaLabel(left.recommended.processor.input.mimeTypes[0] ?? '').localeCompare(mediaLabel(right.recommended.processor.input.mimeTypes[0] ?? ''));
    return left.recommended.processor.displayName.localeCompare(right.recommended.processor.displayName);
  }), [products, q, category, input, output, sort]);
  const categories = [...new Set(releases.map((release) => release.processor.category))].sort();
  const inputTypes = [...new Set(releases.flatMap((release) => release.processor.input.mimeTypes))].sort();
  const outputTypes = [...new Set(releases.flatMap((release) => release.processor.output.mimeTypes))].sort();
  const activeFilters = [['q', q], ['category', category], ['input', input], ['output', output]].filter(([, value]) => value);
  const pendingKey = mutation.isPending ? releaseKey(mutation.variables.release) : undefined;
  async function copy(release: ProjectProcessorRelease) { try { await navigator.clipboard.writeText(stepJson(release.processor)); setFeedback(`Copied ${release.processor.displayName} v${release.processor.version} step JSON.`); } catch { setFeedback('Clipboard unavailable. Select the example and copy it manually.'); } }

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="max-w-3xl"><p className="text-sm font-medium text-primary">Processor Market</p><h1 className="mt-1 text-3xl font-bold tracking-tight">Composable media capabilities, with the facts upfront.</h1><p className="mt-3 leading-7 text-muted-foreground">Discover an exact release, enable it for this project, then add it to a pipeline. Every result comes from the versioned processor manifest.</p></header>
    <section aria-label="Processor discovery" className="space-y-4 rounded-2xl border border-border bg-card p-4 sm:p-5">
      <div className="grid gap-3 lg:grid-cols-[minmax(14rem,1fr)_repeat(4,minmax(9rem,auto))]"><label className="text-sm">Search processors<input type="search" value={q} onChange={(event) => setParam('q', event.target.value)} placeholder="Name, purpose, MIME type, parameter…" className="mt-1 w-full rounded-lg border border-border bg-background p-2.5" /></label><label className="text-sm">Category<select value={category} onChange={(event) => setParam('category', event.target.value)} className="mt-1 w-full rounded-lg border border-border bg-background p-2.5"><option value="">All categories</option>{categories.map((value) => <option key={value} value={value}>{categoryLabels[value]?.title ?? value}</option>)}</select></label><label className="text-sm">Accepted input<select value={input} onChange={(event) => setParam('input', event.target.value)} className="mt-1 w-full rounded-lg border border-border bg-background p-2.5"><option value="">Any input</option>{inputTypes.map((value) => <option key={value} value={value}>{mediaLabel(value)} ({value})</option>)}</select></label><label className="text-sm">Produced output<select value={output} onChange={(event) => setParam('output', event.target.value)} className="mt-1 w-full rounded-lg border border-border bg-background p-2.5"><option value="">Any output</option>{outputTypes.map((value) => <option key={value} value={value}>{mediaLabel(value)} ({value})</option>)}</select></label><label className="text-sm">Sort by<select value={sort} onChange={(event) => setParam('sort', event.target.value === 'name' ? '' : event.target.value)} className="mt-1 w-full rounded-lg border border-border bg-background p-2.5"><option value="name">Name</option><option value="category">Category</option><option value="input">Input file type</option><option value="newest">Newest release</option></select></label></div>
      <div className="flex flex-wrap items-center gap-2"><p role="status" className="mr-auto text-sm text-muted-foreground">{filtered.length} of {products.length} processors</p>{activeFilters.map(([name, value]) => <button key={name} type="button" onClick={() => setParam(name, '')} className="rounded-full border border-border bg-background px-3 py-1 text-xs hover:border-primary">{name}: {name === 'input' || name === 'output' ? mediaLabel(value) : value} ×</button>)}{(activeFilters.length > 0 || sort !== 'name') && <Button type="button" variant="ghost" onClick={reset}>Reset filters</Button>}</div>
    </section>
    {feedback && <p role="status" className="rounded-xl border border-border bg-background p-3 text-sm">{feedback}</p>}
    {(session.isLoading || catalogQuery.isLoading) && <p className="text-muted-foreground">Loading processor catalog…</p>}
    {(session.isError || catalogQuery.isError) && <p role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-sm">The project processor catalog could not be loaded. Try again once the API is available.</p>}
    {!projectId && !session.isLoading && !session.isError && <p role="alert" className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">Select a project before managing processor releases.</p>}
    {projectId && catalogQuery.isSuccess && products.length === 0 && <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">No processor releases are published yet.</p>}
    {products.length > 0 && filtered.length === 0 && <div className="rounded-2xl border border-border bg-card p-8 text-center"><h2 className="text-xl font-semibold">No processors match these filters</h2><p className="mt-2 text-sm text-muted-foreground">Clear one or more filters to return to the full project catalog.</p><Button type="button" className="mt-4" onClick={reset}>Reset filters</Button></div>}
    {[...new Set(filtered.map((product) => product.recommended.processor.category))].map((group) => <section key={group} aria-labelledby={`category-${group}`} className="space-y-4"><div><h2 id={`category-${group}`} className="text-xl font-semibold">{categoryLabels[group]?.title ?? group}</h2><p className="text-sm text-muted-foreground">{categoryLabels[group]?.detail}</p></div>{filtered.filter((product) => product.recommended.processor.category === group).map((product) => <ProcessorCard key={product.recommended.processor.slug} product={product} projectId={projectId} pendingKey={pendingKey} confirmingKey={confirmingKey} onEnable={(release) => mutation.mutate({ release, enable: true })} onAskDisable={(release) => setConfirmingKey(releaseKey(release))} onCancelDisable={() => setConfirmingKey(null)} onDisable={(release) => mutation.mutate({ release, enable: false })} onCopy={copy} />)}</section>)}
  </div>;
}
