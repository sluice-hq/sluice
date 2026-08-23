'use client';
/* eslint-disable react-hooks/set-state-in-effect -- Query results hydrate the local draft editor. */

import { useEffect, useMemo, useState } from 'react';
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
  const { data: pipelineList = [] } = useQuery({ queryKey: ['pipelines'], queryFn: listPipelines });
  const { data: processors = [] } = useQuery({ queryKey: ['processors'], queryFn: listProcessors });
  const { data: detail } = useQuery({ queryKey: ['pipeline', selected], queryFn: () => getPipeline(selected), enabled: !!selected });
  const { data: history = [] } = useQuery({ queryKey: ['pipeline-history', selected], queryFn: () => getPipelineHistory(selected), enabled: !!selected });

  useEffect(() => {
    if (detail) {
      const editable = detail.draft ?? history.find((version) => version.status === 'PUBLISHED');
      if (editable) {
        setDefinition(editable.definition);
        setJson(JSON.stringify(editable.definition, null, 2));
        setReport(editable.validation);
      }
      setName(detail.pipeline.name);
      setDescription(detail.pipeline.description ?? '');
    }
  }, [detail, history]);

  const processorMap = useMemo(() => new Map(processors.map((item) => [`${item.slug}@${item.version}`, item])), [processors]);

  function update(next: PipelineDefinition) { setDefinition(next); setJson(JSON.stringify(next, null, 2)); setReport(null); }
  function updateStep(index: number, patch: Record<string, unknown>) {
    update({ ...definition, steps: definition.steps.map((step, i) => i === index ? { ...step, ...patch } : step) });
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
        setMessage('Draft saved.');
      } else if (action === 'validate') {
        if (!selected) throw new Error('Save the pipeline before validating it.');
        const result = await validatePipeline(selected, current); setReport(result);
        setMessage(result.valid ? 'Pipeline is valid and ready to publish.' : 'Validation found issues.');
      } else {
        if (!selected || !detail?.draft) throw new Error('Save a draft before publishing.');
        await publishPipeline(selected, detail.draft.revision); setMessage('Published an immutable version. The first publish becomes stable.');
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
        <button onClick={() => { setSelected(''); update(emptyDefinition); setName('My pipeline'); }} className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium">New pipeline</button>
        {pipelineList.map((item) => <button key={item.id} onClick={() => setSelected(item.slug)} className={`w-full rounded-md border p-3 text-left text-sm ${selected === item.slug ? 'border-primary bg-primary/10' : 'border-border'}`}>
          <span className="block font-medium">{item.name}</span><span className="text-xs text-muted-foreground">{item.slug} · stable {item.stableVersion ?? '—'}</span>
        </button>)}
      </aside>
      <section className="space-y-5 rounded-xl border border-border bg-card p-5">
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Name<input value={name} onChange={(e) => setName(e.target.value)} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
          <label className="text-sm">Description<input value={description} onChange={(e) => setDescription(e.target.value)} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label></div>
        <div className="flex gap-2"><button onClick={() => switchMode('form')} className={`rounded-md px-3 py-1.5 text-sm ${mode === 'form' ? 'bg-primary' : 'bg-background'}`}>Form</button><button onClick={() => switchMode('json')} className={`rounded-md px-3 py-1.5 text-sm ${mode === 'json' ? 'bg-primary' : 'bg-background'}`}>JSON</button></div>
        {mode === 'json' ? <textarea aria-label="Canonical pipeline JSON" value={json} onChange={(e) => { setJson(e.target.value); setReport(null); }} className="min-h-[520px] w-full rounded-md border border-border bg-background p-4 font-mono text-sm" />
          : <FormEditor definition={definition} processors={processors} processorMap={processorMap} update={update} updateStep={updateStep} />}
        {report && <div className={`rounded-md border p-3 text-sm ${report.valid ? 'border-emerald-500/40' : 'border-destructive/40'}`}><p className="font-medium">{report.valid ? 'Valid pipeline' : `${report.errors.length} validation issue(s)`}</p>
          {report.errors.map((error) => <p key={`${error.path}-${error.code}`} className="mt-1 font-mono text-xs">{error.path}: {error.message}</p>)}</div>}
        {message && <p role="status" className="rounded-md border border-border bg-background p-3 text-sm">{message}</p>}
        <div className="flex flex-wrap gap-2"><button disabled={busy} onClick={() => act('save')} className="rounded-md bg-primary px-4 py-2 text-sm font-medium">Save draft</button><button disabled={busy || !selected} onClick={() => act('validate')} className="rounded-md border border-border px-4 py-2 text-sm">Validate</button><button disabled={busy || !detail?.draft} onClick={() => act('publish')} className="rounded-md border border-emerald-500/50 px-4 py-2 text-sm">Publish immutable version</button></div>
        {!!selected && <div><h2 className="font-semibold">Version history</h2><div className="mt-2 flex flex-wrap gap-2">{history.map((item) => <span key={item.id} className="rounded border border-border px-2 py-1 text-xs">v{item.versionNumber} {item.status}{detail?.pipeline.stableVersion === item.versionNumber ? ' · stable' : ''}</span>)}</div></div>}
      </section>
    </div>
  </div>;
}

