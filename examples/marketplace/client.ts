import { createHash, createHmac, timingSafeEqual } from 'node:crypto';

export type Asset = { id: string; size: number; contentType: string; filename: string };
export type Run = {
  id: string; status: string; outputs: Asset[];
  error?: { code: string; message: string };
  governance?: { decision: string; provider: string; policyVersion: string };
  steps: { stepId: string; metadata?: Record<string, unknown> }[];
};
export type UploadRunCheckpoint = { fingerprint?: string; assetId?: string; uploaded?: boolean; completed?: boolean };
export class SluiceError extends Error {
  status: number;
  path: string;
  constructor(status: number, path: string) {
    // Response bodies may include sensitive values. Keep errors useful without logging them.
    super(`Sluice request ${path} failed with HTTP ${status}`);
    this.status = status; this.path = path;
  }
}

/** Reference helper, deliberately not a separately released SDK. Node 24 or newer. */
export class SluiceClient {
  private base: string;
  private apiKey: string;
  private transport: typeof fetch;
  constructor(baseUrl: string, apiKey: string, transport: typeof fetch = fetch) {
    this.apiKey = apiKey; this.transport = transport;
    const url = new URL(baseUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new Error('API base must be an HTTP(S) URL without credentials, query or fragment');
    }
    this.base = baseUrl.replace(/\/$/, '');
  }

