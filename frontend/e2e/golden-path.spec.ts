import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const demoDir = resolve(process.cwd(), '..', 'demo');

function dateTimeLocal(date: Date): string {
  const part = (number: number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(date.getMinutes())}`;
}

test('developer completes the local dashboard golden path', async ({ page, context }) => {
  test.setTimeout(300_000);
  const suffix = Date.now().toString();
  const email = `browser-${suffix}@example.com`;
  const password = 'browser-password-2026';
  const projectName = `Browser ${suffix}`;
  const pipelineName = `Browser WebP ${suffix}`;
  const governancePipelineName = `Browser Governed ${suffix}`;
  const definition = JSON.parse(readFileSync(resolve(demoDir, 'pipeline.json'), 'utf8'));
  definition.slug = `browser-governed-${suffix}`;

  const crossSiteRecovery = await page.request.post('/api/session/recovery', {
    headers: { Origin: 'https://attacker.example', 'Sec-Fetch-Site': 'cross-site' },
    data: { email: `target-${suffix}@example.com` },
  });
  expect(crossSiteRecovery.status()).toBe(403);
  expect((await crossSiteRecovery.json()).code).toBe('csrf_rejected');

  const crossSiteLogin = await page.request.post('/api/session/login', {
    headers: { Origin: 'https://attacker.example', 'Sec-Fetch-Site': 'cross-site' },
    data: { email: `target-${suffix}@example.com`, password },
  });
  expect(crossSiteLogin.status()).toBe(403);

  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot your password?' }).click();
  await expect(page.getByRole('heading', { name: 'Reset your password' })).toBeVisible();
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Enter a valid email address.' })).toBeVisible();
  await page.getByLabel('Email address').fill(`missing-${suffix}@example.com`);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByRole('status')).toContainText('If an account exists');

  await page.goto(`/reset-password?token=invalid-${suffix}`);
  await page.getByLabel('New password', { exact: true }).fill('replacement-password-2026');
  await page.getByLabel('Confirm new password', { exact: true }).fill('different-password-2026');
  await page.getByRole('button', { name: 'Reset password' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'The passwords do not match.' })).toBeVisible();
  await page.getByLabel('Confirm new password', { exact: true }).fill('replacement-password-2026');
  await page.getByRole('button', { name: 'Reset password' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'invalid or has expired' })).toBeVisible();

  await page.route('**/api/session/reset', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Password reset completed.' }),
    });
  });
  await page.goto(`/reset-password?token=browser-success-${suffix}`);
  await page.getByLabel('New password', { exact: true }).fill('replacement-password-2026');
  await page.getByLabel('Confirm new password', { exact: true }).fill('replacement-password-2026');
  await page.getByRole('button', { name: 'Reset password' }).click();
  await expect(page.getByRole('status')).toContainText('Your password was reset');
  await page.unroute('**/api/session/reset');

  await page.goto(`/verify-email/confirm?token=invalid-${suffix}`);
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'invalid or has expired' })).toBeVisible();

  await page.route('**/api/session/verification/confirm', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Email verification completed.' }),
    });
  });
  await page.goto(`/verify-email/confirm?token=browser-success-${suffix}`);
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByRole('status')).toContainText('Your email is verified');
  await page.unroute('**/api/session/verification/confirm');

  await page.goto('/');
  const anonymousOpenApi = await page.request.get('/api/backend/openapi.json');
  expect(anonymousOpenApi.status()).toBe(401);
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
  await expect(page).toHaveURL(new RegExp(`/verify-email\\?email=${encodeURIComponent(email)}`));
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('We sent a verification link');
  await page.getByRole('link', { name: 'Open dashboard' }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Start your first integration' })).toBeVisible();
  const initialChecklist = page.getByRole('region', { name: 'Start your first integration' });
  await expect(initialChecklist.getByRole('listitem').filter({ hasText: 'Create an API key' }).getByLabel('Not complete')).toBeVisible();
  await expect(initialChecklist.getByRole('listitem').filter({ hasText: 'Publish a pipeline' }).getByLabel('Not complete')).toBeVisible();
  await expect(initialChecklist.getByRole('listitem').filter({ hasText: 'Complete your first run' }).getByLabel('Not complete')).toBeVisible();

  await page.getByRole('link', { name: 'Skip to main content' }).focus();
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('link', { name: 'Build' })).toBeVisible();
  await page.getByRole('button', { name: 'Open navigation menu' }).click();
  const mobileNavigation = page.getByRole('navigation', { name: 'Mobile navigation' });
  await expect(mobileNavigation.getByRole('link')).toHaveCount(8);
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
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('API key copied to clipboard.');
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: async () => { throw new Error('Clipboard denied for test'); } },
    });
  });
  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Clipboard unavailable. The key is selected; copy it manually.');
  await expect(page.locator('#revealed-api-key')).toBeFocused();
  await page.reload();
  await expect(page.locator('input[readonly]')).toHaveCount(0);
  await expect(page.getByText('browser-demo')).toBeVisible();
  await page.getByRole('link', { name: 'API Quick Start' }).click();
  await expect(page.getByRole('heading', { name: 'API Quick Start' })).toBeVisible();
  await expect(page.getByRole('main').getByText(`Selected ${suffix}`, { exact: true })).toBeVisible();
  const sessionResponse = await page.request.get('/api/session');
  expect(sessionResponse.status()).toBe(200);
  const session = await sessionResponse.json();
  expect(() => new URL(session.apiBaseUrl)).not.toThrow();
  expect(session.apiBaseUrl.endsWith('/')).toBe(false);
  await expect(page.locator('pre')).toContainText(session.apiBaseUrl);
  await expect(page.getByRole('note')).toContainText('Publish a pipeline');
  await expect(page.locator('pre')).toContainText('<PUBLISHED_PIPELINE_SLUG>');
  const openApiLink = page.getByRole('link', { name: /Open generated OpenAPI endpoint/ });
  await expect(openApiLink).toHaveAttribute('href', '/api/backend/openapi.json');
  const authenticatedOpenApi = await page.request.get('/api/backend/openapi.json');
  expect(authenticatedOpenApi.status()).toBe(200);
  expect((await authenticatedOpenApi.json()).openapi).toMatch(/^3\./);
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.getByRole('button', { name: 'Copy example' }).click();
  await expect(page.getByRole('status')).toHaveText('cURL example copied.');
  await expect(page.getByRole('status')).toHaveAttribute('data-copy-status', 'success');
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: async () => { throw new Error('Clipboard denied for test'); } },
    });
  });
  await page.getByRole('button', { name: 'Copy example' }).click();
  await expect(page.getByRole('status')).toHaveText('Copy failed. Select the code and copy it manually.');
  await expect(page.getByRole('status')).toHaveAttribute('data-copy-status', 'error');

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
  await page.locator('#desktop-project').selectOption({ label: `Selected ${suffix}` });
  await expect(page.locator('#desktop-project')).toHaveValue(/.+/);
  await expect(page.locator('#desktop-project')).toHaveValue(session.selectedProjectId);

  const authenticatedCookies = await context.cookies();
  const csrfToken = authenticatedCookies.find((cookie) => cookie.name === 'sluice_csrf')?.value;
  expect(csrfToken).toBeTruthy();

  const catalogFailurePage = await context.newPage();
  await catalogFailurePage.route('**/api/backend/projects/*/processor-releases', async (route) => {
    await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ detail: 'Catalog unavailable' }) });
  });
  await catalogFailurePage.goto('/pipelines');
  await expect(catalogFailurePage.getByRole('alert').filter({ hasText: 'Enabled processor releases could not be loaded.' })).toBeVisible();
  await expect(catalogFailurePage.getByRole('heading', { name: 'Enable a processor to start building' })).toHaveCount(0);
  await catalogFailurePage.goto('/processors');
  await expect(catalogFailurePage.getByRole('alert').filter({ hasText: 'catalog could not be loaded' })).toBeVisible();
  await expect(catalogFailurePage.getByText('No processor releases are published yet.')).toHaveCount(0);
  await catalogFailurePage.close();

  await page.getByRole('link', { name: 'Pipelines' }).click();
  await expect(page.getByRole('heading', { name: 'Enable a processor to start building' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Browse Processor Market' })).toBeVisible();

  const requiredProcessorReleases = [
    ['mime-validation', '1.0.0'],
    ['governance.content-safety', '1.0.0'],
    ['resize', '2.0.0'],
  ];
  for (const [slug, version] of requiredProcessorReleases) {
    const enabled = await page.request.put(
      `/api/backend/projects/${session.selectedProjectId}/processor-releases/${encodeURIComponent(slug)}/versions/${version}`,
      { headers: { 'X-Sluice-CSRF': csrfToken! } },
    );
    expect(enabled.status()).toBe(200);
  }

  await page.getByRole('link', { name: 'Processor Market', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Composable media capabilities/i })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(7);
  await expect(page.getByRole('heading', { name: 'Transform', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Optimize', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Privacy', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Governance', exact: true })).toBeVisible();

  await page.getByLabel('Search processors').fill('quality');
  await expect(page).toHaveURL(/q=quality/);
  await expect(page.getByRole('article')).toHaveCount(1);
  await expect(page.getByRole('article')).toContainText('WebP Encoder');
  await page.getByLabel('Produced output').selectOption('image/webp');
  await expect(page).toHaveURL(/output=image%2Fwebp/);
  await page.reload();
  await expect(page.getByLabel('Search processors')).toHaveValue('quality');
  await expect(page.getByLabel('Produced output')).toHaveValue('image/webp');
  await expect(page.getByRole('article')).toHaveCount(1);
  await page.getByRole('button', { name: 'Reset filters' }).click();
  await expect(page.getByRole('article')).toHaveCount(7);

  const resizeCard = page.getByRole('article').filter({ hasText: 'Image Resize' });
  await expect(resizeCard).toContainText('Recommended · v2.0.0');
  await expect(resizeCard.getByText('Accepts')).toBeVisible();
  await expect(resizeCard).toContainText('image/jpeg');
  await expect(resizeCard.getByRole('heading', { name: 'Pipeline step example' })).toBeVisible();
  await expect(resizeCard.locator('pre')).toContainText('"processor": "resize"');
  await expect(resizeCard.locator('pre')).toContainText('"config": {}');
  await resizeCard.getByText('Version history (1)').click();
  await expect(resizeCard.getByText('v1.0.0', { exact: true })).toBeVisible();

  const checksumCard = page.getByRole('article').filter({ hasText: 'SHA-256 Checksum' });
  await checksumCard.getByRole('button', { name: 'Enable for project' }).click();
  await expect(checksumCard.getByRole('link', { name: 'Use in pipeline' })).toBeVisible();
  await checksumCard.getByRole('button', { name: 'Disable' }).click();
  await expect(checksumCard.getByRole('alertdialog')).toContainText('Published pipelines remain unchanged.');
  await checksumCard.getByRole('button', { name: 'Confirm disable' }).click();
  await expect(checksumCard.getByRole('button', { name: 'Enable for project' })).toBeVisible();

  const webpCard = page.getByRole('article').filter({ hasText: 'WebP Encoder' });
  await webpCard.getByRole('button', { name: 'Enable for project' }).first().click();
  await expect(webpCard.getByRole('link', { name: 'Use in pipeline' }).first()).toBeVisible();
  await webpCard.getByRole('link', { name: 'Use in pipeline' }).first().click();
  await expect(page).toHaveURL(/\/pipelines\?processor=webp&version=2\.0\.0/);
  await expect(page.getByRole('heading', { name: 'Build a media pipeline' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('WebP Encoder v2.0.0 added from the Processor Market.');
  const processorPicker = page.getByRole('button', { name: 'Select exact processor release' });
  await processorPicker.click();
  const enabledReleaseOptions = page.getByRole('listbox', { name: 'Enabled processor releases' }).getByRole('option');
  await expect(enabledReleaseOptions).toHaveCount(4);
  await expect(enabledReleaseOptions.first()).toContainText('WebP Encoder · v2.0.0');
  await expect(page.getByRole('listbox', { name: 'Enabled processor releases' })).not.toContainText('SHA-256 Checksum');
  const processorSearch = page.getByRole('combobox', { name: 'Search enabled processor releases' });
  await processorSearch.press('ArrowDown');
  await processorSearch.press('Enter');
  await expect(processorPicker).toContainText('Content Safety · v1.0.0');
  await page.getByLabel('Name').fill(pipelineName);
  const webpTemplate = page.getByRole('button', { name: /WebP delivery.*Validate MIME.*Encode WebP/ });
  const resizeTemplate = page.getByRole('button', { name: /Resize \+ WebP.*Validate MIME.*Resize image.*Encode WebP/ });
  await expect(webpTemplate).toHaveAttribute('aria-pressed', 'false');
  await resizeTemplate.click();
  let resizeTemplateDialog = page.getByRole('alertdialog', { name: 'Confirm starter template' });
  await expect(resizeTemplateDialog).toBeFocused();
  await expect(resizeTemplateDialog).toContainText('Replace unsaved pipeline steps?');
  await expect(resizeTemplateDialog).toContainText('Content Safety');
  await expect(resizeTemplateDialog).toContainText('MIME Validation → Image Resize → WebP Encoder');
  await resizeTemplateDialog.press('Escape');
  await expect(resizeTemplate).toBeFocused();
  await expect(processorPicker).toContainText('Content Safety · v1.0.0');
  const templateStatus = page.getByRole('status').filter({ hasText: /Template applied:|Kept the current pipeline steps/ });
  await expect(templateStatus).toContainText('Resize + WebP was not applied.');
  await resizeTemplate.click();
  resizeTemplateDialog = page.getByRole('alertdialog', { name: 'Confirm starter template' });
  await resizeTemplateDialog.getByRole('button', { name: 'Keep current steps' }).click();
  await expect(resizeTemplate).toBeFocused();
  await resizeTemplate.click();
  resizeTemplateDialog = page.getByRole('alertdialog', { name: 'Confirm starter template' });
  await resizeTemplateDialog.getByRole('button', { name: 'Apply Resize + WebP' }).click();
  await expect(resizeTemplate).toHaveAttribute('aria-pressed', 'true');
  await expect(templateStatus).toContainText('Template applied: Resize + WebP.');
  await expect(templateStatus).toContainText('Replaced 1 step with 3');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await webpTemplate.click();
  const webpTemplateDialog = page.getByRole('alertdialog', { name: 'Confirm starter template' });
  await expect(webpTemplateDialog).toContainText('MIME Validation → Image Resize → WebP Encoder');
  await expect(webpTemplateDialog).toContainText('MIME Validation → WebP Encoder');
  await webpTemplateDialog.getByRole('button', { name: 'Apply WebP delivery' }).click();
  await expect(webpTemplate).toHaveAttribute('aria-pressed', 'true');
  await expect(templateStatus).toContainText('Template applied: WebP delivery.');
  const definitionRegion = page.getByRole('region', { name: 'Pipeline definition steps' });
  await expect(definitionRegion).toHaveClass(/sluice-template-settle/);
  expect(await definitionRegion.evaluate((element) => getComputedStyle(element).animationName)).toBe('none');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.getByLabel('Input MIME types').fill('video/mp4');
  const compatibilityWarning = page.getByText('This processor cannot accept the preceding output. Reorder the steps or choose a compatible release.', { exact: true });
  await expect(compatibilityWarning).toBeVisible();
  await page.getByLabel('Input MIME types').fill('image/png');
  await expect(compatibilityWarning).toHaveCount(0);
  await page.getByLabel('allowedTypes').fill('image/png, image/jpeg');
  await page.getByLabel('quality number').fill('82');
  await expect(page.getByLabel('quality slider')).toHaveValue('82');
  await expect(page.getByLabel('Name')).toHaveValue(pipelineName);
  await page.getByRole('button', { name: 'JSON', exact: true }).click();
  const guidedDefinition = JSON.parse(await page.getByLabel('Canonical pipeline JSON').inputValue());
  expect(guidedDefinition.steps.map((step: { processor: string }) => step.processor)).toEqual(['mime-validation', 'webp']);
  expect(guidedDefinition.steps[0].config.allowedTypes).toEqual(['image/png', 'image/jpeg']);
  expect(guidedDefinition.steps[1].config.quality).toBe(82);
  await page.getByRole('button', { name: 'Form', exact: true }).click();
  await expect(webpTemplate).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'JSON', exact: true }).click();
  let releaseSave: (() => void) | undefined;
  const saveGate = new Promise<void>((resolveGate) => { releaseSave = resolveGate; });
  await page.route('**/api/backend/pipelines', async (route) => {
    if (route.request().method() === 'POST') await saveGate;
    await route.continue();
  });
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('button', { name: 'Saving…' })).toHaveAttribute('aria-busy', 'true');
  releaseSave?.();
  await expect(page.getByRole('status')).toContainText('Draft saved.');
  await page.unroute('**/api/backend/pipelines');
  const pipelineSearch = page.getByLabel('Search pipelines');
  const pipelineState = page.getByLabel('Pipeline state');
  await pipelineSearch.fill(pipelineName.toUpperCase());
  await pipelineState.selectOption('draft');
  const selectedPipelineCard = page.getByRole('button', { name: new RegExp(pipelineName) });
  await expect(selectedPipelineCard).toHaveAttribute('aria-current', 'true');
  await pipelineSearch.fill(guidedDefinition.slug);
  await expect(selectedPipelineCard).toBeVisible();
  await pipelineState.selectOption('published');
  await expect(page.getByText('No pipelines match this search and state.')).toBeVisible();
  expect(JSON.parse(await page.getByLabel('Canonical pipeline JSON').inputValue())).toEqual(guidedDefinition);
  await pipelineSearch.fill('');
  await pipelineState.selectOption('all');
  await expect(page.getByRole('button', { name: 'Publish immutable version' })).toBeEnabled();
  const savedJson = await page.getByLabel('Canonical pipeline JSON').inputValue();
  await page.getByLabel('Canonical pipeline JSON').fill(`${savedJson}\n`);
  const discardDialogPromise = page.waitForEvent('dialog');
  const newPipelineClick = page.getByRole('button', { name: 'New pipeline' }).click();
  const discardDialog = await discardDialogPromise;
  expect(discardDialog.message()).toContain('Discard your unsaved pipeline changes?');
  await discardDialog.dismiss();
  await newPipelineClick;
  await expect(page.getByLabel('Canonical pipeline JSON')).toHaveValue(`${savedJson}\n`);
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('button', { name: 'Publish immutable version' })).toBeEnabled();
  await page.getByRole('button', { name: 'Validate' }).click();
  await expect(page.getByRole('status')).toContainText('ready to publish');
  await page.getByRole('button', { name: 'Publish immutable version' }).click();
  await expect(page.getByRole('alertdialog', { name: 'Confirm pipeline publication' })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm publish' }).click();
  await expect(page.getByRole('status')).toContainText('Published an immutable version');
  await pipelineState.selectOption('published');
  await expect(selectedPipelineCard).toBeVisible();
  await expect(selectedPipelineCard).toContainText('Published v1');
  await pipelineState.selectOption('draft');
  await expect(page.getByText('No pipelines match this search and state.')).toBeVisible();
  await expect(page.getByLabel('Canonical pipeline JSON')).toHaveValue(`${savedJson}\n`);
  await pipelineState.selectOption('all');

  const publishedResponse = await page.request.get('/api/backend/pipelines/published');
  expect(publishedResponse.status()).toBe(200);
  const publishedPipelines = await publishedResponse.json();
  expect(publishedPipelines).toHaveLength(1);
  const publishedSlug = publishedPipelines[0].slug as string;
  expect(publishedSlug).not.toContain('<');

  const malformedContractPage = await context.newPage();
  await malformedContractPage.route('**/api/backend/pipelines/published', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ ...publishedPipelines[0], inputContract: null, uploadConstraints: null }]),
    });
  });
  await malformedContractPage.goto('/assets/upload');
  const malformedPipelinePicker = malformedContractPage.getByRole('combobox', { name: 'Published pipeline' });
  await malformedPipelinePicker.fill(publishedSlug);
  const malformedPipelineOption = malformedContractPage.getByRole('listbox', { name: 'Published pipelines' }).getByRole('option');
  await expect(malformedPipelineOption).toContainText(publishedPipelines[0].name);
  await expect(malformedPipelineOption).toContainText(`${publishedSlug} · Published v${publishedPipelines[0].versionNumber}`);
  await malformedPipelinePicker.press('Enter');
  await expect(malformedContractPage.locator('#file-upload')).toBeDisabled();
  await expect(malformedContractPage.getByRole('alert').filter({
    hasText: 'no usable resolved input contract',
  })).toBeVisible();
  await malformedContractPage.close();

  const compatibilityPage = await context.newPage();
  const videoPipeline = {
    ...publishedPipelines[0],
    id: 'mock-video-pipeline',
    slug: 'video-delivery',
    name: 'Video delivery',
    versionId: 'mock-video-version',
    versionNumber: 7,
    expectedInputMimeType: 'video/mp4',
    inputContract: { ...publishedPipelines[0].inputContract, kind: 'video', mimeTypes: ['video/mp4'] },
    uploadConstraints: { ...publishedPipelines[0].uploadConstraints, allowedContentTypes: ['video/mp4'] },
  };
  await compatibilityPage.route('**/api/backend/pipelines/published', async (route) => {
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify([videoPipeline, publishedPipelines[0]]) });
  });
  await compatibilityPage.goto('/assets/upload');
  const compatibilityPicker = compatibilityPage.getByRole('combobox', { name: 'Published pipeline' });
  await compatibilityPicker.focus();
  const orderedPipelineOptions = compatibilityPage.getByRole('listbox', { name: 'Published pipelines' }).getByRole('option');
  await expect(orderedPipelineOptions.first()).toContainText(publishedPipelines[0].name);
  await expect(orderedPipelineOptions.last()).toContainText('Video delivery');
  await expect(orderedPipelineOptions.first()).toHaveAttribute('tabindex', '-1');
  await compatibilityPicker.fill(publishedSlug);
  await compatibilityPicker.press('Enter');
  await expect(compatibilityPicker).toHaveValue(`${publishedPipelines[0].name} · ${publishedSlug} · Published v${publishedPipelines[0].versionNumber}`);
  const compatibilityImage = Buffer.from(readFileSync(resolve(demoDir, 'sample.png.base64'), 'utf8').trim(), 'base64');
  await compatibilityPage.locator('#file-upload').setInputFiles({ name: 'selected-compatible.png', mimeType: 'image/png', buffer: compatibilityImage });
  await expect(compatibilityPage.getByText('selected-compatible.png')).toBeVisible();
  await compatibilityPicker.fill('Video delivery');
  await compatibilityPicker.press('ArrowDown');
  await compatibilityPicker.press('Enter');
  await expect(compatibilityPicker).toHaveValue('Video delivery · video-delivery · Published v7');
  await expect(compatibilityPage.getByText('selected-compatible.png')).toHaveCount(0);
  await expect(compatibilityPage.getByRole('note')).toContainText('Accepted types: video/mp4');
  await compatibilityPicker.fill(publishedSlug);
  await compatibilityPicker.press('Enter');
  await compatibilityPage.locator('#file-upload').setInputFiles({ name: 'project-bound.png', mimeType: 'image/png', buffer: compatibilityImage });
  await expect(compatibilityPage.getByText('project-bound.png')).toBeVisible();
  const projectSwitcher = compatibilityPage.locator('#desktop-project');
  const originalProjectId = await projectSwitcher.inputValue();
  const otherProjectId = await projectSwitcher.locator('option').evaluateAll((options, selectedProjectId) =>
    options.map((option) => (option as HTMLOptionElement).value).find((value) => value && value !== selectedProjectId) ?? '',
  originalProjectId);
  expect(otherProjectId).not.toBe('');
  await projectSwitcher.selectOption(otherProjectId);
  await expect.poll(async () => {
    const response = await compatibilityPage.request.get('/api/session');
    return (await response.json()).selectedProjectId;
  }).toBe(otherProjectId);
  await expect(compatibilityPicker).toHaveValue('');
  await expect(compatibilityPage.getByText('project-bound.png')).toHaveCount(0);
  await projectSwitcher.selectOption(originalProjectId);
  await expect.poll(async () => {
    const response = await compatibilityPage.request.get('/api/session');
    return (await response.json()).selectedProjectId;
  }).toBe(originalProjectId);
  await compatibilityPage.close();

  await page.goto('/quick-start');
  await expect(page.getByRole('main').getByText(publishedSlug, { exact: true })).toBeVisible();
  await expect(page.getByRole('note')).toHaveCount(0);
  const pipelineStep = page.getByRole('listitem').filter({ hasText: 'Publish a pipeline' });
  await expect(pipelineStep.getByLabel('Complete')).toBeVisible();
  await expect(page.locator('pre')).toContainText('/webhook-endpoints');
  await expect(page.locator('pre')).toContainText('callback:{webhookEndpointId:$webhookEndpointId}');
  await expect(page.locator('pre')).toContainText(publishedSlug);

  const curlTab = page.getByRole('tab', { name: 'cURL' });
  await curlTab.focus();
  await page.keyboard.press('ArrowRight');
  const javascriptTab = page.getByRole('tab', { name: 'JavaScript' });
  await expect(javascriptTab).toBeFocused();
  await expect(javascriptTab).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', 'example-tab-javascript');
  await expect(page.locator('pre')).toContainText("callback: { webhookEndpointId: webhook.id }");
  await expect(page.locator('pre')).toContainText('verifyWebhook');
  await page.keyboard.press('End');
  const pythonTab = page.getByRole('tab', { name: 'Python' });
  await expect(pythonTab).toBeFocused();
  await expect(page.locator('pre')).toContainText("'callback': {'webhookEndpointId': webhook['id']}");
  await page.keyboard.press('Home');
  await expect(curlTab).toBeFocused();

  await page.goto('/assets/upload');
  const image = Buffer.from(readFileSync(resolve(demoDir, 'sample.png.base64'), 'utf8').trim(), 'base64');
  await expect(page.locator('#file-upload')).toBeDisabled();
  const publishedPipelinePicker = page.getByRole('combobox', { name: 'Published pipeline' });
  await publishedPipelinePicker.fill(publishedSlug);
  await publishedPipelinePicker.press('Enter');
  await expect(page.getByRole('note')).toContainText('Accepted types: image/png');
  await page.locator('#file-upload').setInputFiles({ name: 'browser-demo.mp4', mimeType: 'video/mp4', buffer: image });
  const incompatibleFileAlert = page.getByRole('alert').filter({ hasText: 'Test could not continue' });
  await expect(incompatibleFileAlert).toContainText('video/mp4');
  await expect(incompatibleFileAlert).toContainText('image/png');
  await page.locator('#file-upload').setInputFiles({ name: 'browser-demo.png', mimeType: 'image/png', buffer: image });
  await page.getByRole('button', { name: 'Upload and create run' }).click();
  await expect(page.getByRole('heading', { name: 'Run created' })).toBeVisible({ timeout: 90_000 });

  await page.getByRole('button', { name: 'View run' }).click();
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
  expect(run.outputs[0].storageUrl).toBeUndefined();
  await expect(page.getByText(`exact version v${run.pipeline.version}`)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Step details' })).toBeVisible();
  await expect(page.getByText('Queue wait', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Outputs and compression' })).toBeVisible();
  const downloadOutput = page.getByRole('link', { name: 'Download output' });
  await expect(downloadOutput).toBeVisible({ timeout: 120_000 });
  const [outputDownload] = await Promise.all([
    page.waitForEvent('download'),
    downloadOutput.click(),
  ]);
  expect(outputDownload.suggestedFilename()).toBe(run.outputs[0].filename);
  const downloadedPath = await outputDownload.path();
  expect(downloadedPath).not.toBeNull();
  expect(readFileSync(downloadedPath!).length).toBeGreaterThan(0);

  await page.getByRole('link', { name: `View ${run.outputs[0].filename}` }).click();
  await expect(page.getByRole('heading', { name: 'Image preview' })).toBeVisible();
  const preview = page.getByAltText(`Preview of ${run.outputs[0].filename}`);
  await expect(preview).toHaveAttribute('src', `/api/downloads/assets/${run.outputs[0].id}?inline=1`);
  await expect(page.getByRole('link', { name: `Download ${run.outputs[0].filename}` })).toHaveAttribute('href', `/api/downloads/assets/${run.outputs[0].id}`);
  await page.goBack();

  const referencedFilename = `customer-avatar-${suffix}.png`;
  const externalSubjectId = `user_${suffix}`;
  const externalReference = `avatar_${suffix}`;
  const referencedUploadResponse = await page.request.post(`${session.apiBaseUrl}/uploads`, {
    headers: { 'X-API-Key': revealed, 'Idempotency-Key': `asset-search-${suffix}` },
    data: { filename: referencedFilename, contentType: 'image/png', size: image.length, externalSubjectId, externalReference },
  });
  expect(referencedUploadResponse.status()).toBe(201);
  const referencedUpload = await referencedUploadResponse.json();
  expect((await page.request.put(referencedUpload.uploadUrl, {
    headers: { 'x-ms-blob-type': 'BlockBlob', 'Content-Type': 'image/png' }, data: image,
  })).status()).toBe(201);
  expect((await page.request.post(`${session.apiBaseUrl}/uploads/${referencedUpload.assetId}/complete`, {
    headers: { 'X-API-Key': revealed, 'Idempotency-Key': `asset-complete-${suffix}` },
  })).status()).toBe(200);

  const today = dateTimeLocal(new Date()).slice(0, 10);
  await page.goto('/assets');
  await page.getByLabel('Filename').fill(`avatar-${suffix}`);
  await page.getByLabel('Status').selectOption('COMPLETED');
  await page.getByLabel('Media family').selectOption('image');
  await page.getByLabel('External subject ID').fill(externalSubjectId);
  await page.getByLabel('External reference').fill(externalReference);
  await page.getByLabel('Created from').fill(today);
  await page.getByLabel('Created through').fill(today);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page).toHaveURL(new RegExp(`/assets\\?.*q=avatar-${suffix}.*status=COMPLETED.*type=image`));
  await expect(page.getByRole('link', { name: referencedFilename })).toBeVisible();
  await expect(page.getByLabel(`Remove Subject: ${externalSubjectId}`)).toBeVisible();
  await page.reload();
  await expect(page.getByRole('link', { name: referencedFilename })).toBeVisible();
  await page.getByLabel(`Remove Filename: avatar-${suffix}`).click();
  await expect(page).not.toHaveURL(/(?:\?|&)q=/);
  await expect(page.getByRole('link', { name: referencedFilename })).toBeVisible();
  await page.getByLabel('Filename').fill(`missing-${suffix}`);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText('No assets match these filters')).toBeVisible();
  await page.getByRole('button', { name: 'Reset filters' }).click();
  await expect(page).toHaveURL(/\/assets$/);

  const pagedAsset = {
    id: referencedUpload.assetId, filename: referencedFilename, size: image.length,
    contentType: 'image/png', uploadStatus: 'COMPLETED', createdAt: new Date().toISOString(),
    parentAssetId: null, producingJobId: null, externalSubjectId, externalReference,
  };
  await page.route('**/api/backend/assets?*', async (route) => {
    const requested = new URL(route.request().url());
    const number = Number(requested.searchParams.get('page') ?? '0');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      content: [pagedAsset], number, size: 20, numberOfElements: 1, totalElements: 21,
      totalPages: 2, first: number === 0, last: number === 1, empty: false,
    }) });
  });
  await page.goto('/assets?q=paged&status=COMPLETED');
  const nextAssetsPage = page.getByRole('button', { name: 'Next', exact: true });
  await expect(nextAssetsPage).toBeEnabled();
  await nextAssetsPage.click();
  await expect(page).toHaveURL(/\/assets\?q=paged&status=COMPLETED&page=1$/);
  await page.unroute('**/api/backend/assets?*');

  await expect.poll(async () => {
    const response = await page.request.get('/api/backend/dashboard');
    return (await response.json()).completedJobs;
  }).toBeGreaterThan(0);
  await page.goto('/app');
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Start your first integration' })).toHaveCount(0);

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
  const governancePipelinePicker = page.getByRole('combobox', { name: 'Published pipeline' });
  await governancePipelinePicker.fill(governancePipelineName);
  await governancePipelinePicker.press('Enter');
  await page.locator('#file-upload').setInputFiles({ name: 'browser-governed.png', mimeType: 'image/png', buffer: image });
  await page.getByRole('button', { name: 'Upload and create run' }).click();
  await expect(page.getByRole('heading', { name: 'Run created' })).toBeVisible({ timeout: 90_000 });

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
  await expect(page.getByRole('heading', { name: 'Governance' })).toBeVisible();
  await expect(page.getByText('Policy version', { exact: true })).toBeVisible();
  await expect(page.getByText('Provider', { exact: true })).toBeVisible();
  await expect(page.getByText('Categories', { exact: true })).toBeVisible();
  await expect(page.getByText('Reasons', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Runs', exact: true }).click();
  await page.getByLabel('Status').selectOption('COMPLETED');
  await page.getByLabel('Pipeline slug').fill(definition.slug);
  await page.getByLabel('From').fill(dateTimeLocal(new Date(Date.now() - 86_400_000)));
  await page.getByLabel('To', { exact: true }).fill(dateTimeLocal(new Date(Date.now() + 86_400_000)));
  await page.getByRole('button', { name: 'Apply' }).click();
  await expect(page).toHaveURL(/\/jobs\?.*status=COMPLETED.*pipeline=/);
  await expect(page.getByRole('link', { name: new RegExp(definition.slug) })).toBeVisible();
  await page.getByRole('link', { name: new RegExp(definition.slug) }).click();
  await page.getByRole('link', { name: 'Back to Runs' }).click();
  await expect(page).toHaveURL(/\/jobs\?.*status=COMPLETED.*pipeline=/);
  await page.getByRole('link', { name: 'Governance' }).click();
  await expect(page.getByLabel('ALLOW', { exact: true })).toBeVisible();
  await page.getByLabel('Decision', { exact: true }).selectOption('ALLOW');
  await page.getByLabel('Pipeline slug').fill('missing-governance-pipeline');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByRole('heading', { name: 'No governance decisions match these filters' })).toBeVisible();
  await page.getByRole('button', { name: 'Reset filters' }).click();
  await expect(page).toHaveURL(/\/governance$/);
  await page.getByLabel('Decision', { exact: true }).selectOption('ALLOW');
  await page.getByLabel('Pipeline slug').fill(definition.slug);
  await page.getByLabel('Created from').fill(dateTimeLocal(new Date(Date.now() - 86_400_000)).split('T')[0]);
  await page.getByLabel('Created through').fill(dateTimeLocal(new Date(Date.now() + 86_400_000)).split('T')[0]);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page).toHaveURL(/\/governance\?.*decision=ALLOW.*pipeline=/);
  const governanceQuery = new URL(page.url()).search;
  await expect(page.getByText('Showing 1-1 of 1 decisions')).toBeVisible();
  await page.locator('tbody a').first().click();
  await page.getByRole('link', { name: 'Back to Governance' }).click();
  await expect(page).toHaveURL(`/governance${governanceQuery}`);

  await page.getByRole('link', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Revoke', exact: true }).click();
  const revokeDialog = page.getByRole('dialog', { name: 'Revoke API key?' });
  await expect(revokeDialog).toBeVisible();
  await revokeDialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(revokeDialog).toBeHidden();
  const revokeButton = page.getByRole('button', { name: 'Revoke', exact: true });
  await expect(revokeButton).toBeVisible();
  await revokeButton.click();
  await page.getByRole('dialog', { name: 'Revoke API key?' }).getByRole('button', { name: 'Revoke key' }).click();
  await expect(page.getByRole('status')).toHaveText('API key revoked.');
  await expect(page.getByText('Revoked', { exact: true })).toBeVisible();
  await page.goto('/app');
  const resumedChecklist = page.getByRole('region', { name: 'Start your first integration' });
  await expect(resumedChecklist).toBeVisible();
  await expect(resumedChecklist.getByRole('listitem').filter({ hasText: 'Create an API key' }).getByLabel('Not complete')).toBeVisible();
  await expect(resumedChecklist.getByRole('listitem').filter({ hasText: 'Publish a pipeline' }).getByLabel('Complete')).toBeVisible();
  await expect(resumedChecklist.getByRole('listitem').filter({ hasText: 'Complete your first run' }).getByLabel('Complete')).toBeVisible();

  await page.getByRole('button', { name: 'Sign out' }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();
});
