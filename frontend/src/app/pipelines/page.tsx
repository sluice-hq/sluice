'use client';
/* eslint-disable react-hooks/set-state-in-effect -- Query results hydrate the local draft editor. */

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listProjectProcessors, type ProcessorContract } from '@/api/processors';
import {
  createPipeline, getPipeline, getPipelineHistory, listPipelines, publishPipeline, saveDraft,
  validatePipeline, type PipelineDefinition, type PipelineSummary, type ValidationReport,
} from '@/api/pipelines';
import { Button } from '@/components/ui/button';

const emptyDefinition: PipelineDefinition = {
  schemaVersion: '1', slug: 'my-pipeline',
  input: { kind: 'image', mimeTypes: ['image/jpeg', 'image/png'], maxBytes: 50000000, maxPixels: 40000000 },
  steps: [],
  limits: { maxSteps: 10, timeoutSeconds: 90, maxOutputBytes: 50000000 },
};

type PipelineStateFilter = 'all' | 'draft' | 'published';
type PipelineAction = 'save' | 'validate' | 'publish';

type StarterTemplate = {
  id: string;
  name: string;
  slugs: string[];
  flow: string;
  output: string;
};

const STARTER_TEMPLATES: StarterTemplate[] = [
  {
    id: 'webp-delivery',
    name: 'WebP delivery',
    slugs: ['mime-validation', 'webp'],
    flow: 'Validate MIME → Encode WebP',
    output: 'WebP image',
  },
  {
    id: 'resize-webp',
    name: 'Resize + WebP',
    slugs: ['mime-validation', 'resize', 'webp'],
    flow: 'Validate MIME → Resize image → Encode WebP',
    output: 'Resized WebP image',
  },
];

function comparePipelineSummaries(left: PipelineSummary, right: PipelineSummary) {
  const leftName = left.name.toLowerCase();
  const rightName = right.name.toLowerCase();
  if (leftName !== rightName) return leftName < rightName ? -1 : 1;
  const leftSlug = left.slug.toLowerCase();
  const rightSlug = right.slug.toLowerCase();
  if (leftSlug !== rightSlug) return leftSlug < rightSlug ? -1 : 1;
  return left.id < right.id ? -1 : left.id > right.id ? 1 : 0;
}

