import { createServer } from 'node:http';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { SluiceClient, WebhookVerifier, type TerminalEvent } from './client.ts';
import { listingFixture, inspectWebp } from './fixture.ts';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const base = process.env.SLUICE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const callback = process.env.SLUICE_DEMO_CALLBACK_URL ?? 'http://host.docker.internal:8787/webhooks';
const callbackUrl = new URL(callback);
const port = Number(process.env.SLUICE_DEMO_LISTEN_PORT ?? 8787);
const operation = `listing-${randomUUID()}`;
const events = new Map<string, TerminalEvent>();
let verifier: WebhookVerifier | undefined;
const server = createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== callbackUrl.pathname) { response.writeHead(404).end(); return; }
  try {
    const chunks: Buffer[] = []; let size = 0;
    for await (const chunk of request) {
      size += chunk.length;
      if (size > 128 * 1024) { response.writeHead(413).end(); request.destroy(); return; }
      chunks.push(chunk);
    }
    if (!verifier) { response.writeHead(503).end(); return; }
    const accepted = verifier.accept(Buffer.concat(chunks), request.headers);
    if (!accepted.duplicate) events.set(accepted.event.runId, accepted.event);
    response.writeHead(204).end();
  } catch { response.writeHead(401).end(); }
});
server.requestTimeout = 10_000;
server.headersTimeout = 10_000;

async function bootstrap() {
  if (process.env.SLUICE_API_KEY) return new SluiceClient(base, process.env.SLUICE_API_KEY);
  if (!['localhost', '127.0.0.1'].includes(new URL(base).hostname)) throw new Error('Hosted use requires SLUICE_API_KEY; automatic signup is local-only');
  const response = await fetch(`${base}/auth/signup`, {
    method: 'POST', signal: AbortSignal.timeout(30_000), redirect: 'error', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `demo-${randomUUID()}@example.com`, password: `Demo-${randomUUID()}!`, projectName: 'Marketplace reference demo' }),
  });
  if (!response.ok) throw new Error(`Local signup failed with HTTP ${response.status}`);
  const signup = await response.json() as { token: string; selectedProjectId: string };
  const human = async (path: string, method: string, body?: unknown) => {
    const result = await fetch(`${base}${path}`, {
      method, signal: AbortSignal.timeout(30_000), redirect: 'error',
      headers: { Authorization: `Bearer ${signup.token}`, 'X-Project-ID': signup.selectedProjectId, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!result.ok) throw new Error(`Local project setup failed with HTTP ${result.status}`);
    return result;
  };
  for (const [slug, version] of [['governance.content-safety', '1.0.0'], ['resize', '2.0.0'], ['webp', '2.0.0']]) {
    await human(`/projects/${signup.selectedProjectId}/processor-releases/${slug}/versions/${version}`, 'PUT');
  }
  const created = await (await human(`/projects/${signup.selectedProjectId}/api-keys`, 'POST', { name: 'marketplace-reference' })).json() as { value: string };
  return new SluiceClient(base, created.value);
}

try {
  await new Promise<void>((resolveListen, reject) => { server.once('error', reject); server.listen(port, '0.0.0.0', resolveListen); });
  const client = await bootstrap();
  const endpoint = await client.request<{ id: string; secret: string }>('/webhook-endpoints', 'POST', { callbackUrl: callback });
  verifier = new WebhookVerifier(endpoint.secret);
  let pipelineSlug = process.env.SLUICE_PIPELINE;
  if (!pipelineSlug) {
    const definition = JSON.parse(await readFile(resolve(root, 'demo/pipeline.json'), 'utf8'));
    definition.slug = `marketplace-${randomUUID().slice(0, 8)}`;
    definition.steps.find((step: { id: string }) => step.id === 'resize').config = { maxWidth: 1000, maxHeight: 1000, allowUpscale: false };
    const created = await client.request<{ draft: { revision: number } }>('/pipelines', 'POST', {
      name: 'Marketplace listing image', description: 'Synthetic reference integration with verified callback', definition,
    });
    await client.request(`/pipelines/${definition.slug}/publish`, 'POST', { revision: created.draft.revision });
    pipelineSlug = definition.slug;
  }
  const bytes = listingFixture();
  const submission = { bytes, filename: 'synthetic-listing.png', contentType: 'image/png',
    pipeline: pipelineSlug!, operationId: operation, externalSubjectId: 'seller_demo_001', externalReference: operation,
    webhookEndpointId: endpoint.id, checkpoint: {} };
  const createdRun = await client.uploadAndRun(submission);
  const replay = await client.uploadAndRun(submission);
  if (replay.id !== createdRun.id) throw new Error('Retry created a second logical run');
  const run = await client.waitForRun(createdRun.id);
  if (run.status !== 'COMPLETED') throw new Error(`Run ${run.id} ended ${run.status} (${run.error?.code ?? 'no error code'})`);
  const assets = await client.request<{ content: { externalSubjectId: string; externalReference: string }[] }>(
    `/assets?externalSubjectId=seller_demo_001&externalReference=${encodeURIComponent(operation)}`);
  if (!assets.content.length || assets.content.some(asset => asset.externalSubjectId !== 'seller_demo_001' || asset.externalReference !== operation)) {
    throw new Error('Caller-owned listing references were not returned by asset discovery');
  }
  if (run.outputs.length !== 1 || run.outputs[0].contentType !== 'image/webp') throw new Error('Expected exactly one WebP output');
  const output = await client.download(run.outputs[0].id);
  const inspection = inspectWebp(output);
  if (!inspection.metadataAbsent || !run.steps.some(step => step.metadata?.metadataStripped === true)) throw new Error('Metadata stripping evidence is missing');
  if (output.length !== run.outputs[0].size || output.length >= bytes.length) throw new Error('Output byte evidence does not show a smaller derivative');
  if (run.governance?.decision !== 'ALLOW') throw new Error('Listing did not receive ALLOW policy outcome');
  const deadline = Date.now() + 60_000;
  while (!events.has(run.id) && Date.now() < deadline) await new Promise(resolveWait => setTimeout(resolveWait, 250));
  const event = events.get(run.id);
  if (!event || event.status !== run.status) throw new Error('No matching authenticated terminal webhook arrived within 60 seconds');
  const artifactDir = resolve(root, '.sluice'); await mkdir(artifactDir, { recursive: true });
  await writeFile(resolve(artifactDir, 'demo-input.png'), bytes); await writeFile(resolve(artifactDir, 'demo-output.webp'), output);
  console.log(JSON.stringify({ result: 'PASS', pipeline: pipelineSlug, runId: run.id, status: run.status, idempotentReplay: true,
    inputBytes: bytes.length, outputBytes: output.length, bytesSaved: bytes.length - output.length,
    reductionPercent: Number(((1 - output.length / bytes.length) * 100).toFixed(1)),
    fixture: 'Synthetic unoptimized PNG, not a representative compression benchmark',
    metadataStripped: inspection.metadataAbsent, governance: run.governance,
    webhook: { verified: true, eventId: event.eventId }, outputFile: resolve(artifactDir, 'demo-output.webp') }, null, 2));
} catch (error) {
  console.error(error instanceof Error ? error.message : 'Reference integration failed'); process.exitCode = 1;
} finally {
  server.closeAllConnections(); await new Promise<void>(resolveClose => server.close(() => resolveClose()));
}