function FormEditor({ definition, processors, processorMap, update, updateStep }: {
  definition: PipelineDefinition; processors: ProcessorContract[]; processorMap: Map<string, ProcessorContract>;
  update: (next: PipelineDefinition) => void; updateStep: (index: number, patch: Record<string, unknown>) => void;
}) {
  return <div className="space-y-4">
    <label className="block text-sm">Pipeline slug<input value={definition.slug} disabled className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    <label className="block text-sm">Input MIME types<input value={definition.input.mimeTypes.join(', ')} onChange={(e) => update({ ...definition, input: { ...definition.input, mimeTypes: e.target.value.split(',').map((v) => v.trim()).filter(Boolean) } })} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    {definition.steps.map((step, index) => {
      const selected = processorMap.get(`${step.processor}@${step.version}`);
      const properties = (selected?.configSchema.properties ?? {}) as Record<string, { type?: string; minimum?: number; maximum?: number }>;
      return <div key={`${step.id}-${index}`} className="space-y-3 rounded-lg border border-border bg-background/40 p-4">
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Step id<input value={step.id} onChange={(e) => updateStep(index, { id: e.target.value })} className="mt-1 w-full rounded border border-border bg-background p-2" /></label>
          <label className="text-sm">Exact processor release<select value={`${step.processor}@${step.version}`} onChange={(e) => { const [processor, version] = e.target.value.split('@'); updateStep(index, { processor, version, config: {} }); }} className="mt-1 w-full rounded border border-border bg-background p-2">
            {processors.map((item) => <option key={`${item.slug}@${item.version}`} value={`${item.slug}@${item.version}`}>{item.displayName} · {item.version}</option>)}</select></label></div>
        {Object.entries(properties).map(([key, schema]) => <label key={key} className="block text-sm">{key}<input type={schema.type === 'integer' || schema.type === 'number' ? 'number' : 'text'} min={schema.minimum} max={schema.maximum} value={String(step.config[key] ?? '')} onChange={(e) => updateStep(index, { config: { ...step.config, [key]: schema.type === 'integer' || schema.type === 'number' ? Number(e.target.value) : e.target.value } })} className="mt-1 w-full rounded border border-border bg-background p-2" /></label>)}
        <button onClick={() => update({ ...definition, steps: definition.steps.filter((_, i) => i !== index) })} className="text-xs text-destructive">Remove step</button>
      </div>;
    })}
    <button disabled={!processors.length || definition.steps.length >= 10} onClick={() => { const item = processors[0]; if (item) update({ ...definition, steps: [...definition.steps, { id: `step-${definition.steps.length + 1}`, processor: item.slug, version: item.version, config: {} }] }); }} className="rounded-md border border-border px-3 py-2 text-sm">Add ordered step</button>
  </div>;
}
