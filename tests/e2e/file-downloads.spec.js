const { test, expect } = require('@playwright/test');
const fs = require('node:fs/promises');

async function loginAdmin(page) {
  const response = await page.request.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'x-forwarded-for': `198.51.100.${Math.floor(Math.random() * 200) + 1}` },
  });
  expect(response.ok()).toBeTruthy();
  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'ADMIN');
    localStorage.setItem('mitUsername', 'admin');
  });
  await page.goto('/admin-dashboard.html');
  await page.waitForFunction(() => typeof downloadCsv === 'function');
}

test('admin report downloads use CSV with escaped cells and formula protection', async ({ page }) => {
  await loginAdmin(page);
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(() => downloadCsv('grades-export.csv', [{
    studentName: 'A, "B"',
    gradeValue: '=1+1',
  }]));
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('grades-export.csv');
  const contents = await fs.readFile(await download.path(), 'utf8');
  expect(contents).toContain('\uFEFF');
  expect(contents).toContain('"studentName","gradeValue"');
  expect(contents).toContain('"A, ""B""","\'=1+1"');
});

test('authenticated file downloads honor a safe server-provided filename', async ({ page }) => {
  await loginAdmin(page);
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(async () => {
    const response = new Response('verified file content', {
      headers: { 'Content-Disposition': 'attachment; filename="report.pdf"' },
    });
    await downloadResponse(response, 'fallback.pdf');
  });
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report.pdf');
  expect(await fs.readFile(await download.path(), 'utf8')).toBe('verified file content');
});

test('empty CSV exports produce a valid downloadable file', async ({ page }) => {
  await loginAdmin(page);
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(() => downloadCsv('empty-export.csv', []));
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('empty-export.csv');
  const contents = await fs.readFile(await download.path(), 'utf8');
  expect(contents).toContain('"message"');
  expect(contents).toContain('"No records found"');
});
