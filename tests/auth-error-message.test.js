const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const authSource = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/js/auth.js'),
  'utf8',
);

const context = {
  URL,
  Request,
  FormData,
  navigator: {},
  document: {},
  localStorage: {},
  window: {
    location: { href: 'https://portal.example/index.html' },
    fetch: async () => new Response(null, { status: 204 }),
    addEventListener() {},
    clearTimeout() {},
    setTimeout() {},
  },
};
vm.runInNewContext(authSource, context, { filename: 'auth.js' });

test('HTML gateway errors are replaced with the supplied friendly fallback', async () => {
  const response = new Response('<!doctype html><title>Cloudflare Tunnel error</title>', {
    status: 530,
    headers: { 'content-type': 'text/html; charset=UTF-8' },
  });

  assert.equal(
    await context.getResponseErrorMessage(response, 'Portal unavailable. Try again shortly.'),
    'Portal unavailable. Try again shortly.',
  );
});

test('structured API error messages remain available to the user', async () => {
  const response = Response.json({ message: 'Enter your authenticator code.' }, { status: 401 });

  assert.equal(
    await context.getResponseErrorMessage(response, 'Sign-in failed.'),
    'Enter your authenticator code.',
  );
});

test('unexpected successful HTML responses are not treated as JSON login data', () => {
  const response = new Response('<!doctype html><title>Gateway error</title>', {
    headers: { 'content-type': 'text/html' },
  });

  assert.equal(context.isJsonResponse(response), false);
});
