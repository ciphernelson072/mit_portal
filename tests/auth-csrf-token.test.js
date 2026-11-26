const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const authSource = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/js/auth.js'),
  'utf8',
);

test('each state-changing API request obtains a fresh CSRF token', async () => {
  const csrfTokens = [];
  const requestTokens = [];
  const window = {
    location: { href: 'https://portal.example/account-security.html' },
    fetch: async (input, init = {}) => {
      const url = new URL(input, window.location.href);
      if (url.pathname === '/api/auth/csrf') {
        const token = `csrf-${csrfTokens.length + 1}`;
        csrfTokens.push(token);
        return Response.json({ token });
      }
      requestTokens.push(new Headers(init.headers).get('X-XSRF-TOKEN'));
      if (requestTokens.length === 1) {
        return Response.json({ message: 'Access denied' }, { status: 403 });
      }
      return Response.json({});
    },
    addEventListener() {},
    clearTimeout() {},
    setTimeout() {},
  };
  const document = {
    addEventListener() {},
    createElement: () => ({ dataset: {}, setAttribute() {} }),
    body: { append() {} },
    head: { appendChild() {} },
    querySelector: () => null,
  };
  const context = {
    URL,
    Request,
    FormData,
    Headers,
    Response,
    navigator: {},
    document,
    localStorage: {},
    window,
  };
  Object.defineProperty(context, 'fetch', { get: () => window.fetch });
  vm.runInNewContext(authSource, context, { filename: 'auth.js' });

  await assert.rejects(
    context.apiFetch('/auth/mfa/setup', { method: 'POST', body: '{}' }),
    /Access denied/,
  );
  await context.apiFetch('/auth/mfa/verify', { method: 'POST', body: '{}' });

  assert.deepEqual(csrfTokens, ['csrf-1', 'csrf-2']);
  assert.deepEqual(requestTokens, ['csrf-1', 'csrf-2']);
});
