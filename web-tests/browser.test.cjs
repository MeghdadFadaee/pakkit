// Browser UI tests use a local API fixture; emulator.test.cjs covers the actual watch transport.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const assets = path.resolve(__dirname, '../app/src/main/assets/web');
const reports = path.resolve(__dirname, '../app/build/reports/validation');
let state = 'waiting',
  failure = false,
  hold = false,
  bytesReceived = 0,
  disconnected = false,
  approved = true,
  denied = false,
  connectStalled = false,
  statusBodyStalled = false;
const server = http.createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  const routes = {
    '/': ['index.html', 'text/html'],
    '/app.js': ['app.js', 'text/javascript'],
    '/style.css': ['style.css', 'text/css'],
    '/privacy.txt': ['../legal/privacy.txt', 'text/plain'],
    '/licenses.txt': ['../legal/licenses.txt', 'text/plain'],
  };
  if (routes[request.url]) {
    const [file, type] = routes[request.url];
    response.setHeader('Content-Type', type);
    response.end(fs.readFileSync(path.join(assets, file)));
    return;
  }
  response.setHeader('Content-Type', 'application/json');
  if (request.url === '/api/connect' || request.url === '/api/retry') {
    if (connectStalled) return;
    if (request.url === '/api/retry') denied = false;
    response.end(
      JSON.stringify({
        connection: approved ? 'approved' : denied ? 'denied' : 'pending',
        credential: 'test-browser',
      }),
    );
  } else if (request.headers.authorization !== 'Bearer test-browser') {
    response.writeHead(403).end(JSON.stringify({ connection: 'unknown' }));
  } else if (!approved) {
    response.writeHead(403).end(JSON.stringify({ connection: denied ? 'denied' : 'pending' }));
  } else if (request.url === '/api/status') {
    if (statusBodyStalled) {
      response.flushHeaders();
      return;
    }
    if (disconnected) {
      response.writeHead(503).end('{}');
      return;
    }
    response.end(
      JSON.stringify({
        state,
        label: 'Test app',
        detail: 'Please review your watch.',
        connection: 'approved',
      }),
    );
  } else if (request.url === '/api/upload') {
    bytesReceived = 0;
    for await (const chunk of request) bytesReceived += chunk.length;
    if (hold) return; // Cancellation while awaiting a response.
    if (failure)
      response.writeHead(400).end(
        JSON.stringify({
          error: 'This APK is not compatible with your watch.',
        }),
      );
    else {
      state = 'ready';
      response.end(JSON.stringify({ label: 'Test app', state }));
    }
  } else response.writeHead(404).end('{}');
});

