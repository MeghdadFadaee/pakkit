// Real Chrome on an Android Emulator -> real HTTP server on a Wear OS Emulator.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const session = new URL(process.env.PAKKIT_SESSION_URL);
  const endpoint = new URL(process.env.PAKKIT_CDP_URL);
  assert.match(session.hostname, /^10\.0\.2\.\d+$/, 'Use the emulators’ shared Wi-Fi network.');
  assert.equal(endpoint.hostname, '127.0.0.1');
  const reports = path.resolve(__dirname, '../app/build/reports/validation');
  fs.mkdirSync(reports, { recursive: true });
  const browser = await chromium.connectOverCDP(endpoint.href);
  try {
    const context = browser.contexts()[0];
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(session.href, { timeout: 20000 });
    await page.getByText('Connected to your watch', { exact: true }).waitFor({ timeout: 20000 });
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
    );
    await page.screenshot({
      path: path.join(reports, 'android-browser.png'),
      fullPage: true,
    });
    await page.locator('#file').setInputFiles({
      name: 'pakkit-test.apk',
      mimeType: 'application/vnd.android.package-archive',
      buffer: fs.readFileSync(
        path.resolve(__dirname, '../qa/fixture/build/outputs/apk/debug/qaFixture-debug.apk'),
      ),
    });
    await page.getByRole('button', { name: 'Send to watch' }).click();
    await page.getByRole('heading', { name: 'Made it to your watch' }).waitFor({ timeout: 30000 });
    await page.screenshot({
      path: path.join(reports, 'android-browser-review.png'),
      fullPage: true,
    });
    const status = await page.evaluate(async (token) => {
      const response = await fetch('/api/status', {
        headers: { Authorization: `Bearer ${token}` },
      });
      return response.json();
    }, session.hash.slice(1));
    assert.equal(status.state, 'ready');
    assert.equal(status.label, 'Pakkit QA fixture');
    assert.deepEqual(errors, []);
    const result = {
      browser: await browser.version(),
      directEmulatorWifi: true,
      upload: 'ready',
      javascriptErrors: errors,
    };
    fs.writeFileSync(
      path.join(reports, 'android-browser.json'),
      JSON.stringify(result, null, 2) + '\n',
    );
    console.log(JSON.stringify(result));
    await page.close();
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
