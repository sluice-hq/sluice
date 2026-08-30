'use client';

import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Check, CheckCircle2, FileVideo, Loader2, Upload, X, XCircle } from 'lucide-react';
import { completeUpload, requestUploadUrl } from '@/api/assets';
import { ApiError } from '@/api/client';
import { getPublishedPipelines } from '@/api/pipelines';
import type { PublishedPipeline } from '@/api/types';
import { startRun } from '@/api/runs';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type UploadPhase = 'IDLE' | 'REQUEST_URL' | 'TRANSFER' | 'COMPLETE' | 'CREATE_RUN' | 'COMPLETED' | 'ERROR';
type ActivePhase = Exclude<UploadPhase, 'IDLE' | 'COMPLETED' | 'ERROR'>;

const PHASES: Array<{ id: ActivePhase; label: string }> = [
  { id: 'REQUEST_URL', label: 'Create upload URL' },
  { id: 'TRANSFER', label: 'Transfer file' },
  { id: 'COMPLETE', label: 'Verify upload' },
  { id: 'CREATE_RUN', label: 'Create run' },
];

type Attempt = {
  assetId: string | null;
  uploadUrl: string | null;
  requestKey: string;
  completionKey: string;
  runKey: string;
};

function mimeMatches(actual: string, pattern: string) {
  if (pattern === '*/*') return true;
  const [patternType, patternSubtype] = pattern.toLowerCase().split('/');
  const [actualType, actualSubtype] = actual.toLowerCase().split('/');
  return Boolean(patternType && patternSubtype && actualType === patternType
    && (patternSubtype === '*' || patternSubtype === actualSubtype));
}

function formatBytes(bytes: number) {
  if (bytes >= 1_000_000) return `${(bytes / 1_000_000).toFixed(bytes % 1_000_000 === 0 ? 0 : 1)} MB`;
  return `${Math.ceil(bytes / 1_000)} KB`;
}

function comparePublishedPipelines(left: PublishedPipeline, right: PublishedPipeline) {
  const leftName = left.name.toLowerCase();
  const rightName = right.name.toLowerCase();
  if (leftName !== rightName) return leftName < rightName ? -1 : 1;
  const leftSlug = left.slug.toLowerCase();
  const rightSlug = right.slug.toLowerCase();
  if (leftSlug !== rightSlug) return leftSlug < rightSlug ? -1 : 1;
  if (left.versionNumber !== right.versionNumber) return right.versionNumber - left.versionNumber;
  return left.versionId < right.versionId ? -1 : left.versionId > right.versionId ? 1 : 0;
}

function pipelineOptionLabel(pipeline: PublishedPipeline) {
  return `${pipeline.name} · ${pipeline.slug} · Published v${pipeline.versionNumber}`;
}

type PipelineSelection = {
  projectId: string;
  pipeline: PublishedPipeline;
};

