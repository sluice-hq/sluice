'use client';
/* eslint-disable react-hooks/set-state-in-effect -- Query results hydrate the local draft editor. */

import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listProcessors, type ProcessorContract } from '@/api/processors';
import {
  createPipeline, getPipeline, getPipelineHistory, listPipelines, publishPipeline, saveDraft,
  validatePipeline, type PipelineDefinition, type ValidationReport,
} from '@/api/pipelines';

const emptyDefinition: PipelineDefinition = {
  schemaVersion: '1', slug: 'my-pipeline',
  input: { kind: 'image', mimeTypes: ['image/jpeg', 'image/png'], maxBytes: 50000000, maxPixels: 40000000 },
  steps: [{ id: 'validate', processor: 'mime-validation', version: '1.0.0', config: { allowedTypes: ['image/jpeg', 'image/png'] } }],
  limits: { maxSteps: 10, timeoutSeconds: 90, maxOutputBytes: 50000000 },
};

export default function PipelinesPage() {
  const queryClient = useQueryClient();
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
  const { data: pipelineList = [] } = useQuery({ queryKey: ['pipelines'], queryFn: listPipelines });
  const { data: processors = [] } = useQuery({ queryKey: ['processors'], queryFn: listProcessors });
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
          : <FormEditor definition={definition} processors={processors} processorMap={processorMap} update={update} updateStep={updateStep} />}
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

function FormEditor({ definition, processors, processorMap, update, updateStep }: {
  definition: PipelineDefinition; processors: ProcessorContract[]; processorMap: Map<string, ProcessorContract>;
  update: (next: PipelineDefinition) => void; updateStep: (index: number, patch: Record<string, unknown>) => void;
}) {
  const [processorQuery, setProcessorQuery] = useState('');
  const published = processors.filter((item) => item.status === 'PUBLISHED');
  const releasesBySlug = new Map<string, ProcessorContract[]>();
  published.forEach((item) => releasesBySlug.set(item.slug, [...(releasesBySlug.get(item.slug) ?? []), item]));
  return <div className="space-y-4">
    <label className="block text-sm">Pipeline slug<input value={definition.slug} disabled className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    <div className="rounded-lg border border-border bg-background/40 p-4"><p className="text-sm font-medium">Starter templates</p><div className="mt-2 flex flex-wrap gap-2"><button type="button" onClick={() => applyTemplate(['mime-validation', 'webp'], releasesBySlug, definition, update)} className="rounded border border-primary/40 px-3 py-1.5 text-sm text-primary">WebP delivery</button><button type="button" onClick={() => applyTemplate(['mime-validation', 'resize', 'webp'], releasesBySlug, definition, update)} className="rounded border border-primary/40 px-3 py-1.5 text-sm text-primary">Resize + WebP</button></div></div>
    <label className="block text-sm">Input MIME types<input value={definition.input.mimeTypes.join(', ')} onChange={(e) => update({ ...definition, input: { ...definition.input, mimeTypes: e.target.value.split(',').map((v) => v.trim()).filter(Boolean) } })} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    {definition.steps.map((step, index) => {
      const selected = processorMap.get(`${step.processor}@${step.version}`);
      const properties = (selected?.configSchema.properties ?? {}) as Record<string, SchemaControl>;
      const previous = index === 0 ? definition.input.mimeTypes : propagatedOutputMimeTypes(
        definition.steps[index - 1], definition.input.mimeTypes, processorMap);
      const compatible = !selected || previous.some((actual) => selected.input.mimeTypes.some((accepted) => mediaTypeMatches(actual, accepted)));
      const choices = [...releasesBySlug.values()].filter((releases) => `${releases[0].displayName} ${releases[0].slug} ${releases[0].category}`.toLowerCase().includes(processorQuery.toLowerCase()));
      const releaseChoices = processors.filter((item) => item.status === 'PUBLISHED' || item.status === 'DEPRECATED');
      return <div key={`${step.id}-${index}`} className="space-y-3 rounded-lg border border-border bg-background/40 p-4">
        <label className="block text-sm">Find a processor<input value={processorQuery} onChange={(event) => setProcessorQuery(event.target.value)} placeholder="Search Transform, Optimize, Privacy…" className="mt-1 w-full rounded border border-border bg-background p-2" /></label>
        <div className="flex flex-wrap gap-2">{choices.map((releases) => <button key={releases[0].slug} type="button" onClick={() => updateStep(index, { processor: releases[0].slug, version: releases[0].version, config: configDefaults(releases[0]) })} className={`rounded border px-2 py-1 text-xs ${step.processor === releases[0].slug ? 'border-primary bg-primary/10 text-primary' : 'border-border'}`}>{releases[0].category} · {releases[0].displayName}</button>)}</div>
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Step id<input value={step.id} onChange={(e) => updateStep(index, { id: e.target.value })} className="mt-1 w-full rounded border border-border bg-background p-2" /></label>
          <label className="text-sm">Exact processor release<select value={`${step.processor}@${step.version}`} onChange={(e) => { const [processor, version] = e.target.value.split('@'); updateStep(index, { processor, version, config: {} }); }} className="mt-1 w-full rounded border border-border bg-background p-2">
            {releaseChoices.map((item) => <option key={`${item.slug}@${item.version}`} value={`${item.slug}@${item.version}`}>{item.displayName} · {item.version}{item.status !== 'PUBLISHED' ? ' (deprecated)' : ''}</option>)}</select></label></div>
        {selected && <div className="rounded-lg border border-border bg-background/45 p-3 text-xs leading-5"><div className="flex flex-wrap items-center gap-2"><span className="font-semibold text-sm">{selected.displayName} · v{selected.version}</span><span className="rounded-full border border-border px-2 py-0.5 capitalize text-muted-foreground">{selected.status.toLowerCase()}</span></div><p className="mt-1 text-muted-foreground">{selected.description}</p><p className="mt-1 text-muted-foreground">Accepts {selected.input.mimeTypes.join(', ')} · produces {selected.output.mimeTypes.join(', ')}</p><a className="mt-1 inline-block text-primary underline-offset-2 hover:underline" href={`/processors#processor-${selected.slug}`}>View full processor details</a>{selected.status === 'DEPRECATED' && <p role="alert" className="mt-2 text-amber-300">This release is deprecated. Use it only when compatibility with an existing pipeline requires it.</p>}</div>}
        {!compatible && <p role="alert" className="rounded border border-destructive/40 bg-destructive/10 p-2 text-xs">This processor cannot accept the preceding output. Reorder the steps or choose a compatible release.</p>}
        {Object.entries(properties).map(([key, schema]) => <ConfigControl key={key} name={key} schema={schema} value={step.config[key]} onChange={(value) => updateStep(index, { config: { ...step.config, [key]: value } })} />)}
        <div className="flex gap-3"><button disabled={index === 0} onClick={() => reorder(definition, index, index - 1, update)} className="text-xs disabled:text-muted-foreground">Move up</button><button disabled={index === definition.steps.length - 1} onClick={() => reorder(definition, index, index + 1, update)} className="text-xs disabled:text-muted-foreground">Move down</button><button onClick={() => update({ ...definition, steps: definition.steps.filter((_, i) => i !== index) })} className="text-xs text-destructive">Remove step</button></div>
      </div>;
    })}
    <button disabled={!processors.length || definition.steps.length >= 10} onClick={() => { const item = processors[0]; if (item) update({ ...definition, steps: [...definition.steps, { id: `step-${definition.steps.length + 1}`, processor: item.slug, version: item.version, config: {} }] }); }} className="rounded-md border border-border px-3 py-2 text-sm">Add ordered step</button>
  </div>;
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
