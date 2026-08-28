'use client';

import { type KeyboardEvent, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Check, CircleAlert, Copy, ExternalLink } from 'lucide-react';
import { getPublishedPipelines } from '@/api/pipelines';
import { OnboardingChecklist } from '@/components/domain/OnboardingChecklist';
import { Button } from '@/components/ui/button';

const languages = ['curl', 'javascript', 'python'] as const;
type Language = (typeof languages)[number];
type CopyStatus = { kind: 'success' | 'error'; message: string } | null;

interface Session {
  projects: Array<{ id: string; name: string }>;
  selectedProjectId?: string;
  apiBaseUrl: string;
}

function languageLabel(language: Language): string {
  if (language === 'curl') return 'cURL';
  if (language === 'javascript') return 'JavaScript';
  return 'Python';
}

function examples(apiBaseUrl: string, pipeline: string, projectId: string): Record<Language, string> {
  const api = JSON.stringify(apiBaseUrl);
  const slug = JSON.stringify(pipeline);
  const project = JSON.stringify(projectId);
  return {
    curl: [
      '# Requires Bash, curl, jq, OpenSSL, and GNU coreutils.',
      'set -euo pipefail',
      `API_BASE_URL=${api}`,
      `PROJECT_ID=${project} # Selected dashboard project; use an API key scoped to it.`,
      'SLUICE_API_KEY="<SLUICE_API_KEY>"',
      `PIPELINE_SLUG=${slug}`,
      'FILE="./photo.png"',
      'WEBHOOK_CALLBACK_URL="<HTTPS_WEBHOOK_CALLBACK_URL>"',
      '',
      '# 1. Request a write-only upload URL and upload the bytes.',
      'FILE_SIZE=$(wc -c < "$FILE" | tr -d " ")',
      'UPLOAD=$(curl --fail-with-body -sS -X POST "$API_BASE_URL/uploads" -H "X-API-Key: $SLUICE_API_KEY" -H "Idempotency-Key: create-upload-$(date +%s)" -H "Content-Type: application/json" --data "$(jq -n --arg filename "$(basename "$FILE")" --argjson size "$FILE_SIZE" \'{filename:$filename,contentType:"image/png",size:$size}\')")',
      'ASSET_ID=$(jq -r .assetId <<<"$UPLOAD"); UPLOAD_URL=$(jq -r .uploadUrl <<<"$UPLOAD")',
      'curl --fail-with-body -sS -X PUT "$UPLOAD_URL" -H "x-ms-blob-type: BlockBlob" -H "Content-Type: image/png" --upload-file "$FILE"',
      '',
      '# 2. Complete the upload.',
      'curl --fail-with-body -sS -X POST "$API_BASE_URL/uploads/$ASSET_ID/complete" -H "X-API-Key: $SLUICE_API_KEY" -H "Idempotency-Key: upload-$(date +%s)"',
      '',
      '# 3. Register the public HTTPS receiver and save its one-time signing secret securely.',
      'WEBHOOK=$(curl --fail-with-body -sS -X POST "$API_BASE_URL/webhook-endpoints" -H "X-API-Key: $SLUICE_API_KEY" -H "Content-Type: application/json" --data "$(jq -n --arg callbackUrl "$WEBHOOK_CALLBACK_URL" \'{callbackUrl:$callbackUrl}\')")',
      'WEBHOOK_ENDPOINT_ID=$(jq -r .id <<<"$WEBHOOK")',
      'WEBHOOK_SECRET=$(jq -r .secret <<<"$WEBHOOK")',
      'test -n "$WEBHOOK_ENDPOINT_ID" && test "$WEBHOOK_ENDPOINT_ID" != "null" && test -n "$WEBHOOK_SECRET" && test "$WEBHOOK_SECRET" != "null"',
      '',
      '# 4. Create a run and attach the registered webhook endpoint.',
      'RUN=$(curl --fail-with-body -sS -X POST "$API_BASE_URL/runs" -H "X-API-Key: $SLUICE_API_KEY" -H "Idempotency-Key: run-$(date +%s)" -H "Content-Type: application/json" --data "$(jq -n --arg pipeline "$PIPELINE_SLUG" --arg asset "$ASSET_ID" --arg webhookEndpointId "$WEBHOOK_ENDPOINT_ID" \'{pipeline:$pipeline,alias:"stable",inputAssetId:$asset,callback:{webhookEndpointId:$webhookEndpointId}}\')")',
      'RUN_ID=$(jq -r .id <<<"$RUN")',
      '',
      '# 5. Poll until the run reaches a terminal status.',
      'while :; do RUN=$(curl --fail-with-body -sS "$API_BASE_URL/runs/$RUN_ID" -H "X-API-Key: $SLUICE_API_KEY"); STATUS=$(jq -r .status <<<"$RUN"); [[ "$STATUS" =~ ^(COMPLETED|FAILED|REVIEW_REQUIRED)$ ]] && break; sleep 2; done',
      'test "$STATUS" = "COMPLETED" || { jq . <<<"$RUN"; exit 1; }',
      '',
      '# 6. Resolve a short-lived URL and download the first output.',
      'OUTPUTS=$(curl --fail-with-body -sS "$API_BASE_URL/runs/$RUN_ID/outputs" -H "X-API-Key: $SLUICE_API_KEY"); OUTPUT_ID=$(jq -r ".[0].id" <<<"$OUTPUTS")',
      'DOWNLOAD_URL=$(curl --fail-with-body -sS "$API_BASE_URL/assets/$OUTPUT_ID/download" -H "X-API-Key: $SLUICE_API_KEY" | jq -r .downloadUrl)',
      'curl --fail-with-body -sS "$DOWNLOAD_URL" --output output.webp',
      '',
      '# 7. In the receiver, save the exact raw request body, then verify it before JSON parsing.',
      'TIMESTAMP="<X-SLUICE-TIMESTAMP>"; SIGNATURE="<X-SLUICE-SIGNATURE>"; PAYLOAD_FILE="./webhook-body.json"',
      '[[ "$TIMESTAMP" =~ ^[0-9]{1,18}$ ]] || { echo "Invalid webhook timestamp" >&2; exit 1; }',
      'SECRET_HEX=$(printf %s "$WEBHOOK_SECRET" | base64 --decode | od -An -vtx1 | tr -d " \\n")',
      'EXPECTED="v1=$({ printf "%s." "$TIMESTAMP"; cat "$PAYLOAD_FILE"; } | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$SECRET_HEX" -hex | awk \'{print $NF}\')"',
      'AGE=$(( $(date +%s) - TIMESTAMP )); test "$AGE" -lt 0 && AGE=$(( -AGE ))',
      'test "$AGE" -le 300 && test "$SIGNATURE" = "$EXPECTED"',
    ].join('\n'),
    javascript: [
      "import { readFile, writeFile } from 'node:fs/promises';",
      "import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';",
      `const API_BASE_URL = ${api};`,
      `const PROJECT_ID = ${project}; // Selected project; use an API key scoped to it.`,
      "const SLUICE_API_KEY = '<SLUICE_API_KEY>';",
      `const PIPELINE_SLUG = ${slug};`,
      "const WEBHOOK_CALLBACK_URL = '<HTTPS_WEBHOOK_CALLBACK_URL>';",
      "const file = await readFile('./photo.png');",
      "const api = async (path, init = {}) => { const response = await fetch(API_BASE_URL + path, { ...init, headers: { 'X-API-Key': SLUICE_API_KEY, ...init.headers } }); if (!response.ok) throw new Error(await response.text()); return response.json(); };",
      '',
      '// 1. Request an upload URL and upload the bytes.',
      "const upload = await api('/uploads', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() }, body: JSON.stringify({ filename: 'photo.png', contentType: 'image/png', size: file.length }) });",
      "const blobResponse = await fetch(upload.uploadUrl, { method: 'PUT', headers: { 'x-ms-blob-type': 'BlockBlob', 'Content-Type': 'image/png' }, body: file }); if (!blobResponse.ok) throw new Error('Blob upload failed');",
      '',
      '// 2. Complete the upload.',
      "await api(`/uploads/${upload.assetId}/complete`, { method: 'POST', headers: { 'Idempotency-Key': randomUUID() } });",
      '',
      '// 3. Register the public HTTPS receiver and store the returned secret securely.',
      "const webhook = await api('/webhook-endpoints', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ callbackUrl: WEBHOOK_CALLBACK_URL }) });",
      'const WEBHOOK_SIGNING_SECRET_BASE64 = webhook.secret;',
      '',
      '// 4. Create a run and attach the registered webhook endpoint.',
      "let run = await api('/runs', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() }, body: JSON.stringify({ pipeline: PIPELINE_SLUG, alias: 'stable', inputAssetId: upload.assetId, callback: { webhookEndpointId: webhook.id } }) });",
      '',
      '// 5. Poll to a terminal state.',
      "while (!['COMPLETED', 'FAILED', 'REVIEW_REQUIRED'].includes(run.status)) { await new Promise((resolve) => setTimeout(resolve, 2000)); run = await api(`/runs/${run.id}`); }",
      "if (run.status !== 'COMPLETED') throw new Error(`Run ended with ${run.status}`);",
      '',
      '// 6. Download the first output through a short-lived URL.',
      "const [output] = await api(`/runs/${run.id}/outputs`);",
      "const { downloadUrl } = await api(`/assets/${output.id}/download`);",
      "const outputResponse = await fetch(downloadUrl); if (!outputResponse.ok) throw new Error('Output download failed');",
      "await writeFile('./output.webp', Buffer.from(await outputResponse.arrayBuffer()));",
      '',
      '// 7. Use this in the receiver on the exact raw body before JSON parsing.',
      "export function verifyWebhook(rawBody, timestamp, signature, secret = WEBHOOK_SIGNING_SECRET_BASE64) { if (typeof timestamp !== 'string' || typeof signature !== 'string' || typeof secret !== 'string' || !/^[0-9]+$/.test(timestamp)) return false; const epochSeconds = Number(timestamp); if (!Number.isSafeInteger(epochSeconds)) return false; const age = Math.abs(Math.floor(Date.now() / 1000) - epochSeconds); if (age > 300) return false; const digest = createHmac('sha256', Buffer.from(secret, 'base64')).update(timestamp + '.').update(rawBody).digest('hex'); const expected = Buffer.from(`v1=${digest}`); const received = Buffer.from(signature); return expected.length === received.length && timingSafeEqual(expected, received); }",
    ].join('\n'),
    python: [
      'import base64, binascii, hashlib, hmac, time, uuid',
      'import requests',
      `API_BASE_URL = ${api}`,
      `PROJECT_ID = ${project}  # Selected project; use an API key scoped to it.`,
      "SLUICE_API_KEY = '<SLUICE_API_KEY>'",
      `PIPELINE_SLUG = ${slug}`,
      "WEBHOOK_CALLBACK_URL = '<HTTPS_WEBHOOK_CALLBACK_URL>'",
      "HEADERS = {'X-API-Key': SLUICE_API_KEY}",
      "def api(method, path, **kwargs):\n    response = requests.request(method, API_BASE_URL + path, headers={**HEADERS, **kwargs.pop('headers', {})}, timeout=30, **kwargs)\n    response.raise_for_status()\n    return response.json()",
      '',
      '# 1. Request an upload URL and upload the bytes.',
      "file_bytes = open('./photo.png', 'rb').read()",
      "upload = api('POST', '/uploads', headers={'Idempotency-Key': str(uuid.uuid4())}, json={'filename': 'photo.png', 'contentType': 'image/png', 'size': len(file_bytes)})",
      "requests.put(upload['uploadUrl'], data=file_bytes, headers={'x-ms-blob-type': 'BlockBlob', 'Content-Type': 'image/png'}, timeout=60).raise_for_status()",
      '',
      '# 2. Complete the upload.',
      "api('POST', f\"/uploads/{upload['assetId']}/complete\", headers={'Idempotency-Key': str(uuid.uuid4())})",
      '',
      '# 3. Register the public HTTPS receiver and store the returned secret securely.',
      "webhook = api('POST', '/webhook-endpoints', json={'callbackUrl': WEBHOOK_CALLBACK_URL})",
      "WEBHOOK_SIGNING_SECRET_BASE64 = webhook['secret']",
      '',
      '# 4. Create a run and attach the registered webhook endpoint.',
      "run = api('POST', '/runs', headers={'Idempotency-Key': str(uuid.uuid4())}, json={'pipeline': PIPELINE_SLUG, 'alias': 'stable', 'inputAssetId': upload['assetId'], 'callback': {'webhookEndpointId': webhook['id']}})",
      '',
      '# 5. Poll to a terminal state.',
      "while run['status'] not in {'COMPLETED', 'FAILED', 'REVIEW_REQUIRED'}:\n    time.sleep(2)\n    run = api('GET', f\"/runs/{run['id']}\")",
      "if run['status'] != 'COMPLETED': raise RuntimeError(f\"Run ended with {run['status']}\")",
      '',
      '# 6. Download the first output through a short-lived URL.',
      "output = api('GET', f\"/runs/{run['id']}/outputs\")[0]",
      "download = api('GET', f\"/assets/{output['id']}/download\")",
      "output_response = requests.get(download['downloadUrl'], timeout=60)",
      "output_response.raise_for_status()",
      "open('./output.webp', 'wb').write(output_response.content)",
      '',
      '# 7. Use this in the receiver on the exact raw body before JSON parsing.',
      "def verify_webhook(raw_body: bytes, timestamp: str, signature: str, secret: str = WEBHOOK_SIGNING_SECRET_BASE64) -> bool:\n    if not isinstance(timestamp, str) or not isinstance(signature, str) or not isinstance(secret, str): return False\n    if not timestamp.isascii() or not timestamp.isdecimal(): return False\n    if abs(time.time() - int(timestamp)) > 300: return False\n    try: secret_bytes = base64.b64decode(secret, validate=True)\n    except (binascii.Error, ValueError): return False\n    expected = 'v1=' + hmac.new(secret_bytes, timestamp.encode('ascii') + b'.' + raw_body, hashlib.sha256).hexdigest()\n    return hmac.compare_digest(expected, signature)",
    ].join('\n'),
  };
}