function PublishedPipelineCombobox({ pipelines, selected, disabled, loading, onSelect }: {
  pipelines: PublishedPipeline[];
  selected: PublishedPipeline | null;
  disabled: boolean;
  loading: boolean;
  onSelect: (pipeline: PublishedPipeline) => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const listId = useId();
  const results = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return pipelines
      .filter((pipeline) => !normalizedQuery
        || pipeline.name.toLowerCase().includes(normalizedQuery)
        || pipeline.slug.toLowerCase().includes(normalizedQuery))
      .toSorted(comparePublishedPipelines);
  }, [pipelines, query]);
  const activeIndex = Math.min(highlighted, Math.max(results.length - 1, 0));

  useEffect(() => {
    if (open && results[activeIndex]) document.getElementById(`${listId}-${activeIndex}`)?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex, listId, open, results]);

  function choose(pipeline: PublishedPipeline) {
    if (pipeline.versionId !== selected?.versionId) onSelect(pipeline);
    setOpen(false);
    setQuery('');
  }

  return <div className="relative" onBlur={(event) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setOpen(false);
      setQuery('');
    }
  }}>
    <label htmlFor="published-pipeline" className="text-sm font-medium">Published pipeline</label>
    <div className="relative mt-1">
      <input
        id="published-pipeline"
        role="combobox"
        aria-autocomplete="list"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open && results[activeIndex] ? `${listId}-${activeIndex}` : undefined}
        autoComplete="off"
        disabled={disabled}
        placeholder={loading ? 'Loading pipelines…' : 'Search published pipelines'}
        value={open ? query : selected ? pipelineOptionLabel(selected) : ''}
        onFocus={() => { setOpen(true); setQuery(''); setHighlighted(0); }}
        onChange={(event) => { setOpen(true); setQuery(event.target.value); setHighlighted(0); }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') { event.preventDefault(); setOpen(false); setQuery(''); }
          if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true); setHighlighted((value) => Math.min(value + 1, Math.max(results.length - 1, 0))); }
          if (event.key === 'ArrowUp') { event.preventDefault(); setOpen(true); setHighlighted((value) => Math.max(value - 1, 0)); }
          if (event.key === 'Home' && open) { event.preventDefault(); setHighlighted(0); }
          if (event.key === 'End' && open) { event.preventDefault(); setHighlighted(Math.max(results.length - 1, 0)); }
          if (event.key === 'Enter' && open && results[activeIndex]) { event.preventDefault(); choose(results[activeIndex]); }
        }}
        className="h-10 w-full rounded-md border border-border bg-background px-3 pr-9 text-sm disabled:opacity-50"
      />
      <span aria-hidden="true" className="pointer-events-none absolute right-3 top-2 text-muted-foreground">⌄</span>
    </div>
    {open && !disabled && <div id={listId} role="listbox" aria-label="Published pipelines" className="absolute z-20 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-border bg-card p-2 shadow-xl">
      {results.map((pipeline, index) => <button
        id={`${listId}-${index}`}
        key={pipeline.versionId}
        type="button"
        role="option"
        aria-selected={pipeline.versionId === selected?.versionId}
        tabIndex={-1}
        onMouseDown={(event) => event.preventDefault()}
        onMouseEnter={() => setHighlighted(index)}
        onClick={() => choose(pipeline)}
        className={`block w-full rounded px-3 py-2 text-left text-sm hover:bg-primary/10 ${index === activeIndex ? 'bg-primary/10' : ''}`}
      ><span className="block font-medium">{pipeline.name}</span><span className="block text-xs text-muted-foreground">{pipeline.slug} · Published v{pipeline.versionNumber}</span></button>)}
      {results.length === 0 && <p className="p-3 text-sm text-muted-foreground">No published pipelines match this search.</p>}
    </div>}
  </div>;
}

function explainFailure(error: unknown, phase: ActivePhase) {
  if (error instanceof ApiError) {
    if (error.status === 413) return 'The server rejected this file because it exceeds the active upload or pipeline size limit. Choose a smaller file.';
    if (error.status === 415) return 'The server rejected this media type. Choose a file whose MIME type is listed for the selected pipeline.';
    if (error.status === 400) return `The server could not accept the file or request (${error.message}). Check the file, then retry this phase or start over.`;
  }
  if (error instanceof Error && error.message) return error.message;
  return `The ${PHASES.find((item) => item.id === phase)?.label.toLowerCase()} phase failed. You can safely retry it.`;
}

