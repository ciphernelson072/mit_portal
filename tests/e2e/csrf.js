async function postWithCsrf(page, url, options = {}) {
  const tokenResponse = await page.request.get('/api/auth/csrf');
  if (!tokenResponse.ok()) {
    throw new Error(`Could not load CSRF token (${tokenResponse.status()})`);
  }

  const payload = await tokenResponse.json();
  if (typeof payload.token !== 'string' || !payload.token) {
    throw new Error('CSRF token response was invalid');
  }

  return page.request.post(url, {
    ...options,
    headers: {
      ...options.headers,
      'X-XSRF-TOKEN': payload.token,
    },
  });
}

async function routeApiWithCsrf(page, handler) {
  await page.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname.endsWith('/auth/csrf')) return route.continue();
    return handler(route);
  });
}

module.exports = { postWithCsrf, routeApiWithCsrf };
