const { defineConfig } = require('@playwright/test');

const executablePath = process.env.BROWSER_EXECUTABLE_PATH;

module.exports = defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: process.env.BASE_URL || 'http://127.0.0.1:8080',
    serviceWorkers: 'block',
    trace: 'on-first-retry',
    ...(executablePath ? { launchOptions: { executablePath } } : {}),
  },
  reporter: [['list']],
});