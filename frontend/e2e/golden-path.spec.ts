import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const demoDir = resolve(process.cwd(), '..', 'demo');

test('developer completes the local dashboard golden path', async ({ page, context }) => {
  const suffix = Date.now().toString();
  const email = `browser-${suffix}@example.com`;
  const password = 'browser-password-2026';
  const projectName = `Browser ${suffix}`;
  const pipelineName = `Browser WebP ${suffix}`;
  const definition = JSON.parse(readFileSync(resolve(demoDir, 'pipeline.json'), 'utf8'));
  definition.slug = `browser-webp-${suffix}`;

  await page.goto('/signup');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Enter your email address.')).toBeVisible();
  await expect(page.getByText('Create a password.')).toBeVisible();
  await expect(page.getByText('Name your first project.')).toBeVisible();

  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByLabel('First project name').fill(projectName);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();

  const cookies = await context.cookies();
  expect(cookies.find((cookie) => cookie.name === 'sluice_session')?.httpOnly).toBe(true);
  expect(cookies.find((cookie) => cookie.name === 'sluice_csrf')?.httpOnly).toBe(false);

  const rejected = await page.request.post('/api/backend/projects', { data: { name: 'Missing CSRF' } });
  expect(rejected.status()).toBe(403);
  expect((await rejected.json()).code).toBe('csrf_rejected');

  await page.getByRole('link', { name: 'Settings' }).click();
  await page.getByPlaceholder('New project name').fill(`Selected ${suffix}`);
  await page.getByRole('button', { name: 'Create project' }).click();
  await expect(page.getByRole('main').getByText(`Selected ${suffix}`).first()).toBeVisible();

  await page.getByPlaceholder('Key name, e.g. storefront-dev').fill('browser-demo');
  await page.getByRole('button', { name: 'Create API key' }).click();
  await expect(page.getByText('Copy this key now. It cannot be shown again.')).toBeVisible();
  const revealed = await page.locator('input[readonly]').inputValue();
  expect(revealed).toMatch(/^sl_live_/);
  await page.reload();
  await expect(page.locator('input[readonly]')).toHaveCount(0);
  await expect(page.getByText('browser-demo')).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Revoke' }).click();
  await expect(page.getByText('Revoked')).toBeVisible();

  await page.getByTitle('Sign out').click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('The email or password is incorrect.')).toBeVisible();
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();

  await page.getByRole('link', { name: 'Pipelines' }).click();
  await page.getByLabel('Name').fill(pipelineName);
  await page.getByRole('button', { name: 'JSON', exact: true }).click();
  await page.getByLabel('Canonical pipeline JSON').fill(JSON.stringify(definition, null, 2));
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('status')).toContainText('Draft saved.');
  await expect(page.getByRole('button', { name: 'Publish immutable version' })).toBeEnabled();
  await page.getByRole('button', { name: 'Validate' }).click();
  await expect(page.getByRole('status')).toContainText('ready to publish');
  await page.getByRole('button', { name: 'Publish immutable version' }).click();
  await expect(page.getByRole('status')).toContainText('Published an immutable version');

  await page.goto('/assets/upload');
  const image = Buffer.from(readFileSync(resolve(demoDir, 'sample.png.base64'), 'utf8').trim(), 'base64');
  await page.locator('#file-upload').setInputFiles({ name: 'browser-demo.png', mimeType: 'image/png', buffer: image });
  await page.getByLabel('Processing pipeline').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Start Upload' }).click();
  await expect(page.getByRole('heading', { name: 'Upload Successful' })).toBeVisible({ timeout: 90_000 });

  await page.getByRole('link', { name: 'Runs' }).click();
  const runLink = page.locator('tbody a').first();
  await expect(runLink).toBeVisible({ timeout: 30_000 });
  await runLink.click();
  await page.waitForURL(/\/jobs\/[0-9a-f-]{36}$/);
  const runId = page.url().split('/').pop()!;
  await expect.poll(async () => {
    const response = await page.request.get(`/api/backend/runs/${runId}`);
    return (await response.json()).status;
  }, { timeout: 120_000 }).toBe('COMPLETED');

  const runResponse = await page.request.get(`/api/backend/runs/${runId}`);
  const run = await runResponse.json();
  expect(run.outputs).toHaveLength(1);
  expect(run.outputs[0].contentType).toBe('image/webp');
  expect(run.governance.decision).toBe('ALLOW');
  await expect(page.getByRole('heading', { name: 'Outputs and compression' })).toBeVisible();

  await page.getByRole('link', { name: 'Governance' }).click();
  await expect(page.getByText('ALLOW', { exact: true })).toBeVisible();
});
