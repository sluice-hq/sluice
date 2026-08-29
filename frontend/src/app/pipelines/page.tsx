'use client';
/* eslint-disable react-hooks/set-state-in-effect -- Query results hydrate the local draft editor. */

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listProjectProcessors, type ProcessorContract } from '@/api/processors';
import {
  createPipeline, getPipeline, getPipelineHistory, listPipelines, publishPipeline, saveDraft,
  validatePipeline, type PipelineDefinition, type ValidationReport,
} from '@/api/pipelines';

const emptyDefinition: PipelineDefinition = {
  schemaVersion: '1', slug: 'my-pipeline',
  input: { kind: 'image', mimeTypes: ['image/jpeg', 'image/png'], maxBytes: 50000000, maxPixels: 40000000 },
  steps: [],
  limits: { maxSteps: 10, timeoutSeconds: 90, maxOutputBytes: 50000000 },
};

export default function PipelinesPage() {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const [selected, setSelected] = useState('');
  const [name, setName] = useState('My pipeline');
  const [description, setDescription] = useState('');
  const [mode, setMode] = useState<'form' | 'json'>('form');
  const [definition, setDefinition] = useState<PipelineDefinition>(emptyDefinition);
  const [json, setJson] = useState(JSON.stringify(emptyDefinition, null, 2));
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const hydratedPipeline = useRef<string | null>(null);
  const appliedMarketRelease = useRef('');
  const { data: pipelineList = [] } = useQuery({ queryKey: ['pipelines'], queryFn: listPipelines });
  const { data: session } = useQuery<{ selectedProjectId?: string }>({ queryKey: ['session'], queryFn: async () => { const response = await fetch('/api/session'); if (!response.ok) throw new Error('Session unavailable'); return response.json(); } });
  const projectId = session?.selectedProjectId ?? '';
  const projectReleasesQuery = useQuery({ queryKey: ['project-processor-releases', projectId], queryFn: () => listProjectProcessors(projectId), enabled: !!projectId });
  const projectReleases = useMemo(() => projectReleasesQuery.data ?? [], [projectReleasesQuery.data]);
  const processors = useMemo(() => projectReleases.map((item) => item.processor), [projectReleases]);
  const enabledProcessors = useMemo(() => projectReleases.filter((item) => item.enabled).map((item) => item.processor), [projectReleases]);
  const projectReleasesReady = !!projectId && projectReleasesQuery.isSuccess;
  const { data: detail } = useQuery({ queryKey: ['pipeline', selected], queryFn: () => getPipeline(selected), enabled: !!selected });
  const { data: history = [] } = useQuery({ queryKey: ['pipeline-history', selected], queryFn: () => getPipelineHistory(selected), enabled: !!selected });

  useEffect(() => {
    if (!detail || hydratedPipeline.current === selected) return;
    const editable = detail.draft ?? history.find((version) => version.status === 'PUBLISHED');
    if (!editable) return;
    setDefinition(editable.definition);
    setJson(JSON.stringify(editable.definition, null, 2));
    setReport(editable.validation);
    setName(detail.pipeline.name);
    setDescription(detail.pipeline.description ?? '');
    setDirty(false);
    hydratedPipeline.current = selected;
  }, [detail, history, selected]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const processorMap = useMemo(() => new Map(processors.map((item) => [`${item.slug}@${item.version}`, item])), [processors]);
  const enabledKeys = useMemo(() => new Set(enabledProcessors.map((item) => `${item.slug}@${item.version}`)), [enabledProcessors]);
  const unavailableSteps = projectReleasesReady ? definition.steps.filter((step) => !enabledKeys.has(`${step.processor}@${step.version}`)) : [];

  useEffect(() => {
    const slug = searchParams.get('processor') ?? '';
    const version = searchParams.get('version') ?? '';
    const key = `${slug}@${version}`;
    if (!slug || !version || appliedMarketRelease.current === key || !enabledKeys.has(key)) return;
    const processor = processorMap.get(key);
    if (!processor) return;
    const id = `${slug.replaceAll('.', '-')}-${definition.steps.length + 1}`;
    update({ ...definition, steps: [...definition.steps, { id, processor: slug, version, config: configDefaults(processor) }] });
    setMessage(`${processor.displayName} v${version} added from the Processor Market.`);
    appliedMarketRelease.current = key;
  }, [definition, enabledKeys, processorMap, searchParams]);

  function update(next: PipelineDefinition) { setDefinition(next); setJson(JSON.stringify(next, null, 2)); setReport(null); setDirty(true); }
  function updateStep(index: number, patch: Record<string, unknown>) {
    update({ ...definition, steps: definition.steps.map((step, i) => i === index ? { ...step, ...patch } : step) });
  }
  function selectPipeline(next: string) {
    if (next === selected) return;
    if (dirty && !window.confirm('Discard your unsaved pipeline changes?')) return;
    setConfirmPublish(false); setMessage(''); setReport(null); setSelected(next);
    hydratedPipeline.current = null;
    if (!next) {
      setDefinition(emptyDefinition); setJson(JSON.stringify(emptyDefinition, null, 2));
      setName('My pipeline'); setDescription(''); setDirty(false);
    }
  }
  function switchMode(next: 'form' | 'json') {
    if (next === 'form') {
      try { const parsed = JSON.parse(json) as PipelineDefinition; setDefinition(parsed); setMessage(''); }
      catch { setMessage('Fix the JSON syntax before opening Form view.'); return; }
    }
    setMode(next);
  }

  async function act(action: 'save' | 'validate' | 'publish') {
    setBusy(true); setMessage('');
    try {
      const current = mode === 'json' ? JSON.parse(json) as PipelineDefinition : definition;
      setDefinition(current);
      if (action === 'save') {
        if (!selected) {
          const created = await createPipeline(name, description, current); setSelected(created.pipeline.slug);
        } else {
          await saveDraft(selected, detail?.draft?.revision ?? 0, current);
        }
        setDirty(false);
        setMessage('Draft saved.');
      } else if (action === 'validate') {
        if (!selected) throw new Error('Save the pipeline before validating it.');
        const result = await validatePipeline(selected, current); setReport(result);
        setMessage(result.valid ? 'Pipeline is valid and ready to publish.' : 'Validation found issues.');
      } else {
        if (!selected || !detail?.draft) throw new Error('Save a draft before publishing.');
        await publishPipeline(selected, detail.draft.revision); setMessage('Published an immutable version. The first publish becomes stable.');
        setConfirmPublish(false); setDirty(false);
      }
      await queryClient.invalidateQueries({ queryKey: ['pipelines'] });
      await queryClient.invalidateQueries({ queryKey: ['pipeline', selected] });
      await queryClient.invalidateQueries({ queryKey: ['pipeline-history', selected] });
    } catch (error) { setMessage(error instanceof Error ? error.message : 'The operation failed.'); }
    finally { setBusy(false); }
  }

  return <div className="max-w-7xl space-y-6">
    <header><p className="text-sm font-medium text-primary">Pipelines</p><h1 className="text-2xl font-bold">Canonical pipeline authoring</h1>
      <p className="mt-2 text-sm text-muted-foreground">Form and JSON edit the same definition. Publishing pins exact processor versions.</p></header>
    <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
      <aside className="space-y-3 rounded-xl border border-border bg-card p-4">
        <button onClick={() => selectPipeline('')} className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium">New pipeline</button>
        {pipelineList.map((item) => <button key={item.id} onClick={() => selectPipeline(item.slug)} className={`w-full rounded-md border p-3 text-left text-sm ${selected === item.slug ? 'border-primary bg-primary/10' : 'border-border'}`}>
          <span className="block font-medium">{item.name}</span><span className="text-xs text-muted-foreground">{item.slug} · stable {item.stableVersion ?? '—'}</span>
        </button>)}
      </aside>
      <section className="space-y-5 rounded-xl border border-border bg-card p-5">
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Name<input value={name} onChange={(e) => { setName(e.target.value); setDirty(true); }} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
          <label className="text-sm">Description<input value={description} onChange={(e) => { setDescription(e.target.value); setDirty(true); }} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label></div>
        <div className="flex gap-2"><button onClick={() => switchMode('form')} className={`rounded-md px-3 py-1.5 text-sm ${mode === 'form' ? 'bg-primary' : 'bg-background'}`}>Form</button><button onClick={() => switchMode('json')} className={`rounded-md px-3 py-1.5 text-sm ${mode === 'json' ? 'bg-primary' : 'bg-background'}`}>JSON</button></div>
        {mode === 'json' ? <textarea aria-label="Canonical pipeline JSON" value={json} onChange={(e) => { setJson(e.target.value); setReport(null); setDirty(true); }} className="min-h-[520px] w-full rounded-md border border-border bg-background p-4 font-mono text-sm" />
          : <FormEditor definition={definition} processors={enabledProcessors} processorMap={processorMap} releasesReady={projectReleasesReady} update={update} updateStep={updateStep} />}
        {!!projectId && projectReleasesQuery.isLoading && <p role="status" className="rounded-md border border-border bg-background p-3 text-sm text-muted-foreground">Loading enabled processor releases…</p>}
        {!!projectId && projectReleasesQuery.isError && <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm">Enabled processor releases could not be loaded. Existing draft step availability cannot be checked until the catalog is available.</p>}
        {unavailableSteps.length > 0 && <p role="alert" className="rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm">{unavailableSteps.length} step release(s) are not enabled for this project. Existing published pipelines remain valid, but this draft cannot be published until those exact releases are enabled or replaced. <Link href="/processors" className="font-medium text-primary hover:underline">Open Processor Market</Link></p>}
        {report && <div className={`rounded-md border p-3 text-sm ${report.valid ? 'border-emerald-500/40' : 'border-destructive/40'}`}><p className="font-medium">{report.valid ? 'Valid pipeline' : `${report.errors.length} validation issue(s)`}</p>
          {report.errors.map((error) => <p key={`${error.path}-${error.code}`} className="mt-1 font-mono text-xs">{error.path}: {error.message}</p>)}</div>}
        {message && <p role="status" className="rounded-md border border-border bg-background p-3 text-sm">{message}</p>}
        {dirty && <p className="text-xs text-amber-300">Unsaved changes. Save and validate before publishing.</p>}
        {confirmPublish && <div role="alertdialog" aria-label="Confirm pipeline publication" className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4 text-sm"><p className="font-medium">Publish an immutable pipeline version?</p><p className="mt-1 text-muted-foreground">Published definitions cannot be edited. Save any final changes first.</p><div className="mt-3 flex gap-2"><button disabled={busy} onClick={() => act('publish')} className="rounded-md bg-emerald-600 px-3 py-2 font-medium text-white">Confirm publish</button><button disabled={busy} onClick={() => setConfirmPublish(false)} className="rounded-md border border-border px-3 py-2">Cancel</button></div></div>}
        <div className="flex flex-wrap gap-2"><button disabled={busy} onClick={() => act('save')} className="rounded-md bg-primary px-4 py-2 text-sm font-medium">Save draft</button><button disabled={busy || !selected} onClick={() => act('validate')} className="rounded-md border border-border px-4 py-2 text-sm">Validate</button><button disabled={busy || !detail?.draft || dirty} onClick={() => setConfirmPublish(true)} className="rounded-md border border-emerald-500/50 px-4 py-2 text-sm">Publish immutable version</button></div>
        {!!selected && <div><h2 className="font-semibold">Version history</h2><div className="mt-2 flex flex-wrap gap-2">{history.map((item) => <span key={item.id} className="rounded border border-border px-2 py-1 text-xs">v{item.versionNumber} {item.status}{detail?.pipeline.stableVersion === item.versionNumber ? ' · stable' : ''}</span>)}</div></div>}
      </section>
    </div>
  </div>;
}

function FormEditor({ definition, processors, processorMap, releasesReady, update, updateStep }: {
  definition: PipelineDefinition; processors: ProcessorContract[]; processorMap: Map<string, ProcessorContract>;
  releasesReady: boolean;
  update: (next: PipelineDefinition) => void; updateStep: (index: number, patch: Record<string, unknown>) => void;
}) {
  const releasesBySlug = new Map<string, ProcessorContract[]>();
  processors.forEach((item) => releasesBySlug.set(item.slug, [...(releasesBySlug.get(item.slug) ?? []), item]));
  const canUseTemplate = (slugs: string[]) => slugs.every((slug) => releasesBySlug.has(slug));
  const precedingOutput = definition.steps.length === 0
    ? definition.input.mimeTypes
    : propagatedOutputMimeTypes(definition.steps[definition.steps.length - 1], definition.input.mimeTypes, processorMap);
  const nextProcessor = processors.find((candidate) => precedingOutput.some((mime) => candidate.input.mimeTypes.some((accepted) => mediaTypeMatches(mime, accepted))));
  return <div className="space-y-4">
    <label className="block text-sm">Pipeline slug<input value={definition.slug} disabled className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    {processors.length > 0 ? <div className="rounded-lg border border-border bg-background/40 p-4"><p className="text-sm font-medium">Starter templates</p><p className="mt-1 text-xs text-muted-foreground">Templates only use processor releases enabled for this project.</p><div className="mt-3 flex flex-wrap gap-2"><button type="button" disabled={!canUseTemplate(['mime-validation', 'webp'])} onClick={() => applyTemplate(['mime-validation', 'webp'], releasesBySlug, definition, update)} className="cursor-pointer rounded border border-primary/40 px-3 py-1.5 text-sm text-primary transition hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40">WebP delivery</button><button type="button" disabled={!canUseTemplate(['mime-validation', 'resize', 'webp'])} onClick={() => applyTemplate(['mime-validation', 'resize', 'webp'], releasesBySlug, definition, update)} className="cursor-pointer rounded border border-primary/40 px-3 py-1.5 text-sm text-primary transition hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40">Resize + WebP</button></div></div>
      : releasesReady ? <div className="rounded-xl border border-dashed border-primary/40 bg-primary/5 p-6 text-center"><h2 className="font-semibold">Enable a processor to start building</h2><p className="mt-2 text-sm text-muted-foreground">This project has no processor releases enabled yet. Choose only the capabilities this project needs.</p><Link href="/processors" className="mt-4 inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/80">Browse Processor Market</Link></div>
        : null}
    <label className="block text-sm">Input MIME types<input value={definition.input.mimeTypes.join(', ')} onChange={(e) => update({ ...definition, input: { ...definition.input, mimeTypes: e.target.value.split(',').map((v) => v.trim()).filter(Boolean) } })} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    {definition.steps.map((step, index) => {
      const selected = processorMap.get(`${step.processor}@${step.version}`);
      const properties = (selected?.configSchema.properties ?? {}) as Record<string, SchemaControl>;
      const previous = index === 0 ? definition.input.mimeTypes : propagatedOutputMimeTypes(
        definition.steps[index - 1], definition.input.mimeTypes, processorMap);
      const compatible = !selected || previous.some((actual) => selected.input.mimeTypes.some((accepted) => mediaTypeMatches(actual, accepted)));
      return <div key={`${step.id}-${index}`} className="space-y-3 rounded-lg border border-border bg-background/40 p-4">
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Step id<input value={step.id} onChange={(e) => updateStep(index, { id: e.target.value })} className="mt-1 w-full rounded border border-border bg-background p-2" /></label><ProcessorPicker pickerId={`step-${index}`} processors={processors} selected={selected} acceptedInputs={previous} onSelect={(processor) => updateStep(index, { processor: processor.slug, version: processor.version, config: configDefaults(processor) })} /></div>
        {selected && <div className="rounded-lg border border-border bg-background/45 p-3 text-xs leading-5"><div className="flex flex-wrap items-center gap-2"><span className="font-semibold text-sm">{selected.displayName} · v{selected.version}</span><span className="rounded-full border border-border px-2 py-0.5 capitalize text-muted-foreground">{selected.status.toLowerCase()}</span></div><p className="mt-1 text-muted-foreground">{selected.description}</p><p className="mt-1 text-muted-foreground">Accepts {selected.input.mimeTypes.join(', ')} · produces {selected.output.mimeTypes.join(', ')}</p><a className="mt-1 inline-block text-primary underline-offset-2 hover:underline" href={`/processors#processor-${selected.slug}`}>View full processor details</a>{selected.status === 'DEPRECATED' && <p role="alert" className="mt-2 text-amber-300">This release is deprecated. Use it only when compatibility with an existing pipeline requires it.</p>}</div>}
        {!compatible && <p role="alert" className="rounded border border-destructive/40 bg-destructive/10 p-2 text-xs">This processor cannot accept the preceding output. Reorder the steps or choose a compatible release.</p>}
        {Object.entries(properties).map(([key, schema]) => <ConfigControl key={key} name={key} schema={schema} value={step.config[key]} onChange={(value) => updateStep(index, { config: { ...step.config, [key]: value } })} />)}
        <div className="flex gap-3"><button disabled={index === 0} onClick={() => reorder(definition, index, index - 1, update)} className="text-xs disabled:text-muted-foreground">Move up</button><button disabled={index === definition.steps.length - 1} onClick={() => reorder(definition, index, index + 1, update)} className="text-xs disabled:text-muted-foreground">Move down</button><button onClick={() => update({ ...definition, steps: definition.steps.filter((_, i) => i !== index) })} className="text-xs text-destructive">Remove step</button></div>
      </div>;
    })}
    <button disabled={!nextProcessor || definition.steps.length >= 10} onClick={() => { if (nextProcessor) update({ ...definition, steps: [...definition.steps, { id: `step-${definition.steps.length + 1}`, processor: nextProcessor.slug, version: nextProcessor.version, config: configDefaults(nextProcessor) }] }); }} className="cursor-pointer rounded-md border border-border px-3 py-2 text-sm transition hover:border-primary hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40">Add ordered step</button>
  </div>;
}

function ProcessorPicker({ pickerId, processors, selected, acceptedInputs, onSelect }: { pickerId: string; processors: ProcessorContract[]; selected?: ProcessorContract; acceptedInputs: string[]; onSelect: (processor: ProcessorContract) => void }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const results = processors.filter((processor) => `${processor.displayName} ${processor.slug} ${processor.version} ${processor.category} ${processor.description} ${processor.input.mimeTypes.join(' ')} ${processor.output.mimeTypes.join(' ')}`.toLowerCase().includes(query.toLowerCase()));
  const compatible = (processor: ProcessorContract) => acceptedInputs.some((mime) => processor.input.mimeTypes.some((accepted) => mediaTypeMatches(mime, accepted)));
  const groups = [...new Set(results.map((processor) => processor.category))].sort();
  const orderedResults = groups.flatMap((group) => results.filter((processor) => processor.category === group));
  const listId = `processor-options-${pickerId}`;
  function choose(processor: ProcessorContract) { if (!compatible(processor)) return; onSelect(processor); setOpen(false); setQuery(''); }
  return <div className="relative text-sm"><span>Exact processor release</span><button type="button" aria-label="Select exact processor release" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined} onClick={() => setOpen((value) => !value)} className="mt-1 flex w-full cursor-pointer items-center justify-between rounded border border-border bg-background p-2 text-left hover:border-primary"><span>{selected ? `${selected.displayName} · v${selected.version}` : 'Choose a processor'}</span><span aria-hidden="true">⌄</span></button>{open && <div className="absolute z-20 mt-1 w-full min-w-80 rounded-lg border border-border bg-card p-2 shadow-xl"><input autoFocus role="combobox" aria-label="Search enabled processor releases" aria-autocomplete="list" aria-expanded="true" aria-controls={listId} aria-activedescendant={orderedResults[highlighted] ? `${listId}-${highlighted}` : undefined} value={query} onChange={(event) => { setQuery(event.target.value); setHighlighted(0); }} onKeyDown={(event) => { if (event.key === 'Escape') setOpen(false); if (event.key === 'ArrowDown') { event.preventDefault(); setHighlighted((value) => Math.min(value + 1, orderedResults.length - 1)); } if (event.key === 'ArrowUp') { event.preventDefault(); setHighlighted((value) => Math.max(value - 1, 0)); } if (event.key === 'Enter' && orderedResults[highlighted]) { event.preventDefault(); choose(orderedResults[highlighted]); } }} placeholder="Search name, category or file type" className="w-full rounded border border-border bg-background p-2" /><div id={listId} role="listbox" aria-label="Enabled processor releases" className="mt-2 max-h-72 overflow-y-auto">{groups.map((group) => <div key={group} role="group" aria-label={group}><p className="px-2 py-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{group}</p>{orderedResults.map((processor, index) => processor.category === group && <button id={`${listId}-${index}`} role="option" aria-selected={`${processor.slug}@${processor.version}` === `${selected?.slug}@${selected?.version}`} aria-disabled={!compatible(processor)} key={`${processor.slug}@${processor.version}`} type="button" disabled={!compatible(processor)} onClick={() => choose(processor)} className={`block w-full cursor-pointer rounded px-2 py-2 text-left text-sm hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-40 ${index === highlighted ? 'bg-primary/10' : ''}`}><span className="block font-medium">{processor.displayName} · v{processor.version}</span><span className="block text-xs text-muted-foreground">{processor.input.mimeTypes.join(', ')} → {processor.output.mimeTypes.join(', ')}</span></button>)}</div>)}{orderedResults.length === 0 && <p className="p-3 text-sm text-muted-foreground">No enabled releases match this search.</p>}</div><Link href="/processors" className="mt-2 block border-t border-border px-2 pt-2 text-xs font-medium text-primary hover:underline">Manage processors in the market</Link></div>}</div>;
}

function applyTemplate(slugs: string[], releases: Map<string, ProcessorContract[]>, definition: PipelineDefinition, update: (next: PipelineDefinition) => void) {
  const steps = slugs.map((slug, index) => {
    const release = releases.get(slug)?.[0];
    return { id: `step-${index + 1}`, processor: slug, version: release?.version ?? '', config: release ? configDefaults(release) : {} };
  });
  if (steps.every((step) => step.version)) update({ ...definition, steps });
}

function configDefaults(processor: ProcessorContract) {
  return Object.fromEntries(Object.entries((processor.configSchema.properties ?? {}) as Record<string, { default?: unknown }>)
    .filter(([, schema]) => schema.default !== undefined).map(([key, schema]) => [key, schema.default]));
}

type SchemaControl = { type?: string; enum?: unknown[]; minimum?: number; maximum?: number; default?: unknown };
function reorder(definition: PipelineDefinition, from: number, to: number, update: (next: PipelineDefinition) => void) { const steps = [...definition.steps]; [steps[from], steps[to]] = [steps[to], steps[from]]; update({ ...definition, steps }); }
function mediaTypeMatches(actualMimeType: string, acceptedMimeType: string) {
  const actual = actualMimeType.trim().toLowerCase();
  const accepted = acceptedMimeType.trim().toLowerCase();
  if (!actual || !accepted) return false;
  if (actual === '*/*' || accepted === '*/*') return true;
  if (accepted.endsWith('/*')) return actual.startsWith(accepted.slice(0, -1));
  return actual === accepted;
}
function propagatedOutputMimeTypes(step: PipelineDefinition['steps'][number], inputMimeTypes: string[], processorMap: Map<string, ProcessorContract>) {
  const output = processorMap.get(`${step.processor}@${step.version}`)?.output.mimeTypes ?? [];
  return output.length > 0 && output.every((mimeType) => mimeType.includes('*')) ? inputMimeTypes : output;
}
function ConfigControl({ name, schema, value, onChange }: { name: string; schema: SchemaControl; value: unknown; onChange: (value: unknown) => void }) {
  if (schema.type === 'boolean') return <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={Boolean(value ?? schema.default)} onChange={(event) => onChange(event.target.checked)} />{name}</label>;
  if (schema.enum) return <label className="block text-sm">{name}<select value={String(value ?? schema.default ?? '')} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded border border-border bg-background p-2">{schema.enum.map((item) => <option key={String(item)} value={String(item)}>{String(item)}</option>)}</select></label>;
  if (schema.type === 'array') {
    const items = Array.isArray(value) ? value : Array.isArray(schema.default) ? schema.default : [];
    return <label className="block text-sm">{name}<input value={items.join(', ')} onChange={(event) => onChange(event.target.value.split(',').map((item) => item.trim()).filter(Boolean))} placeholder="Comma-separated values" className="mt-1 w-full rounded border border-border bg-background p-2" /><span className="mt-1 block text-xs text-muted-foreground">Enter one or more comma-separated values.</span></label>;
  }
  const numeric = schema.type === 'integer' || schema.type === 'number';
  if (numeric && schema.minimum !== undefined && schema.maximum !== undefined) {
    const current = Number(value ?? schema.default ?? schema.minimum);
    return <fieldset className="text-sm"><legend>{name}</legend><div className="mt-1 grid grid-cols-[1fr_6rem] items-center gap-3"><input aria-label={`${name} slider`} type="range" min={schema.minimum} max={schema.maximum} value={current} onChange={(event) => onChange(Number(event.target.value))} /><input aria-label={`${name} number`} type="number" min={schema.minimum} max={schema.maximum} value={current} onChange={(event) => onChange(Number(event.target.value))} className="w-full rounded border border-border bg-background p-2" /></div><span className="mt-1 block text-xs text-muted-foreground">{schema.minimum} minimum to {schema.maximum} maximum</span></fieldset>;
  }
  return <label className="block text-sm">{name}<input type={numeric ? 'number' : 'text'} min={schema.minimum} max={schema.maximum} value={String(value ?? schema.default ?? '')} onChange={(event) => onChange(numeric ? Number(event.target.value) : event.target.value)} className="mt-1 w-full rounded border border-border bg-background p-2" />{numeric && <span className="mt-1 block text-xs text-muted-foreground">{schema.minimum ?? 'No'} minimum · {schema.maximum ?? 'No'} maximum</span>}</label>;
}