(async () => {
  fs.mkdirSync(reports, { recursive: true });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  const browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  let checks = 0;
  try {
    for (const width of [320, 390, 768, 1440]) {
      for (const theme of ['dark', 'light']) {
        const page = await browser.newPage({
          viewport: { width, height: 1000 },
          colorScheme: theme,
          reducedMotion: 'reduce',
        });
        await page.goto(origin);
        await page.getByText('Connected to your watch', { exact: true }).waitFor();
        assert.equal(
          await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
          false,
          `${width}px/${theme} overflow`,
        );
        assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), theme);
        assert.equal(new URL(page.url()).hash, '', 'No key in the address.');
        if (width === 390 || width === 1440)
          await page.screenshot({
            path: path.join(reports, `web-${width}-${theme}.png`),
            fullPage: true,
          });
        checks++;
        await page.close();
      }
    }
    const privacy = await (await fetch(origin + '/privacy.txt')).text();
    assert.match(privacy, /without encryption/);
    assert.match(privacy, /private watch storage/);
    assert.match(await (await fetch(origin + '/licenses.txt')).text(), /Apache License/);
    checks++;
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    approved = false;
    await page.goto(origin);
    await page.getByText('Tap Allow browser on your watch.', { exact: false }).waitFor();
    assert.equal(
      await page.locator('input:not([type="file"])').count(),
      0,
      'No manual authentication inputs',
    );
    denied = true;
    await page.getByRole('heading', { name: 'Maybe next time?' }).waitFor();
    await page.getByRole('button', { name: 'Try again', exact: true }).click();
    await page.getByRole('heading', { name: 'A quick hello' }).waitFor();
    approved = true;
    await page.getByText('Connected to your watch', { exact: true }).waitFor();
    await page.reload();
    await page.getByText('Connected to your watch', { exact: true }).waitFor();
    checks++;
    assert.deepEqual(await page.context().cookies(origin), [], 'No host-wide authorization cookie');
    let peerAuthorization;
    const peer = http.createServer((request, response) => {
      peerAuthorization = request.headers.authorization;
      response.setHeader('Content-Type', 'text/html');
      response.end('<!doctype html><title>Other watch app</title>');
    });
    await new Promise((resolve) => peer.listen(0, '127.0.0.1', resolve));
    try {
      const other = await page.context().newPage();
      await other.goto(`http://127.0.0.1:${peer.address().port}/`);
      assert.equal(await other.evaluate(() => localStorage.getItem('pakkitAccess')), null);
      assert.equal(peerAuthorization, undefined, 'Other ports do not receive access credentials');
      await other.close();
      await page.bringToFront();
    } finally {
      peer.closeAllConnections();
      peer.close();
    }
    checks++;
    await page.locator('#file').setInputFiles({
      name: 'empty.apk',
      mimeType: 'application/octet-stream',
      buffer: Buffer.alloc(0),
    });
    await page.locator('#message').getByText('This file is empty.', { exact: false }).waitFor();
    checks++;
    const file = {
      name: 'test.apk',
      mimeType: 'application/vnd.android.package-archive',
      buffer: Buffer.from('browser-ui-test'),
    };
    await page.locator('#file').setInputFiles(file);
    failure = true;
    await page.getByRole('button', { name: 'Send to watch' }).click();
    await page
      .locator('#message')
      .getByText('This APK is not compatible with your watch.')
      .waitFor();
    checks++;
    failure = false;
    await page.getByRole('button', { name: 'Send to watch' }).click();
    await page.getByRole('heading', { name: 'Made it to your watch' }).waitFor();
    assert.equal(
      await page.locator('#result-title').evaluate((el) => el === document.activeElement),
      true,
      'Upload completion moves focus out of the hidden cancel control',
    );
    assert.equal(bytesReceived, file.buffer.length);
    state = 'confirm';
    await page.getByRole('heading', { name: 'One tap closer' }).waitFor();
    state = 'installing';
    await page.getByRole('heading', { name: 'Getting ready', exact: true }).waitFor();
    state = 'failed';
    await page.getByRole('heading', { name: 'Let’s try that again' }).waitFor();
    assert.equal(await page.locator('#result').getAttribute('data-state'), 'failed');
    checks++;
    state = 'installed';
    await page.getByRole('heading', { name: 'Looks good on you.' }).waitFor();
    await page.getByRole('button', { name: 'Send another app' }).focus();
    await new Promise((resolve) => setTimeout(resolve, 1700));
    assert.equal(
      await page.locator('#again').evaluate((el) => el === document.activeElement),
      true,
      'Status polling preserves the result action focus',
    );
    checks++;
    await page.getByRole('button', { name: 'Send another app' }).click();
    await page.locator('#file').setInputFiles(file);
    await new Promise((resolve) => setTimeout(resolve, 1700));
    assert.equal(
      await page.locator('#selection').isVisible(),
      true,
      'A completed app does not block choosing another',
    );
    state = 'waiting';
    checks++;
    await page.locator('#file').setInputFiles(file);
    hold = true;
    await page.getByRole('button', { name: 'Send to watch' }).click();
    await page.getByRole('button', { name: 'Cancel transfer' }).click();
    await page.locator('#message').getByText('Transfer cancelled.', { exact: false }).waitFor();
    checks++;
    disconnected = true;
    await page.getByText('Watch out of reach', { exact: true }).waitFor();
    assert.equal(await page.locator('#send').isDisabled(), true);
    ((disconnected = false), (approved = true), (denied = false));
    await page.getByText('Connected to your watch', { exact: true }).waitFor();
    checks++;
    await page.getByRole('button', { name: 'Change color theme' }).click();
    await page.setViewportSize({ width: 640, height: 1136 });
    await page.evaluate(() => {
      document.body.style.zoom = '2';
    });
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
      '200% zoom reflows',
    );
    await page.evaluate(() => {
      document.body.style.zoom = '';
    });
    await page.getByRole('button', { name: 'Remove selected file' }).click();
    await page.locator('#choose').focus();
    assert.equal(
      await page.locator('#choose').evaluate((el) => el === document.activeElement),
      true,
    );
    await page.emulateMedia({ reducedMotion: 'reduce' });
    assert.equal(
      await page.locator('.parcel').evaluate((el) => getComputedStyle(el).animationName),
      'none',
    );
    checks++;

    const chosenTheme = await page.evaluate(() => document.documentElement.dataset.theme);
    await page.reload();
    await page.getByText('Connected to your watch', { exact: true }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), chosenTheme);
    checks++;
    // A stalled connection request must not hold the polling loop forever.
    await page.evaluate(() => localStorage.removeItem('pakkitAccess'));
    connectStalled = true;
    await page.reload();
    await page.getByText('Watch out of reach', { exact: true }).waitFor({ timeout: 10000 });
    connectStalled = false;
    await page.getByText('Connected to your watch', { exact: true }).waitFor({ timeout: 10000 });
    checks++;

    statusBodyStalled = true;
    await page.getByText('Watch out of reach', { exact: true }).waitFor({ timeout: 10000 });
    statusBodyStalled = false;
    await page.getByText('Connected to your watch', { exact: true }).waitFor({ timeout: 10000 });
    checks++;

    hold = false;
    const largePath = path.join(reports, 'browser-large-fixture.apk');
    const descriptor = fs.openSync(largePath, 'w');
    fs.ftruncateSync(descriptor, 64 * 1024 * 1024);
    fs.closeSync(descriptor);
    await page.locator('#file').setInputFiles(largePath);
    await page.getByRole('button', { name: 'Send to watch' }).click();
    await page.getByRole('heading', { name: 'Made it to your watch' }).waitFor();
    assert.equal(
      bytesReceived,
      64 * 1024 * 1024,
      'Large upload streams without the former size limit',
    );
    fs.unlinkSync(largePath);
    checks++;
    assert.deepEqual(errors, []);
    const result = {
      browser: await browser.version(),
      checks,
      javascriptErrors: errors,
      viewports: [320, 390, 768, 1440],
      themes: ['dark', 'light'],
    };
    fs.writeFileSync(path.join(reports, 'web-ui.json'), JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result));
  } finally {
    await browser.close();
  }
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => {
    server.closeAllConnections();
    server.close();
  });