  async request<T>(path: string, method = 'GET', body?: unknown, key?: string): Promise<T> {
    if (!path.startsWith('/') || path.startsWith('//')) throw new Error('Expected a relative API path');
    const response = await this.transport(`${this.base}${path}`, {
      method, redirect: 'error', signal: AbortSignal.timeout(30_000),
      headers: { 'X-API-Key': this.apiKey, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(key ? { 'Idempotency-Key': key } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw new SluiceError(response.status, path.split('?')[0]);
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
  }

  async uploadAndRun(input: {
    bytes: Uint8Array; filename: string; contentType: string; pipeline: string;
    externalSubjectId: string; externalReference: string; operationId: string; webhookEndpointId: string;
    checkpoint?: UploadRunCheckpoint; saveCheckpoint?: (checkpoint: UploadRunCheckpoint) => Promise<void>;
  }): Promise<Run> {
    // Preserve operationId when retrying the SAME logical operation and exact request body.
    const checkpoint = input.checkpoint ?? {};
    const fingerprint = createHash('sha256').update(JSON.stringify([
      input.operationId, input.filename, input.contentType, input.pipeline, input.externalSubjectId,
      input.externalReference, input.webhookEndpointId, createHash('sha256').update(input.bytes).digest('hex'),
    ])).digest('hex');
    if (checkpoint.fingerprint && checkpoint.fingerprint !== fingerprint) throw new Error('Checkpoint belongs to different upload parameters');
    checkpoint.fingerprint = fingerprint;
    const save = async () => { await input.saveCheckpoint?.({ ...checkpoint }); };
    if (!checkpoint.uploaded && !checkpoint.completed) {
      const upload = await this.request<{ assetId: string; uploadUrl: string }>('/uploads', 'POST', {
      filename: input.filename, contentType: input.contentType, size: input.bytes.length,
      externalSubjectId: input.externalSubjectId, externalReference: input.externalReference,
      }, `${input.operationId}-create-upload`);
      checkpoint.assetId = upload.assetId;
      await save();
      // Storage URL has its own scoped signature. Never attach the API key to it.
      const response = await this.transport(upload.uploadUrl, {
      method: 'PUT', redirect: 'error', signal: AbortSignal.timeout(30_000),
      headers: { 'x-ms-blob-type': 'BlockBlob', 'Content-Type': input.contentType },
      body: new Uint8Array(input.bytes).buffer,
      });
      if (!response.ok) throw new Error(`Storage upload failed with HTTP ${response.status}`);
      checkpoint.uploaded = true;
      await save();
    }
    if (!checkpoint.assetId) throw new Error('Upload checkpoint is missing its asset ID');
    if (!checkpoint.completed) {
      await this.request(`/uploads/${checkpoint.assetId}/complete`, 'POST', undefined, `${input.operationId}-complete`);
      checkpoint.completed = true;
      await save();
    }
    return this.request<Run>('/runs', 'POST', {
      pipeline: input.pipeline, alias: 'stable', inputAssetId: checkpoint.assetId,
      callback: { webhookEndpointId: input.webhookEndpointId },
    }, `${input.operationId}-run`);
  }

  async waitForRun(id: string, timeoutMs = 120_000, pollMs = 500): Promise<Run> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const run = await this.request<Run>(`/runs/${encodeURIComponent(id)}`);
      if (!['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(run.status)) return run;
      await new Promise(resolve => setTimeout(resolve, Math.min(pollMs, Math.max(0, deadline - Date.now()))));
    }
    throw new Error(`Run ${id} did not finish within ${timeoutMs}ms`);
  }

  async download(assetId: string): Promise<Buffer> {
    const result = await this.request<{ downloadUrl: string }>(`/assets/${encodeURIComponent(assetId)}/download`);
    const response = await this.transport(result.downloadUrl, { redirect: 'error', signal: AbortSignal.timeout(30_000) });
    if (!response.ok) throw new Error(`Storage download failed with HTTP ${response.status}`);
    return Buffer.from(await response.arrayBuffer());
  }
}

export type TerminalEvent = { eventId: string; runId: string; status: string; projectId: string };
/** Authenticate raw bytes before parsing. Repeated valid events are acknowledged, never reprocessed. */
export class WebhookVerifier {
  private seen = new Map<string, { digest: string; expires: number }>();
  private secret: string;
  private toleranceSeconds: number;
  constructor(secret: string, toleranceSeconds = 300) {
    this.secret = secret; this.toleranceSeconds = toleranceSeconds;
    if (Buffer.from(secret, 'base64').length !== 32) throw new Error('Expected the endpoint signing secret');
  }
  accept(body: Buffer, headers: Record<string, string | string[] | undefined>, now = Math.floor(Date.now() / 1000)) {
    const timestamp = headers['x-sluice-timestamp'];
    const signature = headers['x-sluice-signature'];
    const eventId = headers['x-sluice-event-id'];
    if (typeof timestamp !== 'string' || !/^\d{1,12}$/.test(timestamp)
        || Math.abs(now - Number(timestamp)) > this.toleranceSeconds
        || typeof signature !== 'string' || !/^v1=[a-f0-9]{64}$/.test(signature)
        || typeof eventId !== 'string') throw new Error('Invalid webhook authentication');
    const digest = createHmac('sha256', Buffer.from(this.secret, 'base64'))
      .update(`${timestamp}.`).update(body).digest();
    if (!timingSafeEqual(digest, Buffer.from(signature.slice(3), 'hex'))) throw new Error('Invalid webhook signature');
    const event = JSON.parse(body.toString('utf8')) as TerminalEvent;
    if (event.eventId !== eventId || typeof event.runId !== 'string' || typeof event.projectId !== 'string'
        || !['COMPLETED', 'FAILED', 'REVIEW_REQUIRED'].includes(event.status)) throw new Error('Invalid terminal event');
    for (const [id, entry] of this.seen) if (entry.expires < now) this.seen.delete(id);
    const bodyDigest = createHmac('sha256', Buffer.from(this.secret, 'base64')).update(body).digest('hex');
    const prior = this.seen.get(eventId);
    if (prior && prior.digest !== bodyDigest) throw new Error('Webhook event ID reused with different body');
    if (!prior) this.seen.set(eventId, { digest: bodyDigest, expires: now + this.toleranceSeconds * 2 });
    return { event, duplicate: !!prior };
  }
}
