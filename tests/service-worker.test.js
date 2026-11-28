const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const serviceWorkerSource = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/service-worker.js'),
  'utf8',
);

function createFetchHandler({ cachedResponse, fetchResponse, fetchError } = {}) {
  const listeners = {};
  const cacheWrites = [];
  const self = {
    location: { origin: 'https://portal.example' },
    addEventListener: (name, handler) => { listeners[name] = handler; },
  };
  const caches = {
    match: async () => cachedResponse || null,
    open: async () => ({
      put: async (request, response) => cacheWrites.push({
        request,
        body: await response.text(),
      }),
    }),
  };
  const fetch = fetchError
    ? async () => { throw fetchError; }
    : async () => fetchResponse;

  vm.runInNewContext(serviceWorkerSource, { self, caches, fetch, URL });
  return { handler: listeners.fetch, cacheWrites };
}

test('service worker prefers fresh same-origin content and refreshes its offline cache', async () => {
  const { handler, cacheWrites } = createFetchHandler({
    cachedResponse: new Response('stale'),
    fetchResponse: new Response('fresh'),
  });
  let responsePromise;
  handler({
    request: {
      method: 'GET',
      mode: 'navigate',
      url: 'https://portal.example/dashboard.html?from=notifications',
    },
    respondWith: promise => { responsePromise = promise; },
  });

  const response = await responsePromise;
  assert.equal(await response.text(), 'fresh');
  assert.equal(cacheWrites.length, 1);
  assert.equal(cacheWrites[0].request, 'https://portal.example/dashboard.html');
  assert.equal(cacheWrites[0].body, 'fresh');
});

test('service worker does not cache an authenticated dashboard on its first visit', async () => {
  const { handler, cacheWrites } = createFetchHandler({
    fetchResponse: new Response('dashboard shell'),
  });
  let responsePromise;
  handler({
    request: {
      method: 'GET',
      mode: 'navigate',
      url: 'https://portal.example/teacher-dashboard.html',
    },
    respondWith: promise => { responsePromise = promise; },
  });

  const response = await responsePromise;
  assert.equal(await response.text(), 'dashboard shell');
  assert.equal(cacheWrites.length, 0);
});

test('service worker uses the cached page when the network is unavailable', async () => {
  const { handler } = createFetchHandler({
    cachedResponse: new Response('offline page'),
    fetchError: new Error('network unavailable'),
  });
  let responsePromise;
  handler({
    request: {
      method: 'GET',
      mode: 'navigate',
      url: 'https://portal.example/dashboard.html',
    },
    respondWith: promise => { responsePromise = promise; },
  });

  assert.equal(await (await responsePromise).text(), 'offline page');
});

test('service worker bypasses POST, cross-origin, API, and actuator requests', () => {
  const { handler } = createFetchHandler();
  let intercepted = false;
  for (const request of [
    { method: 'POST', mode: 'cors', url: 'https://portal.example/api/auth/login' },
    { method: 'GET', mode: 'cors', url: 'https://third-party.example/image.png' },
    { method: 'GET', mode: 'cors', url: 'https://portal.example/api/profile' },
    { method: 'GET', mode: 'cors', url: 'https://portal.example/actuator/health' },
  ]) {
    handler({ request, respondWith: () => { intercepted = true; } });
  }
  assert.equal(intercepted, false);
});

test('PWA manifest declares installed app icons at their real supported sizes', () => {
  const staticRoot = path.join(__dirname, '../src/main/resources/static');
  const manifest = JSON.parse(fs.readFileSync(path.join(staticRoot, 'manifest.json'), 'utf8'));
  assert.equal(manifest.start_url, '/', 'installed app should open the portal gateway');

  for (const size of [192, 512]) {
    const icon = manifest.icons.find(candidate => candidate.sizes === `${size}x${size}`);
    assert.ok(icon, `manifest should declare a ${size}x${size} icon`);
    assert.equal(icon.type, 'image/png');
    const iconPath = path.join(staticRoot, icon.src);
    assert.ok(fs.existsSync(iconPath), `${icon.src} should exist`);
    const image = fs.readFileSync(iconPath);
    assert.equal(image.readUInt32BE(16), size);
    assert.equal(image.readUInt32BE(20), size);
  }

  const worker = fs.readFileSync(path.join(staticRoot, 'service-worker.js'), 'utf8');
  const cachedAssets = [...worker.matchAll(/'\.\/([^']+)'/g)].map(match => match[1]);
  for (const asset of cachedAssets) {
    assert.ok(fs.existsSync(path.join(staticRoot, asset)), `${asset} should exist`);
  }

  const appleIcon = fs.readFileSync(path.join(staticRoot, 'icons/pwa-180.png'));
  assert.equal(appleIcon.readUInt32BE(16), 180);
  assert.equal(appleIcon.readUInt32BE(20), 180);
  const loginPage = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  assert.match(loginPage, /rel="apple-touch-icon" href="\/icons\/pwa-180\.png"/);
});

test('service worker precaches public pages but not authenticated dashboard pages', () => {
  const assetsMatch = serviceWorkerSource.match(/const CORE_ASSETS = \[([\s\S]*?)\];/);
  assert.ok(assetsMatch, 'service worker should declare its core offline assets');
  const coreAssets = [...assetsMatch[1].matchAll(/'([^']+)'/g)].map(match => match[1]);

  for (const asset of [
    './', './home.html', './index.html', './admissions.html', './course-catalog.html',
    './admin-login.html', './css/auth.css', './icons/campus-login.png', './icons/campus-login.svg',
    './css/home.css',
    './css/course-catalog.css', './js/course-catalog.js', './js/theme.js',
    './portal-login.html', './js/portal-login.js', './public/css/portal-login.css',
    './public/index.html', './public/js/site.js', './public/images/campus-gathering.jpg',
    './public/images/academic-community.jpg', './public/images/campus-activity.jpg',
  ]) {
    assert.ok(coreAssets.includes(asset), `${asset} should be available for offline startup`);
  }
  for (const asset of ['./dashboard.html', './admin-dashboard.html', './teacher-dashboard.html']) {
    assert.ok(!coreAssets.includes(asset), `${asset} requires authentication and must not be precached for anonymous users`);
  }
  assert.match(serviceWorkerSource, /const CACHE_NAME = 'mit-portal-cache-v41';/);
  assert.ok(coreAssets.includes('./js/pwa-update.js'), 'update support should be available offline');
});

