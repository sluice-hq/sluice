# Marketplace reference integration

This dependency-free Node 24 TypeScript example shows an external app submitting a listing image through Sluice's public API. It creates an upload, uploads bytes directly to storage, completes the upload, creates a run, polls its terminal state, downloads the derivative, and receives an authenticated terminal webhook.

## Local demonstration

Start the local product using the repository scripts, then run from the repository root:

```powershell
./scripts/demo-local.ps1
```

The local stack must explicitly allow the exact development callback URL `http://host.docker.internal:8787/webhooks`. The runner listens on port 8787 on the host; Docker must resolve `host.docker.internal` to that host. A host firewall must allow the container to reach this port. Do not enable private callback exceptions in hosted environments.

The PowerShell wrapper chooses `host.docker.internal` when the Compose API is running and `localhost` for source development. Both exact local URLs are supported by the explicit local-profile demo flag. Override with `-CallbackUrl` when needed; a hosted API always needs a publicly reachable callback.

The runner uses public signup/project/processor-enablement/API-key endpoints for a disposable local project. Its bearer token, API key and one-time webhook secret stay in memory and are never printed. Each run creates new project resources; use a disposable local environment. It generates an original synthetic 1400 by 900 PNG containing a synthetic metadata note, runs governance, resize and WebP encoding, checks the downloaded WebP structure and absence of metadata chunks, and reports byte counts, stripping evidence, policy outcome and verified callback event ID. Files are written to ignored `.sluice/demo-input.png` and `.sluice/demo-output.webp`.

The fixture deliberately uses an uncompressed PNG to demonstrate an unoptimized upload. Its savings are not a production benchmark. Local governance is a simulator, not real content moderation. A missing or invalid callback makes the demonstration fail even if polling succeeds.

## Existing project or hosted API

Set these environment variables before running `node examples/marketplace/demo.ts`:

| Variable | Purpose |
| --- | --- |
| `SLUICE_API_BASE_URL` | Public API base including `/api/v1` |
| `SLUICE_API_KEY` | Project API key, obtained through the dashboard or public management API |
| `SLUICE_PIPELINE` | Optional existing published pipeline slug with stable alias; must produce one WebP, strip metadata, and govern the image |
| `SLUICE_DEMO_CALLBACK_URL` | Publicly reachable HTTPS URL forwarded to this receiver's `/webhooks` path |
| `SLUICE_DEMO_LISTEN_PORT` | Receiver port, default 8787 |

Hosted runs require a supplied API key and a publicly reachable callback, such as your own HTTPS service forwarding to this listener. Local Docker hostnames cannot receive Azure callbacks. If creating a pipeline automatically, first enable `governance.content-safety@1.0.0`, `resize@2.0.0` and `webp@2.0.0` for the project. External services and credentials are not provisioned by this example.

## Reuse the helper

Import `SluiceClient` and `WebhookVerifier` from `client.ts`. `uploadAndRun` accepts bytes, filename, media type, pipeline slug, registered webhook endpoint ID, and caller-owned references. The example uses opaque seller/listing IDs instead of email addresses or usernames. References support correlation only; they never grant access to an asset.

Choose and persist one `operationId` for a logical upload. Pass a reusable `checkpoint` object to `uploadAndRun`; retries must reuse that checkpoint, ID, bytes and parameters. The helper records the asset ID and uploaded/completed stages so a lost completion/run response does not recreate a finalized asset or upload bytes again. For restart recovery, supply an asynchronous `saveCheckpoint` callback and reload its saved value into `checkpoint`. Checkpoints contain IDs, stage flags and a request fingerprint, not API keys or signed storage URLs. Different uploads need different IDs/checkpoints. The helper rejects changed parameters and surfaces HTTP failures without automatically retrying validation failures, permission errors or conflicting idempotency keys. Inspect terminal run status and error code before consuming outputs.

Webhook verification uses the base64-decoded signing secret, HMAC-SHA256 over `timestamp + "." + exact raw request bytes`, a constant-time comparison and a five-minute timestamp window. Parse JSON only after authentication. Duplicate authenticated events are acknowledged without repeating effects. The example replay cache is in memory and bounded by time; a real app must transactionally persist event IDs and business effects so restarts and multiple instances remain safe.

## Focused tests

```powershell
node --test examples/marketplace/client.test.ts
```

These tests need no server, Docker, API key or network access. The real demonstration separately verifies storage, pipeline processing and webhook delivery end to end.