export default function QuickStartPage() {
  const [language, setLanguage] = useState<Language>('curl');
  const [copyStatus, setCopyStatus] = useState<CopyStatus>(null);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const sessionQuery = useQuery<Session>({
    queryKey: ['session'],
    queryFn: async () => {
      const response = await fetch('/api/session', { cache: 'no-store' });
      if (!response.ok) throw new Error('Not signed in');
      return response.json();
    },
  });
  const projectId = sessionQuery.data?.selectedProjectId;
  const pipelinesQuery = useQuery({
    queryKey: ['published-pipelines', projectId],
    queryFn: getPublishedPipelines,
    enabled: Boolean(projectId),
  });
  const project = sessionQuery.data?.projects.find((candidate) => candidate.id === projectId);
  const pipeline = pipelinesQuery.data?.[0]?.slug ?? '<PUBLISHED_PIPELINE_SLUG>';
  const apiBaseUrl = sessionQuery.data?.apiBaseUrl ?? '<PUBLIC_API_BASE_URL>';
  const code = useMemo(
    () => examples(apiBaseUrl, pipeline, projectId ?? '<PROJECT_ID>'),
    [apiBaseUrl, pipeline, projectId],
  );

  async function copyExample() {
    try {
      await navigator.clipboard.writeText(code[language]);
      setCopyStatus({ kind: 'success', message: `${languageLabel(language)} example copied.` });
    } catch {
      setCopyStatus({ kind: 'error', message: 'Copy failed. Select the code and copy it manually.' });
    }
  }

  function selectLanguage(next: Language) {
    setLanguage(next);
    setCopyStatus(null);
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number | undefined;
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % languages.length;
    if (event.key === 'ArrowLeft') nextIndex = (index - 1 + languages.length) % languages.length;
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = languages.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    selectLanguage(languages[nextIndex]);
    tabRefs.current[nextIndex]?.focus();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8 pb-12">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Developer integration</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">API Quick Start</h1>
        <p className="mt-2 max-w-3xl text-muted-foreground">Upload directly to storage, finalize the asset, register a webhook, run a published pipeline, observe completion, download output, and verify the signed callback.</p>
      </header>
      <OnboardingChecklist />
      <section aria-labelledby="context-title" className="grid gap-4 rounded-xl border border-border bg-card p-5 sm:grid-cols-3">
        <div>
          <h2 id="context-title" className="font-semibold">Selected project</h2>
          <p className="mt-1 text-sm text-muted-foreground">{project?.name ?? 'Loading...'}</p>
          <code className="mt-2 block break-all text-xs text-primary">{projectId ?? 'Loading...'}</code>
        </div>
        <div>
          <h2 className="font-semibold">Published pipeline</h2>
          <p className="mt-1 text-sm text-muted-foreground">{pipelinesQuery.data?.[0]?.name ?? (pipelinesQuery.isLoading ? 'Loading...' : 'None published')}</p>
          <code className="mt-2 block break-all text-xs text-primary">{pipeline}</code>
        </div>
        <div>
          <h2 className="font-semibold">Public API base URL</h2>
          <code className="mt-1 block break-all text-sm text-primary">{apiBaseUrl}</code>
          <a href="/api/backend/openapi.json" target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
            Open generated OpenAPI endpoint <ExternalLink className="size-3.5" />
          </a>
          <p className="mt-1 text-xs text-muted-foreground">Opened through your authenticated dashboard session; backend credentials stay server-side.</p>
        </div>
      </section>
      {(sessionQuery.isError || pipelinesQuery.isError) && <div role="alert" className="rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">Quick Start context could not be loaded. Refresh before copying an example.</div>}
      {!pipelinesQuery.isLoading && pipeline.startsWith('<') && <div role="note" className="rounded-xl border border-amber-400/30 bg-amber-400/10 p-4 text-sm"><Link href="/pipelines" className="font-semibold text-amber-300 hover:underline">Publish a pipeline</Link> to replace the pipeline placeholder automatically.</div>}
      <section aria-labelledby="example-title" className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="flex flex-col gap-4 border-b border-border p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 id="example-title" className="font-semibold">End-to-end example</h2>
            <p className="mt-1 text-xs text-muted-foreground">Replace only explicit angle-bracket placeholders. Never paste secrets into the dashboard.</p>
          </div>
          <Button type="button" variant="outline" onClick={copyExample} disabled={!sessionQuery.data || sessionQuery.isError}><Copy /> Copy example</Button>
        </div>
        <div className="flex gap-1 border-b border-border px-4 pt-3" role="tablist" aria-label="Example language">
          {languages.map((item, index) => (
            <button
              key={item}
              ref={(node) => { tabRefs.current[index] = node; }}
              id={`example-tab-${item}`}
              type="button"
              role="tab"
              aria-controls={`example-panel-${item}`}
              aria-selected={language === item}
              tabIndex={language === item ? 0 : -1}
              onClick={() => selectLanguage(item)}
              onKeyDown={(event) => handleTabKeyDown(event, index)}
              className={`rounded-t-lg px-3 py-2 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${language === item ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}
            >
              {languageLabel(item)}
            </button>
          ))}
        </div>
        <div
          id={`example-panel-${language}`}
          role="tabpanel"
          aria-labelledby={`example-tab-${language}`}
          tabIndex={0}
          className="max-h-[42rem] overflow-auto bg-[#07101f] p-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
        >
          <pre className="min-w-max whitespace-pre text-xs leading-6 text-slate-200"><code>{code[language]}</code></pre>
        </div>
        {copyStatus && (
          <div role="status" aria-live="polite" data-copy-status={copyStatus.kind} className="flex items-center gap-2 border-t border-border px-4 py-3 text-sm text-muted-foreground">
            {copyStatus.kind === 'success'
              ? <Check className="size-4 text-emerald-400" aria-hidden="true" />
              : <CircleAlert className="size-4 text-destructive" aria-hidden="true" />}
            {copyStatus.message}
          </div>
        )}
      </section>
    </div>
  );
}
