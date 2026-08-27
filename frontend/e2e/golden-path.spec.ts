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
  const governancePipelineName = `Browser Governed ${suffix}`;
  const definition = JSON.parse(readFileSync(resolve(demoDir, 'pipeline.json'), 'utf8'));
  definition.slug = `browser-governed-${suffix}`;

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Turn media uploads into/i })).toBeVisible();
  await page.getByRole('link', { name: 'Create a workspace' }).click();
  await expect(page).toHaveURL(/\/signup$/);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Enter your email address.')).toBeVisible();
  await expect(page.getByText('Create a password.')).toBeVisible();
  await expect(page.getByText('Name your first project.')).toBeVisible();

  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Show password' }).click();
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Hide password' }).click();
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'password');
  await page.getByLabel('First project name').fill(projectName);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();

  await page.getByRole('link', { name: 'Skip to main content' }).focus();
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('link', { name: 'Build' })).toBeVisible();
  await page.getByRole('button', { name: 'Open navigation menu' }).click();
  const mobileNavigation = page.getByRole('navigation', { name: 'Mobile navigation' });
  await expect(mobileNavigation.getByRole('link')).toHaveCount(7);
  await expect(mobileNavigation.getByRole('link', { name: 'Pipelines' })).toBeVisible();
  await expect(page.locator('#mobile-project')).toBeVisible();
  await expect(page.getByText(email).last()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign out' }).last()).toBeVisible();
  await page.getByRole('button', { name: 'Close navigation menu' }).click();
  await page.setViewportSize({ width: 1280, height: 720 });

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
  await expect(page.getByRole('heading', { name: 'Sign in to Sluice' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('The email or password is incorrect.')).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();

  await page.getByRole('link', { name: 'Processor Market' }).click();
  await expect(page.getByRole('heading', { name: /Composable media capabilities/i })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(7);
  const processorMarket = page.getByRole('main');
  await expect(processorMarket.getByText('Transform', { exact: true })).toBeVisible();
  await expect(processorMarket.getByText('Optimize', { exact: true })).toBeVisible();
  await expect(processorMarket.getByText('Privacy', { exact: true })).toBeVisible();
  await expect(processorMarket.getByText('Governance', { exact: true })).toBeVisible();

  const resizeCard = page.getByRole('article').filter({ hasText: 'Image Resize' });
  await expect(resizeCard).toContainText('Recommended · v2.0.0');
  await expect(resizeCard.getByText('Accepts')).toBeVisible();
  await expect(resizeCard).toContainText('image/jpeg');
  await expect(resizeCard.getByRole('heading', { name: 'Pipeline step example' })).toBeVisible();
  await expect(resizeCard.locator('pre')).toContainText('"processor": "resize"');
  await expect(resizeCard.locator('pre')).toContainText('"config": {}');
  await resizeCard.getByText('Version history (1)').click();
  await expect(resizeCard.getByText('v1.0.0', { exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Pipelines' }).click();
  await page.getByLabel('Name').fill(pipelineName);
  await page.getByRole('button', { name: 'WebP delivery' }).click();
  await page.getByLabel('Input MIME types').fill('video/mp4');
  const compatibilityWarning = page.getByText('This processor cannot accept the preceding output. Reorder the steps or choose a compatible release.', { exact: true });
  await expect(compatibilityWarning).toBeVisible();
  await page.getByLabel('Input MIME types').fill('image/png');
  await expect(compatibilityWarning).toHaveCount(0);
  await page.getByLabel('allowedTypes').fill('image/png, image/jpeg');
  await page.getByLabel('quality number').fill('82');
  await expect(page.getByLabel('quality slider')).toHaveValue('82');
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('Discard your unsaved pipeline changes?');
    await dialog.dismiss();
  });
  await page.getByRole('button', { name: 'New pipeline' }).click();
  await expect(page.getByLabel('Name')).toHaveValue(pipelineName);
  await page.getByRole('button', { name: 'JSON', exact: true }).click();
  const guidedDefinition = JSON.parse(await page.getByLabel('Canonical pipeline JSON').inputValue());
  expect(guidedDefinition.steps.map((step: { processor: string }) => step.processor)).toEqual(['mime-validation', 'webp']);
  expect(guidedDefinition.steps[0].config.allowedTypes).toEqual(['image/png', 'image/jpeg']);
  expect(guidedDefinition.steps[1].config.quality).toBe(82);
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('status')).toContainText('Draft saved.');
  await expect(page.getByRole('button', { name: 'Publish immutable version' })).toBeEnabled();
  await page.getByRole('button', { name: 'Validate' }).click();
  await expect(page.getByRole('status')).toContainText('ready to publish');
  await page.getByRole('button', { name: 'Publish immutable version' }).click();
  await expect(page.getByRole('alertdialog', { name: 'Confirm pipeline publication' })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm publish' }).click();
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
  await expect(page.getByRole('heading', { name: 'Outputs and compression' })).toBeVisible();

  await page.getByRole('link', { name: 'Pipelines' }).click();
  await page.getByRole('button', { name: 'New pipeline' }).click();
  await page.getByLabel('Name').fill(governancePipelineName);
  await page.getByRole('button', { name: 'JSON', exact: true }).click();
  await page.getByLabel('Canonical pipeline JSON').fill(JSON.stringify(definition, null, 2));
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('status')).toContainText('Draft saved.');
  await page.getByRole('button', { name: 'Validate' }).click();
  await expect(page.getByRole('status')).toContainText('ready to publish');
  await page.getByRole('button', { name: 'Publish immutable version' }).click();
  await page.getByRole('button', { name: 'Confirm publish' }).click();

  await page.goto('/assets/upload');
  await page.locator('#file-upload').setInputFiles({ name: 'browser-governed.png', mimeType: 'image/png', buffer: image });
  const governancePipelineOption = page.getByLabel('Processing pipeline').locator('option', { hasText: governancePipelineName });
  await page.getByLabel('Processing pipeline').selectOption(await governancePipelineOption.getAttribute('value') ?? '');
  await page.getByRole('button', { name: 'Start Upload' }).click();
  await expect(page.getByRole('heading', { name: 'Upload Successful' })).toBeVisible({ timeout: 90_000 });

  await page.getByRole('link', { name: 'Runs' }).click();
  const governedRunLink = page.locator('tbody a').first();
  await expect(governedRunLink).toBeVisible({ timeout: 30_000 });
  await governedRunLink.click();
  await page.waitForURL(/\/jobs\/[0-9a-f-]{36}$/);
  const governedRunId = page.url().split('/').pop()!;
  await expect.poll(async () => {
    const response = await page.request.get(`/api/backend/runs/${governedRunId}`);
    return (await response.json()).status;
  }, { timeout: 120_000 }).toBe('COMPLETED');
  const governedRun = await (await page.request.get(`/api/backend/runs/${governedRunId}`)).json();
  expect(governedRun.governance.decision).toBe('ALLOW');
  await page.getByRole('link', { name: 'Governance' }).click();
  await expect(page.getByText('ALLOW', { exact: true })).toBeVisible();
});