export default function UploadPage() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [phase, setPhase] = useState<UploadPhase>('IDLE');
  const [failedPhase, setFailedPhase] = useState<ActivePhase | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pipelineSelection, setPipelineSelection] = useState<PipelineSelection | null>(null);
  const [runId, setRunId] = useState<string | null>(null);
  const submitting = useRef(false);
  const attempt = useRef<Attempt>({ assetId: null, uploadUrl: null, requestKey: '', completionKey: '', runKey: '' });
  const { data: session, isLoading: sessionLoading } = useQuery<{ selectedProjectId?: string }>({
    queryKey: ['session'],
    queryFn: async () => {
      const response = await fetch('/api/session', { cache: 'no-store' });
      if (!response.ok) throw new Error('Session unavailable');
      return response.json();
    },
  });
  const projectId = session?.selectedProjectId ?? '';
  const { data: pipelines = [], isLoading: pipelinesQueryLoading, error: pipelinesError } = useQuery({
    queryKey: ['pipelines', 'published', projectId],
    queryFn: getPublishedPipelines,
    enabled: Boolean(projectId),
  });
  const pipelinesLoading = sessionLoading || (Boolean(projectId) && pipelinesQueryLoading);
  const pipeline = pipelineSelection?.projectId === projectId ? pipelineSelection.pipeline : null;

  const acceptedTypes = useMemo(() => {
    if (!pipeline?.contractUsable) return [];
    return pipeline.uploadConstraints.allowedContentTypes.filter((type) =>
      pipeline.inputContract.mimeTypes.some((pattern) => mimeMatches(type, pattern)));
  }, [pipeline]);
  const maxBytes = useMemo(() => {
    if (!pipeline?.contractUsable) return 0;
    const pipelineLimit = pipeline.inputContract.maxBytes;
    return Math.min(pipelineLimit, pipeline.uploadConstraints.maxBytes);
  }, [pipeline]);
  const isBusy = PHASES.some((item) => item.id === phase);

  const clearAttempt = useCallback(() => {
    attempt.current = { assetId: null, uploadUrl: null, requestKey: '', completionKey: '', runKey: '' };
    setRunId(null);
    setFailedPhase(null);
    setError(null);
    setPhase('IDLE');
  }, []);

  useEffect(() => {
    if (pipelineSelection && (!projectId
      || pipelineSelection.projectId !== projectId
      || (!pipelinesLoading && !pipelines.some((candidate) => candidate.versionId === pipelineSelection.pipeline.versionId)))) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- A project/query boundary invalidates the complete upload attempt atomically.
      setPipelineSelection(null);
      setFile(null);
      clearAttempt();
    }
  }, [clearAttempt, pipelineSelection, pipelines, pipelinesLoading, projectId]);

  const validateFile = useCallback((candidate: File) => {
    if (!pipeline) return 'Select a published pipeline before choosing a file.';
    if (!pipeline.contractUsable) return pipeline.contractIssue || 'This pipeline cannot safely accept test files.';
    if (!candidate.type || !acceptedTypes.includes(candidate.type.toLowerCase())) {
      return `This file reports ${candidate.type || 'no MIME type'}, but ${pipeline.name} accepts ${acceptedTypes.join(', ') || 'no globally enabled input types'}.`;
    }
    if (candidate.size <= 0) return 'Choose a non-empty file.';
    if (candidate.size > maxBytes) return `This file is ${formatBytes(candidate.size)}. The active limit is ${formatBytes(maxBytes)}.`;
    return null;
  }, [acceptedTypes, maxBytes, pipeline]);

  const chooseFile = useCallback((candidate: File) => {
    clearAttempt();
    const validationError = validateFile(candidate);
    if (validationError) {
      setFile(null);
      setError(validationError);
      return;
    }
    setFile(candidate);
  }, [clearAttempt, validateFile]);

  const onDrop = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    if (!pipeline || isBusy) return;
    const candidate = event.dataTransfer.files?.[0];
    if (candidate) chooseFile(candidate);
  }, [chooseFile, isBusy, pipeline]);

  const runAttempt = async (startAt: ActivePhase) => {
    if (!file || !pipeline || submitting.current) return;
    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }

    submitting.current = true;
    setError(null);
    setFailedPhase(null);
    const startIndex = PHASES.findIndex((item) => item.id === startAt);
    let currentPhase = startAt;
    try {
      if (!attempt.current.requestKey) attempt.current.requestKey = crypto.randomUUID();
      if (!attempt.current.completionKey) attempt.current.completionKey = crypto.randomUUID();
      if (!attempt.current.runKey) attempt.current.runKey = crypto.randomUUID();

      if (startIndex <= 0) {
        currentPhase = 'REQUEST_URL';
        setPhase('REQUEST_URL');
        const upload = await requestUploadUrl(
          { filename: file.name, contentType: file.type, size: file.size }, attempt.current.requestKey,
        );
        attempt.current.assetId = upload.assetId;
        attempt.current.uploadUrl = upload.uploadUrl;
      }
      if (startIndex <= 1) {
        currentPhase = 'TRANSFER';
        setPhase('TRANSFER');
        const uploadResponse = await fetch(attempt.current.uploadUrl!, {
          method: 'PUT', body: file,
          headers: { 'x-ms-blob-type': 'BlockBlob', 'Content-Type': file.type },
        });
        if (!uploadResponse.ok) throw new ApiError('The storage transfer failed. Retry this phase; if the upload URL expired, start over.', uploadResponse.status);
      }
      if (startIndex <= 2) {
        currentPhase = 'COMPLETE';
        setPhase('COMPLETE');
        await completeUpload(attempt.current.assetId!, attempt.current.completionKey);
      }
      currentPhase = 'CREATE_RUN';
      setPhase('CREATE_RUN');
      const run = await startRun(pipeline.slug, attempt.current.assetId!, attempt.current.runKey);
      setRunId(run.id);
      setPhase('COMPLETED');
    } catch (caught) {
      setFailedPhase(currentPhase);
      setError(explainFailure(caught, currentPhase));
      setPhase('ERROR');
    } finally {
      submitting.current = false;
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link href="/assets"><Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground"><ArrowLeft className="mr-2 size-4" />Back to Assets</Button></Link>
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Test a pipeline</h2>
        <p className="mt-1 text-muted-foreground">Choose a published contract, upload compatible media directly to storage, and continue to the created run.</p>
      </div>

      <div className="space-y-7 rounded-xl border border-border bg-card p-6 shadow-sm sm:p-8">
        {phase === 'COMPLETED' && runId ? (
          <div className="flex flex-col items-center py-10 text-center" role="status">
            <div className="mb-6 flex size-16 items-center justify-center rounded-full bg-status-success/20"><CheckCircle2 className="size-8 text-status-success" /></div>
            <h3 className="text-2xl font-semibold">Run created</h3>
            <p className="mt-2 max-w-md text-muted-foreground">The upload is verified and run <span className="font-mono text-foreground">{runId}</span> is queued. Open it to follow processing and download its output.</p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <Button onClick={() => router.push(`/jobs/${runId}`)}>View run</Button>
              <Button variant="outline" onClick={() => { setFile(null); clearAttempt(); }}>Test another file</Button>
            </div>
          </div>
        ) : <>
          <section className="space-y-2" aria-labelledby="pipeline-heading">
            <h3 id="pipeline-heading" className="font-semibold">1. Select the pipeline</h3>
            <PublishedPipelineCombobox pipelines={pipelines} selected={pipeline} disabled={isBusy || pipelinesLoading} loading={pipelinesLoading} onSelect={(nextPipeline) => { setPipelineSelection({ projectId, pipeline: nextPipeline }); setFile(null); clearAttempt(); }} />
            {pipelinesError && <p className="text-sm text-status-error" role="alert">Could not load published pipelines. Check the API connection, then refresh.</p>}
            {!pipelinesLoading && !pipelinesError && pipelines.length === 0 && <p className="text-sm text-muted-foreground">This project has no published pipelines yet.</p>}
            {pipeline && <div className="rounded-lg border border-border bg-background/60 p-3 text-sm" role="note">
              <p><span className="font-medium">Accepted types:</span> {acceptedTypes.join(', ') || 'None enabled by the server'}</p>
              <p className="mt-1"><span className="font-medium">Maximum file size:</span> {maxBytes > 0 ? formatBytes(maxBytes) : 'Unavailable'}</p>
              {!pipeline.contractUsable && <p className="mt-2 text-status-error" role="alert">{pipeline.contractIssue}</p>}
              {pipeline.inputContract.maxPixels > 0 && <p className="mt-1 text-muted-foreground">Images are also verified server-side up to {pipeline.inputContract.maxPixels.toLocaleString()} pixels.</p>}
            </div>}
          </section>

          <section className="space-y-3" aria-labelledby="file-heading">
            <h3 id="file-heading" className="font-semibold">2. Choose a compatible file</h3>
            {!file ? <div className={cn('rounded-lg border-2 border-dashed border-border p-10 text-center transition-colors', pipeline && acceptedTypes.length ? 'cursor-pointer hover:bg-white/[0.02]' : 'cursor-not-allowed opacity-60')} onDragOver={(event) => event.preventDefault()} onDrop={onDrop} onClick={() => pipeline && acceptedTypes.length && document.getElementById('file-upload')?.click()}>
              <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-primary/10"><Upload className="size-7 text-primary" /></div>
              <p className="font-medium">{pipeline ? 'Click or drag a file here' : 'Select a pipeline to enable file selection'}</p>
              {pipeline && <p className="mt-1 text-sm text-muted-foreground">{acceptedTypes.join(', ')} · up to {formatBytes(maxBytes)}</p>}
              <input type="file" id="file-upload" className="sr-only" accept={acceptedTypes.join(',')} disabled={!pipeline || !acceptedTypes.length || isBusy} onChange={(event) => { const candidate = event.target.files?.[0]; if (candidate) chooseFile(candidate); event.target.value = ''; }} />
            </div> : <div className="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-4">
              <div className="flex items-center gap-4"><div className="rounded bg-primary/20 p-2"><FileVideo className="size-6 text-primary" /></div><div><p className="font-medium">{file.name}</p><p className="text-sm text-muted-foreground">{file.type} · {formatBytes(file.size)}</p></div></div>
              {!isBusy && <Button variant="ghost" size="icon" aria-label="Remove selected file" onClick={() => { setFile(null); clearAttempt(); }}><X className="size-5" /></Button>}
            </div>}
          </section>

          {(isBusy || phase === 'ERROR') && <ol className="grid gap-2 sm:grid-cols-4" aria-label="Test progress" aria-live="polite">
            {PHASES.map((item, index) => {
              const activeIndex = phase === 'ERROR' && failedPhase ? PHASES.findIndex((entry) => entry.id === failedPhase) : PHASES.findIndex((entry) => entry.id === phase);
              const complete = index < activeIndex || phase === 'COMPLETED';
              const active = item.id === phase || (phase === 'ERROR' && item.id === failedPhase);
              return <li key={item.id} aria-current={active ? 'step' : undefined} className={cn('flex items-center gap-2 rounded-md border p-3 text-xs', active ? 'border-primary text-foreground' : 'border-border text-muted-foreground')}>
                {complete ? <Check className="size-4 text-status-success" /> : active && isBusy ? <Loader2 className="size-4 animate-spin text-primary" /> : <span className="flex size-4 items-center justify-center rounded-full border text-[10px]">{index + 1}</span>}{item.label}
              </li>;
            })}
          </ol>}

          {error && <div className="flex items-start gap-3 rounded-md border border-status-error/30 bg-status-error/10 p-4 text-sm text-status-error" role="alert"><XCircle className="mt-0.5 size-5 shrink-0" /><div><h4 className="font-semibold">Test could not continue</h4><p>{error}</p>{phase === 'ERROR' && <p className="mt-1 text-foreground">Completed phases are retained; retry resumes at the failed phase with the same idempotency key.</p>}</div></div>}

          <div className="flex flex-wrap justify-end gap-3 border-t border-border pt-4">
            {phase === 'ERROR' && <Button variant="outline" onClick={() => { attempt.current = { assetId: null, uploadUrl: null, requestKey: '', completionKey: '', runKey: '' }; setFailedPhase(null); setError(null); setPhase('IDLE'); }}>Start over</Button>}
            {phase === 'ERROR' && failedPhase ? <Button onClick={() => runAttempt(failedPhase)}>Retry {PHASES.find((item) => item.id === failedPhase)?.label.toLowerCase()}</Button> : <Button onClick={() => runAttempt('REQUEST_URL')} disabled={!file || !pipeline || !acceptedTypes.length || isBusy}>{isBusy ? <><Loader2 className="mr-2 size-4 animate-spin" />{PHASES.find((item) => item.id === phase)?.label}…</> : <><Upload className="mr-2 size-4" />Upload and create run</>}</Button>}
          </div>
        </>}
      </div>
    </div>
  );
}