export default function PipelinesPage() {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const [selected, setSelected] = useState('');
  const [pipelineSearch, setPipelineSearch] = useState('');
  const [pipelineState, setPipelineState] = useState<PipelineStateFilter>('all');
  const [name, setName] = useState('My pipeline');
  const [description, setDescription] = useState('');
  const [mode, setMode] = useState<'form' | 'json'>('form');
  const [definition, setDefinition] = useState<PipelineDefinition>(emptyDefinition);
  const [json, setJson] = useState(JSON.stringify(emptyDefinition, null, 2));
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [message, setMessage] = useState('');
  const [busyAction, setBusyAction] = useState<PipelineAction | null>(null);
  const [dirty, setDirty] = useState(false);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const hydratedPipeline = useRef<string | null>(null);
  const appliedMarketRelease = useRef('');
  const { data: pipelineList = [] } = useQuery({ queryKey: ['pipelines'], queryFn: listPipelines });
  const visiblePipelines = useMemo(() => {
    const query = pipelineSearch.trim().toLowerCase();
    return pipelineList
      .filter((item) => !query || item.name.toLowerCase().includes(query) || item.slug.toLowerCase().includes(query))
      .filter((item) => pipelineState === 'all'
        || (pipelineState === 'draft' && item.draftVersion !== null)
        || (pipelineState === 'published' && item.stableVersion !== null))
      .toSorted(comparePipelineSummaries);
  }, [pipelineList, pipelineSearch, pipelineState]);
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

  async function act(action: PipelineAction) {
    setBusyAction(action); setMessage('');
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
    finally { setBusyAction(null); }
  }

  const busy = busyAction !== null;

  return <div className="max-w-7xl space-y-6">
    <header><p className="text-sm font-medium text-primary">Pipelines</p><h1 className="text-2xl font-bold">Build a media pipeline</h1>
      <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">Choose a starter flow or assemble enabled processor releases step by step. Form and JSON edit the same definition; publishing locks an immutable version your application can call safely.</p></header>
    <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
      <aside className="space-y-3 rounded-xl border border-border bg-card p-4">
        <Button type="button" onClick={() => selectPipeline('')} disabled={busy} className="w-full">New pipeline</Button>
        <label className="block text-sm font-medium">Search pipelines<input type="search" value={pipelineSearch} onChange={(event) => setPipelineSearch(event.target.value)} placeholder="Name or slug" className="mt-1 w-full rounded-md border border-border bg-background p-2 font-normal" /></label>
        <label className="block text-sm font-medium">Pipeline state<select value={pipelineState} onChange={(event) => setPipelineState(event.target.value as PipelineStateFilter)} className="mt-1 h-10 w-full rounded-md border border-border bg-background px-2 font-normal">
          <option value="all">All states</option><option value="draft">Draft</option><option value="published">Published</option>
        </select></label>
        <p className="text-xs text-muted-foreground" aria-live="polite">Showing {visiblePipelines.length} of {pipelineList.length} pipelines</p>
        {visiblePipelines.map((item) => <Button key={item.id} type="button" variant="outline" onClick={() => selectPipeline(item.slug)} disabled={busy} aria-current={selected === item.slug ? 'true' : undefined} className={`h-auto w-full justify-start whitespace-normal p-3 text-left ${selected === item.slug ? 'border-primary bg-primary/10' : ''}`}>
          <span className="block font-medium">{item.name}</span><span className="text-xs text-muted-foreground">{item.slug}</span><span className="mt-1 block text-xs text-muted-foreground">{item.draftVersion !== null ? `Draft v${item.draftVersion}` : 'No draft'} · {item.stableVersion !== null ? `Published v${item.stableVersion}` : 'Not published'}</span>
        </Button>)}
        {pipelineList.length > 0 && visiblePipelines.length === 0 && <p className="rounded-md border border-dashed border-border p-3 text-sm text-muted-foreground">No pipelines match this search and state.</p>}
        {pipelineList.length === 0 && <p className="rounded-md border border-dashed border-border p-3 text-sm text-muted-foreground">No pipelines yet.</p>}
      </aside>
      <section className="space-y-5 rounded-xl border border-border bg-card p-5">
        <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Name<input value={name} onChange={(e) => { setName(e.target.value); setDirty(true); }} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
          <label className="text-sm">Description<input value={description} onChange={(e) => { setDescription(e.target.value); setDirty(true); }} disabled={!!selected} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label></div>
        <div className="flex gap-2" role="group" aria-label="Pipeline editor mode"><Button type="button" size="sm" variant={mode === 'form' ? 'default' : 'outline'} aria-pressed={mode === 'form'} onClick={() => switchMode('form')}>Form</Button><Button type="button" size="sm" variant={mode === 'json' ? 'default' : 'outline'} aria-pressed={mode === 'json'} onClick={() => switchMode('json')}>JSON</Button></div>
        {mode === 'json' ? <textarea aria-label="Canonical pipeline JSON" value={json} onChange={(e) => { setJson(e.target.value); setReport(null); setDirty(true); }} className="min-h-[520px] w-full rounded-md border border-border bg-background p-4 font-mono text-sm" />
          : <FormEditor definition={definition} processors={enabledProcessors} processorMap={processorMap} releasesReady={projectReleasesReady} dirty={dirty} update={update} updateStep={updateStep} />}
        {!!projectId && projectReleasesQuery.isLoading && <p role="status" className="rounded-md border border-border bg-background p-3 text-sm text-muted-foreground">Loading enabled processor releases…</p>}
        {!!projectId && projectReleasesQuery.isError && <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm">Enabled processor releases could not be loaded. Existing draft step availability cannot be checked until the catalog is available.</p>}
        {unavailableSteps.length > 0 && <p role="alert" className="rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm">{unavailableSteps.length} step release(s) are not enabled for this project. Existing published pipelines remain valid, but this draft cannot be published until those exact releases are enabled or replaced. <Link href="/processors" className="font-medium text-primary hover:underline">Open Processor Market</Link></p>}
        {report && <div className={`rounded-md border p-3 text-sm ${report.valid ? 'border-emerald-500/40' : 'border-destructive/40'}`}><p className="font-medium">{report.valid ? 'Valid pipeline' : `${report.errors.length} validation issue(s)`}</p>
          {report.errors.map((error) => <p key={`${error.path}-${error.code}`} className="mt-1 font-mono text-xs">{error.path}: {error.message}</p>)}</div>}
        {message && <p role="status" className="rounded-md border border-border bg-background p-3 text-sm">{message}</p>}
        {dirty && <p className="text-xs text-amber-300">Unsaved changes. Save and validate before publishing.</p>}
        {confirmPublish && <div role="alertdialog" aria-label="Confirm pipeline publication" className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4 text-sm"><p className="font-medium">Publish an immutable pipeline version?</p><p className="mt-1 text-muted-foreground">Published definitions cannot be edited. Save any final changes first.</p><div className="mt-3 flex gap-2"><Button type="button" disabled={busy} aria-busy={busyAction === 'publish'} onClick={() => act('publish')} className="bg-emerald-600 text-white hover:bg-emerald-500">{busyAction === 'publish' ? 'Publishing…' : 'Confirm publish'}</Button><Button type="button" variant="outline" disabled={busy} onClick={() => setConfirmPublish(false)}>Cancel</Button></div></div>}
        <div className="flex flex-wrap gap-2"><Button type="button" disabled={busy} aria-busy={busyAction === 'save'} onClick={() => act('save')}>{busyAction === 'save' ? 'Saving…' : 'Save draft'}</Button><Button type="button" variant="outline" disabled={busy || !selected} aria-busy={busyAction === 'validate'} onClick={() => act('validate')}>{busyAction === 'validate' ? 'Validating…' : 'Validate'}</Button><Button type="button" variant="outline" disabled={busy || !detail?.draft || dirty} onClick={() => setConfirmPublish(true)} className="border-emerald-500/50 text-emerald-200 hover:bg-emerald-500/10">Publish immutable version</Button></div>
        {!!selected && <div><h2 className="font-semibold">Version history</h2><div className="mt-2 flex flex-wrap gap-2">{history.map((item) => <span key={item.id} className="rounded border border-border px-2 py-1 text-xs">v{item.versionNumber} {item.status}{detail?.pipeline.stableVersion === item.versionNumber ? ' · stable' : ''}</span>)}</div></div>}
      </section>
    </div>
  </div>;
}

function FormEditor({ definition, processors, processorMap, releasesReady, dirty, update, updateStep }: {
  definition: PipelineDefinition; processors: ProcessorContract[]; processorMap: Map<string, ProcessorContract>;
  releasesReady: boolean; dirty: boolean;
  update: (next: PipelineDefinition) => void; updateStep: (index: number, patch: Record<string, unknown>) => void;
}) {
  const [pendingTemplate, setPendingTemplate] = useState<StarterTemplate | null>(null);
  const [templateStatus, setTemplateStatus] = useState('');
  const [templateHighlight, setTemplateHighlight] = useState(0);
  const templateDialogRef = useRef<HTMLDivElement>(null);
  const templateButtonRefs = useRef(new Map<string, HTMLButtonElement>());
  const releasesBySlug = new Map<string, ProcessorContract[]>();
  processors.forEach((item) => releasesBySlug.set(item.slug, [...(releasesBySlug.get(item.slug) ?? []), item]));
  const canUseTemplate = (slugs: string[]) => slugs.every((slug) => releasesBySlug.has(slug));
  const activeTemplate = STARTER_TEMPLATES.find((template) => template.slugs.length === definition.steps.length
    && template.slugs.every((slug, index) => definition.steps[index]?.processor === slug));
  const precedingOutput = definition.steps.length === 0
    ? definition.input.mimeTypes
    : propagatedOutputMimeTypes(definition.steps[definition.steps.length - 1], definition.input.mimeTypes, processorMap);
  const nextProcessor = processors.find((candidate) => precedingOutput.some((mime) => candidate.input.mimeTypes.some((accepted) => mediaTypeMatches(mime, accepted))));

  useEffect(() => {
    if (pendingTemplate) templateDialogRef.current?.focus();
  }, [pendingTemplate]);

  function restoreTemplateFocus(templateId: string) {
    window.requestAnimationFrame(() => templateButtonRefs.current.get(templateId)?.focus());
  }

  function templateStepNames(template: StarterTemplate) {
    return template.slugs.map((slug) => releasesBySlug.get(slug)?.[0]?.displayName ?? slug);
  }

  function currentStepNames() {
    return definition.steps.map((step) => processorMap.get(`${step.processor}@${step.version}`)?.displayName ?? step.processor);
  }

  function applySelectedTemplate(template: StarterTemplate) {
    const steps = buildTemplateSteps(template.slugs, releasesBySlug);
    if (!steps) return;
    const previousCount = definition.steps.length;
    update({ ...definition, steps });
    setPendingTemplate(null);
    setTemplateHighlight((value) => value + 1);
    setTemplateStatus(`Template applied: ${template.name}. Replaced ${previousCount} ${previousCount === 1 ? 'step' : 'steps'} with ${steps.length}: ${templateStepNames(template).join(' → ')}. Output: ${template.output}.`);
    restoreTemplateFocus(template.id);
  }

  function cancelTemplateReplacement(template: StarterTemplate) {
    setPendingTemplate(null);
    setTemplateStatus(`Kept the current pipeline steps. ${template.name} was not applied.`);
    restoreTemplateFocus(template.id);
  }

  function chooseTemplate(template: StarterTemplate) {
    if (activeTemplate?.id === template.id) {
      setTemplateStatus(`${template.name} is already applied. Flow: ${template.flow}. Output: ${template.output}.`);
      return;
    }
    if (dirty && definition.steps.length > 0) {
      setPendingTemplate(template);
      return;
    }
    applySelectedTemplate(template);
  }

  return <div className="space-y-4">
    <label className="block text-sm">Pipeline slug<input value={definition.slug} disabled className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    {processors.length > 0 ? <section aria-labelledby="starter-templates-heading" className="rounded-lg border border-border bg-background/40 p-4"><h2 id="starter-templates-heading" className="text-sm font-medium">Starter templates</h2><p className="mt-1 text-xs leading-5 text-muted-foreground">Choose a complete flow built only from releases enabled for this project. The selected template follows the current ordered processor steps.</p><div className="mt-3 grid gap-3 sm:grid-cols-2">{STARTER_TEMPLATES.map((template) => {
      const selected = activeTemplate?.id === template.id;
      return <Button key={template.id} ref={(node) => { if (node) templateButtonRefs.current.set(template.id, node); else templateButtonRefs.current.delete(template.id); }} type="button" variant={selected ? 'default' : 'outline'} disabled={!canUseTemplate(template.slugs)} aria-pressed={selected} onClick={() => chooseTemplate(template)} className={`h-auto min-h-24 w-full items-start justify-start whitespace-normal p-3 text-left ${selected ? 'shadow-[0_10px_28px_rgb(35_149_255_/_0.2)]' : ''}`}>
        <span className="block"><span className="flex items-center justify-between gap-3"><span className="font-semibold">{template.name}</span><span className="text-xs font-medium">{selected ? 'Selected' : 'Apply'}</span></span><span className={`mt-2 block text-xs leading-5 ${selected ? 'text-primary-foreground/80' : 'text-muted-foreground'}`}>{template.flow}</span><span className={`mt-1 block text-xs ${selected ? 'text-primary-foreground/80' : 'text-muted-foreground'}`}>Output: {template.output}</span></span>
      </Button>;
    })}</div>{templateStatus && <p role="status" aria-live="polite" className="mt-3 rounded-md border border-primary/30 bg-primary/10 p-3 text-sm text-foreground">{templateStatus}</p>}{pendingTemplate && <div ref={templateDialogRef} role="alertdialog" tabIndex={-1} aria-label="Confirm starter template" aria-describedby="starter-template-confirmation-description" onKeyDown={(event) => { if (event.key === 'Escape') { event.preventDefault(); cancelTemplateReplacement(pendingTemplate); } }} className="mt-3 rounded-lg border border-amber-500/40 bg-amber-500/10 p-4 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"><p className="font-semibold">Replace unsaved pipeline steps?</p><p id="starter-template-confirmation-description" className="mt-1 text-muted-foreground">Your unsaved input settings remain, but the ordered processor steps will be replaced.</p><dl className="mt-3 space-y-2 text-xs"><div><dt className="font-medium">Current flow</dt><dd className="text-muted-foreground">{currentStepNames().join(' → ') || 'No steps'}</dd></div><div><dt className="font-medium">New flow</dt><dd className="text-muted-foreground">{templateStepNames(pendingTemplate).join(' → ')}</dd></div><div><dt className="font-medium">Result</dt><dd className="text-muted-foreground">{pendingTemplate.output}</dd></div></dl><div className="mt-4 flex gap-2"><Button type="button" onClick={() => applySelectedTemplate(pendingTemplate)}>Apply {pendingTemplate.name}</Button><Button type="button" variant="outline" onClick={() => cancelTemplateReplacement(pendingTemplate)}>Keep current steps</Button></div></div>}</section>
      : releasesReady ? <div className="rounded-xl border border-dashed border-primary/40 bg-primary/5 p-6 text-center"><h2 className="font-semibold">Enable a processor to start building</h2><p className="mt-2 text-sm text-muted-foreground">This project has no processor releases enabled yet. Choose only the capabilities this project needs.</p><Link href="/processors" className="mt-4 inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/80">Browse Processor Market</Link></div>
        : null}
    <label className="block text-sm">Input MIME types<input value={definition.input.mimeTypes.join(', ')} onChange={(e) => update({ ...definition, input: { ...definition.input, mimeTypes: e.target.value.split(',').map((v) => v.trim()).filter(Boolean) } })} className="mt-1 w-full rounded-md border border-border bg-background p-2" /></label>
    <section key={templateHighlight} aria-label="Pipeline definition steps" className={templateHighlight > 0 ? 'sluice-template-settle space-y-4 rounded-xl border border-transparent p-1' : 'space-y-4 rounded-xl border border-transparent p-1'}>{definition.steps.map((step, index) => {
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
        <div className="flex flex-wrap gap-2"><Button type="button" size="xs" variant="ghost" disabled={index === 0} onClick={() => reorder(definition, index, index - 1, update)}>Move up</Button><Button type="button" size="xs" variant="ghost" disabled={index === definition.steps.length - 1} onClick={() => reorder(definition, index, index + 1, update)}>Move down</Button><Button type="button" size="xs" variant="destructive" onClick={() => update({ ...definition, steps: definition.steps.filter((_, i) => i !== index) })}>Remove step</Button></div>
      </div>;
    })}{definition.steps.length === 0 && <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">No processor steps yet. Apply a starter template or add the next compatible enabled release.</p>}<Button type="button" variant="outline" disabled={!nextProcessor || definition.steps.length >= 10} onClick={() => { if (nextProcessor) update({ ...definition, steps: [...definition.steps, { id: `step-${definition.steps.length + 1}`, processor: nextProcessor.slug, version: nextProcessor.version, config: configDefaults(nextProcessor) }] }); }}>Add ordered step</Button></section>
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
  return <div className="relative text-sm"><span>Exact processor release</span><Button type="button" variant="outline" aria-label="Select exact processor release" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined} onClick={() => setOpen((value) => !value)} className="mt-1 w-full justify-between text-left"><span>{selected ? `${selected.displayName} · v${selected.version}` : 'Choose a processor'}</span><span aria-hidden="true">⌄</span></Button>{open && <div className="absolute z-20 mt-1 w-full min-w-80 rounded-lg border border-border bg-card p-2 shadow-xl"><input autoFocus role="combobox" aria-label="Search enabled processor releases" aria-autocomplete="list" aria-expanded="true" aria-controls={listId} aria-activedescendant={orderedResults[highlighted] ? `${listId}-${highlighted}` : undefined} value={query} onChange={(event) => { setQuery(event.target.value); setHighlighted(0); }} onKeyDown={(event) => { if (event.key === 'Escape') setOpen(false); if (event.key === 'ArrowDown') { event.preventDefault(); setHighlighted((value) => Math.min(value + 1, orderedResults.length - 1)); } if (event.key === 'ArrowUp') { event.preventDefault(); setHighlighted((value) => Math.max(value - 1, 0)); } if (event.key === 'Enter' && orderedResults[highlighted]) { event.preventDefault(); choose(orderedResults[highlighted]); } }} placeholder="Search name, category or file type" className="w-full rounded border border-border bg-background p-2" /><div id={listId} role="listbox" aria-label="Enabled processor releases" className="mt-2 max-h-72 overflow-y-auto">{groups.map((group) => <div key={group} role="group" aria-label={group}><p className="px-2 py-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{group}</p>{orderedResults.map((processor, index) => processor.category === group && <Button id={`${listId}-${index}`} role="option" aria-selected={`${processor.slug}@${processor.version}` === `${selected?.slug}@${selected?.version}`} aria-disabled={!compatible(processor)} key={`${processor.slug}@${processor.version}`} type="button" variant="ghost" disabled={!compatible(processor)} onClick={() => choose(processor)} className={`h-auto w-full justify-start whitespace-normal px-2 py-2 text-left ${index === highlighted ? 'bg-primary/10' : ''}`}><span className="block"><span className="block font-medium">{processor.displayName} · v{processor.version}</span><span className="block text-xs text-muted-foreground">{processor.input.mimeTypes.join(', ')} → {processor.output.mimeTypes.join(', ')}</span></span></Button>)}</div>)}{orderedResults.length === 0 && <p className="p-3 text-sm text-muted-foreground">No enabled releases match this search.</p>}</div><Link href="/processors" className="mt-2 block border-t border-border px-2 pt-2 text-xs font-medium text-primary hover:underline">Manage processors in the market</Link></div>}</div>;
}

function buildTemplateSteps(slugs: string[], releases: Map<string, ProcessorContract[]>) {
  const steps = slugs.map((slug, index) => {
    const release = releases.get(slug)?.[0];
    return { id: `step-${index + 1}`, processor: slug, version: release?.version ?? '', config: release ? configDefaults(release) : {} };
  });
  return steps.every((step) => step.version) ? steps : null;
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
