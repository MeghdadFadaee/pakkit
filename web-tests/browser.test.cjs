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
  bytesReceived = 0;
const server = http.createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  const routes = {
    '/': ['index.html', 'text/html'],
    '/app.js': ['app.js', 'text/javascript'],
    '/style.css': ['style.css', 'text/css'],
  };
  if (routes[request.url]) {
    const [file, type] = routes[request.url];
    response.setHeader('Content-Type', type);
    response.end(fs.readFileSync(path.join(assets, file)));
    return;
  }
  response.setHeader('Content-Type', 'application/json');
  if (request.headers.authorization !== 'Bearer test-key') {
    response.writeHead(403).end('{}');
  } else if (request.url === '/api/status') {
    response.end(
      JSON.stringify({
        state,
        label: 'Test app',
        detail: 'Please review your watch.',
        expiresIn: 500,
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
        await page.goto(`${origin}/#test-key`);
        await page.getByText('Connected to your watch', { exact: true }).waitFor();
        assert.equal(
          await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
          false,
          `${width}px/${theme} overflow`,
        );
        assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), theme);
        assert.equal(new URL(page.url()).hash, '', 'Secret must be removed from visible history.');
        if (width === 390 || width === 1440)
          await page.screenshot({
            path: path.join(reports, `web-${width}-${theme}.png`),
            fullPage: true,
          });
        checks++;
        await page.close();
      }
    }
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(origin);
    await page.locator('#access').fill('test-key');
    await page.getByRole('button', { name: 'Connect to watch' }).click();
    await page.getByText('Connected to your watch', { exact: true }).waitFor();
    checks++;
    await page.locator('#file').setInputFiles({
      name: 'wrong.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('test'),
    });
    await page
      .getByRole('status')
      .getByText('Choose a complete .apk file', { exact: false })
      .waitFor();
    checks++;
    const file = {
      name: 'test.apk',
      mimeType: 'application/vnd.android.package-archive',
      buffer: Buffer.from('browser-ui-test'),
    };
    await page.locator('#file').setInputFiles(file);
    failure = true;
    await page.getByRole('button', { name: 'Send to my watch' }).click();
    await page
      .getByRole('status')
      .getByText('This APK is not compatible with your watch.')
      .waitFor();
    checks++;
    failure = false;
    await page.getByRole('button', { name: 'Send to my watch' }).click();
    await page.getByRole('heading', { name: 'Over to your wrist.' }).waitFor();
    assert.equal(bytesReceived, file.buffer.length);
    state = 'confirm';
    await page.getByRole('heading', { name: 'Your watch. Your call.' }).waitFor();
    state = 'installed';
    await page.getByRole('heading', { name: 'Looks good on you.' }).waitFor();
    state = 'waiting';
    await page.getByRole('button', { name: 'Choose another APK' }).click();
    checks++;
    await page.locator('#file').setInputFiles(file);
    hold = true;
    await page.getByRole('button', { name: 'Send to my watch' }).click();
    await page.getByRole('button', { name: 'Cancel transfer' }).click();
    await page.getByRole('status').getByText('Transfer cancelled.', { exact: false }).waitFor();
    checks++;
    await page.getByRole('button', { name: 'Change color theme' }).click();
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