test('service worker waits for the user to request an update', async () => {
  const listeners = {};
  let skipWaitingCalled = false;
  const cache = { addAll: async () => {} };
  const self = {
    location: { origin: 'https://portal.example' },
    addEventListener: (name, handler) => { listeners[name] = handler; },
    skipWaiting: async () => { skipWaitingCalled = true; },
  };
  const caches = { open: async () => cache };
  vm.runInNewContext(serviceWorkerSource, { self, caches, fetch: async () => {}, URL });

  let installPromise;
  listeners.install({ waitUntil: promise => { installPromise = promise; } });
  await installPromise;
  assert.equal(skipWaitingCalled, false);

  let updatePromise;
  listeners.message({
    data: { type: 'SKIP_WAITING' },
    waitUntil: promise => { updatePromise = promise; },
  });
  await updatePromise;
  assert.equal(skipWaitingCalled, true);
});

test('admin login loads shared assets from the site root and keeps its initial alert hidden', () => {
  const staticRoot = path.join(__dirname, '../src/main/resources/static');
  const loginPage = fs.readFileSync(path.join(staticRoot, 'admin-login.html'), 'utf8');
  const adminLoginScript = fs.readFileSync(path.join(staticRoot, 'js/admin-login.js'), 'utf8');
  const studentLoginScript = fs.readFileSync(path.join(staticRoot, 'js/script.js'), 'utf8');

  assert.match(loginPage, /href="\/css\/auth\.css"/);
  assert.match(loginPage, /src="\/js\/deployment-config\.js"/);
  assert.match(loginPage, /src="\/js\/auth\.js"/);
  assert.match(loginPage, /id="alert"[^>]*\shidden(?:\s|>)/);
  assert.match(adminLoginScript, /alert\.hidden = false/);
  assert.match(adminLoginScript, /alert\.hidden = true/);
  assert.match(adminLoginScript, /Do not redirect based only on the localStorage role hint/);
  assert.doesNotMatch(loginPage, /if\s*\(\s*getRole\(\)\s*===\s*'(?:ADMIN|TEACHER)'\s*\)/);
  assert.doesNotMatch(studentLoginScript, /if\s*\(\s*getRole\(\)\s*===\s*'STUDENT'\s*\)/);
});

test('all login forms use the supplied campus background image', () => {
  const staticRoot = path.join(__dirname, '../src/main/resources/static');
  const authCss = fs.readFileSync(path.join(staticRoot, 'css/auth.css'), 'utf8');
  const portalLoginCss = fs.readFileSync(path.join(staticRoot, 'public/css/portal-login.css'), 'utf8');
  const portalLoginPage = fs.readFileSync(path.join(staticRoot, 'portal-login.html'), 'utf8');
  const studentLoginPage = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  const adminLoginPage = fs.readFileSync(path.join(staticRoot, 'admin-login.html'), 'utf8');

  assert.match(authCss, /url\(['"]?\.\.\/icons\/campus-login\.png['"]?\)/);
  assert.match(studentLoginPage, /class="portal-auth-page student-auth-page"/);
  assert.match(adminLoginPage, /class="staff-auth-page"/);
  assert.match(portalLoginCss, /url\(['"]?\/icons\/campus-login\.png['"]?\)/);
  assert.match(portalLoginPage, /src="\/icons\/campus-login\.png"/);
});

test('MFA enrollment offers a private QR code and official authenticator downloads', () => {
  const staticRoot = path.join(__dirname, '../src/main/resources/static');
  const securityPage = fs.readFileSync(path.join(staticRoot, 'account-security.html'), 'utf8');
  const securityScript = fs.readFileSync(path.join(staticRoot, 'js/profile-security.js'), 'utf8');
  const securityCss = fs.readFileSync(path.join(staticRoot, 'css/account-security.css'), 'utf8');

  assert.match(securityPage, /id="mfaQrCode"[^>]*hidden/);
  assert.match(securityPage, /id="mfaSetupButton"[^>]*hidden/);
  assert.match(securityPage, /id="mfaVerifyButton"[^>]*hidden/);
  assert.match(securityPage, /id="mfaDisableButton"[^>]*hidden/);
  assert.match(securityCss, /\.account-security-main\s+\[hidden\]\s*\{\s*display:\s*none\s*!important;\s*\}/);
  assert.match(securityScript, /if \(!\/\^\[A-Z2-7\]\{32\}\$\/\.test\(secret\.textContent\)\)/);
  assert.match(securityScript, /if \(!enabled\)\s*\{\s*showMessage\('Two-factor authentication is not enabled\./);
  assert.match(securityPage, /play\.google\.com\/store\/apps\/details\?id=com\.google\.android\.apps\.authenticator2/);
  assert.match(securityPage, /apps\.apple\.com\/app\/microsoft-authenticator\/id983156458/);
  assert.match(securityScript, /qrCode\.src = setup\.qrCodeDataUri/);
  assert.match(securityScript, /data:image\\\/png;base64/);
});
