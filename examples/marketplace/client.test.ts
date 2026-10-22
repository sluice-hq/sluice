import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { inflateSync } from 'node:zlib';
import { SluiceClient, SluiceError, WebhookVerifier } from './client.ts';
import { listingFixture, inspectWebp, metadataMarker } from './fixture.ts';

const secret = Buffer.alloc(32, 17).toString('base64');
const now = 1800000000;
const event = { eventId: 'event-1', runId: 'run-1', status: 'COMPLETED', projectId: 'project-1' };
const body = Buffer.from(JSON.stringify(event));
function signed(payload = body, timestamp = now) {
  return { 'x-sluice-event-id': 'event-1', 'x-sluice-timestamp': String(timestamp),
    'x-sluice-signature': `v1=${createHmac('sha256', Buffer.from(secret, 'base64')).update(`${timestamp}.`).update(payload).digest('hex')}` };
}
test('webhook authenticates exact body and deduplicates retries', () => {
  const verifier = new WebhookVerifier(secret);
  assert.equal(verifier.accept(body, signed(), now).duplicate, false);
  assert.equal(verifier.accept(body, signed(), now).duplicate, true);
  assert.throws(() => verifier.accept(Buffer.from('{}'), signed(), now), /signature/);
  assert.throws(() => verifier.accept(body, signed(body, now - 301), now), /authentication/);
  assert.throws(() => verifier.accept(body, signed(body, now + 301), now), /authentication/);
});
test('webhook rejects wrong secret, malformed signatures, unsigned bodies and event ID mismatch', () => {
  assert.throws(() => new WebhookVerifier(Buffer.alloc(32, 18).toString('base64')).accept(body, signed(), now), /signature/);
  const verifier = new WebhookVerifier(secret);
  assert.throws(() => verifier.accept(body, { ...signed(), 'x-sluice-signature': 'v1=ab' }, now), /authentication/);
  assert.throws(() => verifier.accept(body, {}, now), /authentication/);
  assert.throws(() => verifier.accept(body, { ...signed(), 'x-sluice-event-id': 'other' }, now), /terminal/);
  const changed = Buffer.from(JSON.stringify({ ...event, status: 'FAILED' }));
  verifier.accept(body, signed(), now);
  assert.throws(() => verifier.accept(changed, signed(changed), now), /reused/);
});
test('HTTP errors preserve status without exposing response or credentials', async () => {
  const client = new SluiceClient('https://example.test/api/v1', 'super-secret', async () => new Response('secret-body', { status: 409 }));
  await assert.rejects(client.request('/uploads', 'POST', {}, 'operation'), (error: SluiceError) => error.status === 409 && !error.message.includes('secret'));
  assert.throws(() => new SluiceClient('https://user:password@example.test', 'key'), /base/);
});
test('upload flow uses stable idempotency keys and never sends API key to storage', async () => {
  const requests: { url: string; init: RequestInit }[] = [];
  const client = new SluiceClient('https://api.example.test/api/v1', 'private-key', async (url, init) => {
    requests.push({ url: String(url), init: init! });
    if (String(url).endsWith('/uploads')) return Response.json({ assetId: 'asset-1', uploadUrl: 'https://storage.example.test/blob?sig=scoped' });
    if (String(url).includes('storage.example')) return new Response(null, { status: 201 });
    if (String(url).endsWith('/runs')) return Response.json({ id: 'run-1', status: 'QUEUED' });
    return Response.json({ id: 'asset-1' });
  });
  const input = { bytes: Buffer.from('data'), filename: 'a.png', contentType: 'image/png', pipeline: 'listing', externalSubjectId: 'seller-1', externalReference: 'listing-1', operationId: 'op-1', webhookEndpointId: 'endpoint-1', checkpoint: {} };
  await client.uploadAndRun(input); await client.uploadAndRun(input);
  assert.equal(requests.length, 5, 'A completed upload must not be recreated or overwritten during a retry');
  assert.deepEqual(requests[3].init.headers, requests[4].init.headers);
  assert.equal((requests[1].init.headers as Record<string, string>)['X-API-Key'], undefined);
  assert.equal((requests[0].init.headers as Record<string, string>)['Idempotency-Key'], 'op-1-create-upload');
  assert.equal(JSON.parse(requests[3].init.body as string).callback.webhookEndpointId, 'endpoint-1');
});
test('lost completion response resumes at completion rather than recreating a finalized asset', async () => {
  const paths: string[] = [];
  let completed = false, lost = false;
  const client = new SluiceClient('https://api.example.test/api/v1', 'key', async (url) => {
    const path = new URL(String(url)).pathname; paths.push(path);
    if (path.endsWith('/uploads')) return completed ? new Response(null, { status: 409 }) : Response.json({ assetId: 'a', uploadUrl: 'https://storage.example.test/blob' });
    if (path.endsWith('/complete')) {
      completed = true;
      if (!lost) { lost = true; throw new TypeError('Connection lost after commit'); }
      return Response.json({ id: 'a' });
    }
    if (path.endsWith('/runs')) return Response.json({ id: 'run', status: 'QUEUED' });
    return new Response(null, { status: 201 });
  });
  const checkpoint = {};
  const input = { bytes: Buffer.from('data'), filename: 'a.png', contentType: 'image/png', pipeline: 'listing', externalSubjectId: 'seller-1', externalReference: 'listing-1', operationId: 'op-1', webhookEndpointId: 'endpoint-1', checkpoint };
  await assert.rejects(client.uploadAndRun(input), /Connection lost/);
  assert.equal((await client.uploadAndRun(input)).id, 'run');
  assert.equal(paths.filter(path => path.endsWith('/uploads')).length, 1);
  assert.equal(paths.filter(path => path.endsWith('/complete')).length, 2);
  assert(!JSON.stringify(checkpoint).includes('https://'), 'Persisted checkpoint must not retain signed storage URLs');
  await assert.rejects(client.uploadAndRun({ ...input, bytes: Buffer.from('different') }), /different upload parameters/);
});
test('polling returns terminal failures rather than treating them as successful outputs', async () => {
  const client = new SluiceClient('https://example.test', 'key', async () => Response.json({ id: 'run', status: 'FAILED', error: { code: 'invalid_image' } }));
  assert.equal((await client.waitForRun('run')).status, 'FAILED');
  await assert.rejects(client.waitForRun('run', 0), /did not finish/);
});
test('synthetic fixture is reproducible, bounded, contains metadata and valid pixel data', () => {
  const fixture = listingFixture();
  assert.deepEqual(fixture, listingFixture());
  assert.equal(fixture.readUInt32BE(16), 1400); assert.equal(fixture.readUInt32BE(20), 900);
  assert(fixture.length < 4 * 1024 * 1024); assert(fixture.includes(Buffer.from(metadataMarker)));
  // Keep the local simulator's SHA256 fixture score below reviewThreshold=4.
  assert.equal(createHash('sha256').update(fixture).digest()[0] % 8, 3);
  let offset = 8, data: Buffer | undefined;
  while (offset < fixture.length) { const size = fixture.readUInt32BE(offset); if (fixture.toString('ascii', offset + 4, offset + 8) === 'IDAT') data = fixture.subarray(offset + 8, offset + 8 + size); offset += size + 12; }
  assert.equal(inflateSync(data!).length, (1400 * 3 + 1) * 900);
});
test('WebP inspection rejects incomplete or masquerading files', () => {
  assert.throws(() => inspectWebp(Buffer.from('not a webp')), /WebP/);
  const bytes = Buffer.alloc(22); bytes.write('RIFF'); bytes.writeUInt32LE(14, 4); bytes.write('WEBP', 8); bytes.write('VP8 ', 12); bytes.writeUInt32LE(10, 16);
  assert.throws(() => inspectWebp(bytes), /Truncated/);
});
