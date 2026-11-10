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
    './admin-login.html', './css/auth.css', './icons/campus-login.png',
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
  assert.match(serviceWorkerSource, /const CACHE_NAME = 'mit-portal-cache-v26';/);
});

test('admin login loads shared assets from the site root and keeps its initial alert hidden', () => {
  const staticRoot = path.join(__dirname, '../src/main/resources/static');
  const loginPage = fs.readFileSync(path.join(staticRoot, 'admin-login.html'), 'utf8');
  const studentLoginScript = fs.readFileSync(path.join(staticRoot, 'js/script.js'), 'utf8');

  assert.match(loginPage, /href="\/css\/auth\.css"/);
  assert.match(loginPage, /src="\/js\/deployment-config\.js"/);
  assert.match(loginPage, /src="\/js\/auth\.js"/);
  assert.match(loginPage, /id="alert"[^>]*\shidden(?:\s|>)/);
  assert.match(loginPage, /alert\.hidden = false/);
  assert.match(loginPage, /alert\.hidden = true/);
  assert.match(loginPage, /Do not redirect based only on the localStorage role hint/);
  assert.doesNotMatch(loginPage, /if\s*\(\s*getRole\(\)\s*===\s*'(?:ADMIN|TEACHER)'\s*\)/);
  assert.doesNotMatch(studentLoginScript, /if\s*\(\s*getRole\(\)\s*===\s*'STUDENT'\s*\)/);
});
