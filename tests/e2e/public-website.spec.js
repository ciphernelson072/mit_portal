const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../src/main/resources/static');

async function serveWorkspaceStaticFile(page, urlPattern, filePath, contentType) {
  await page.route(urlPattern, route => route.fulfill({
    status: 200,
    contentType,
    body: fs.readFileSync(path.join(staticRoot, filePath), 'utf8'),
  }));
}

const publicPages = [
  ['/public/index.html', 'Practical learning for a changing world.'],
  ['/public/about.html', 'Technology-focused higher education.'],
  ['/public/programmes.html', 'Explore available programmes.'],
  ['/public/admissions.html', 'Start your application.'],
  ['/public/contact.html', 'Get in touch.'],
];

test('public website pages render with their shared navigation and public-only footer', async ({ page }) => {
  for (const [path, heading] of publicPages) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Main navigation' })
      .getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/public/index.html');
    const campusLogo = page.locator('.site-header .brand-logo');
    await expect(campusLogo).toHaveAttribute('src', '/icons/logo.jpg');
    await expect.poll(() => campusLogo.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    const campusPhoto = page.locator('.hero-art img');
    await expect(campusPhoto).toHaveAttribute('src', '/public/images/campus-community.jpg');
    await expect(campusPhoto).toHaveAttribute('alt', 'A group of people, some wearing graduation gowns, outside a glass-fronted building.');
    await expect.poll(() => campusPhoto.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    await expect(page.locator('meta[property="og:title"]')).toHaveAttribute('content', /Makerere Institute of Technology/);
    const footer = page.locator('footer');
    await expect(footer.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/public/index.html');
    await expect(footer.getByRole('link', { name: 'Contact' })).toHaveAttribute('href', '/public/contact.html');
    await expect(footer.getByRole('link', { name: /portal|staff|admin/i })).toHaveCount(0);
    await expect(footer.getByRole('heading', { name: 'Follow MIT' })).toBeVisible();
    await expect(footer.locator('.social-list li')).toHaveCount(7);
    await expect(footer.locator('.social-list a')).toHaveCount(7);
    await expect(footer.locator('.social-list a[aria-label^="Open Facebook"]')).toHaveAttribute('href', 'https://www.facebook.com/');
    await expect(footer.locator('.social-list a[aria-label^="Open Instagram"]')).toHaveAttribute('href', 'https://www.instagram.com/');
    await expect(footer.locator('.social-list a[aria-label^="Open LinkedIn"]')).toHaveAttribute('href', 'https://www.linkedin.com/');
    await expect(footer.locator('.social-list a[aria-label^="Open YouTube"]')).toHaveAttribute('href', 'https://www.youtube.com/');
    await expect(footer.locator('.social-list a[aria-label^="Open WhatsApp"]')).toHaveAttribute('href', 'https://wa.me/256769234213');
    await expect(footer.locator('.social-list a[aria-label^="Open X"]')).toHaveAttribute('href', 'https://x.com/');
    await expect(footer.locator('.social-list a[aria-label^="Open TikTok"]')).toHaveAttribute('href', 'https://www.tiktok.com/');
    await expect(footer.locator('.social-note')).toContainText('ciphernelson072@gmail.com');
    await expect(footer.locator('.social-note')).toContainText('+256 769 234 213');
  }
});

test('public homepage exposes PWA install help and registers the installable app', async ({ page }) => {
  await page.goto('/public/index.html');
  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', '/manifest.json');
  await expect(page.getByRole('button', { name: 'Install portal' })).toBeVisible();
  await page.getByRole('button', { name: 'Install portal' }).click();
  await expect(page.locator('#installAppStatus')).toContainText('open your browser menu');
  await page.evaluate(() => {
    const event = new Event('beforeinstallprompt');
    event.prompt = () => Promise.resolve();
    event.userChoice = Promise.resolve({ outcome: 'accepted' });
    window.dispatchEvent(event);
  });
  await page.getByRole('button', { name: 'Install portal' }).click();
  await expect(page.locator('#installAppStatus')).toHaveText('The portal has been installed.');
  await expect(page.getByRole('button', { name: 'Install portal' })).toBeHidden();
});

test('student and employee login forms remain available at their explicit paths', async ({ page }) => {
  await page.goto('/index.html');
  await expect(page.getByText('Student Portal', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Welcome Back' })).toBeVisible();

  await page.goto('/admin-login.html');
  await expect(page.locator('.login-container')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Admin Portal' })).toBeVisible();
});

test('portal gateway links to the existing student, employee, and public destinations', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Welcome to MIT' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Choose your portal' })).toBeVisible();
  await expect(page.getByRole('link', { name: /student.*student portal/i })).toHaveAttribute('href', '/index.html');
  await expect(page.getByRole('link', { name: /employee.*employee portal/i })).toHaveAttribute('href', '/admin-login.html');
  await expect(page.getByRole('link', { name: /guest.*public information/i })).toHaveAttribute('href', '/public/index.html');

  await page.keyboard.press('Tab');
  await expect(page.locator('.skip-link')).toBeFocused();
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  const firstPortalChoice = page.locator('.portal-card').first();
  await expect(firstPortalChoice).toBeFocused();
  await expect.poll(() => firstPortalChoice.evaluate(element => getComputedStyle(element).outlineStyle))
    .not.toBe('none');
});

test('portal gateway fits a narrow mobile viewport and keeps all destinations visible', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 780 });
  await page.goto('/');

  for (const name of ['Student', 'Employee', 'Guest']) {
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  }
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
});

test('public pages use a transparent hero overlay header and switch to navy on scroll', async ({ page }) => {
  await page.goto('/public/index.html');
  const header = page.locator('.site-header');
  await expect(header).not.toHaveClass(/is-scrolled/);
  await expect.poll(() => header.evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe('rgba(0, 0, 0, 0)');
  await expect.poll(() => page.locator('.section .eyebrow').first().evaluate(element => getComputedStyle(element).color))
    .toBe('rgb(15, 118, 110)');
  await expect.poll(() => page.locator('.section-soft').evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe('rgb(237, 243, 247)');
  await page.evaluate(() => window.scrollTo(0, 200));
  await expect(header).toHaveClass(/is-scrolled/);
  await expect.poll(() => header.evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe('rgba(18, 48, 74, 0.97)');
});

test('programme catalogue and current application year use the public admissions API', async ({ page }) => {
  await serveWorkspaceStaticFile(page, '**/public/programmes.html', 'public/programmes.html', 'text/html');
  await serveWorkspaceStaticFile(page, '**/public/index.html', 'public/index.html', 'text/html');
  await serveWorkspaceStaticFile(page, '**/js/auth.js', 'js/auth.js', 'application/javascript');
  await page.route('**/api/public/admissions/programs', async route => {
    await new Promise(resolve => setTimeout(resolve, 350));
    return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{ id: 4, code: 'BSC-CS', name: 'BSc Computer Science', awardTitle: 'Bachelor of Science', durationYears: 3 }]),
    });
  });
  await page.route('**/api/public/admissions/academic-year', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ id: 8, name: '2026/2027 Academic Year' }),
  }));

  await page.goto('/public/programmes.html');
  await expect(page.locator('#mitRequestProcessing')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'BSc Computer Science' })).toBeVisible();
  await expect(page.getByText('Programme code: BSC-CS')).toBeVisible();
  await expect(page.getByText('Duration: 3 years')).toBeVisible();
  await expect(page.locator('[data-program-status]')).toBeEmpty();

  await page.goto('/public/index.html');
  await expect(page.locator('[data-current-year]')).toContainText('2026/2027 Academic Year');
});

test('failed system requests show processing but do not announce success', async ({ page }) => {
  await page.route('**/public/index.html', route => route.fulfill({
    status: 200,
    contentType: 'text/html',
    body: '<!doctype html><html><head><script src="/js/auth.js"></script></head><body></body></html>',
  }));
  await serveWorkspaceStaticFile(page, '**/js/auth.js', 'js/auth.js', 'application/javascript');
  await page.route('**/api/feedback-check', async route => {
    await new Promise(resolve => setTimeout(resolve, 350));
    await route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Service unavailable"}' });
  });
  await page.goto('/public/index.html');
  await page.evaluate(() => {
    window.feedbackRequest = fetch('/api/feedback-check').catch(() => {});
  });
  await expect(page.locator('#mitRequestProcessing')).toBeVisible();
  await page.evaluate(() => window.feedbackRequest);
  await expect(page.locator('#mitRequestProcessing')).toBeHidden();
  await expect(page.locator('#mitRequestSuccess')).toBeHidden();
});

test('mobile navigation is operable and public pages do not overflow at narrow widths', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 780 });
  await page.goto('/public/index.html');
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await expect(page.getByRole('button', { name: 'Close navigation' })).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Contact' }).click();
  await expect(page).toHaveURL(/\/public\/contact\.html$/);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
});

test('contact page publishes the support telephone and supplied email/WhatsApp contacts', async ({ page }) => {
  await page.goto('/public/contact.html');
  const contactContent = page.locator('#main-content');
  await expect(contactContent.getByRole('link', { name: '+256 741 902525', exact: true })).toHaveAttribute('href', 'tel:+256741902525');
  await expect(contactContent.getByRole('link', { name: 'ciphernelson072@gmail.com' })).toHaveAttribute('href', 'mailto:ciphernelson072@gmail.com');
  await expect(contactContent.getByRole('link', { name: '+256 769 234 213' })).toHaveAttribute('href', 'https://wa.me/256769234213');
  await expect(page.getByRole('form')).toHaveCount(0);
});

test('application form provides direct social and supplied email/WhatsApp links', async ({ page }) => {
  await page.goto('/admissions.html');
  const social = page.locator('.footer-social');
  await expect(social.getByRole('heading', { name: 'Follow MIT' })).toBeVisible();
  await expect(social.locator('.social-list li')).toHaveCount(7);
  await expect(social.locator('.social-list a')).toHaveCount(7);
  await expect(social.locator('.social-list a[href="https://wa.me/256769234213"]')).toHaveCount(1);
  await expect(social.locator('.social-note a[href="mailto:ciphernelson072@gmail.com"]')).toHaveCount(1);
});

test('public admissions information reaches the existing application and status workflows', async ({ page }) => {
  await page.route('**/api/public/admissions/**', route => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname.endsWith('/programs')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 7, code: 'BSC-CS', name: 'BSc Computer Science' }]),
      });
    }
    if (pathname.endsWith('/academic-year')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 2, name: '2026/2027 Academic Year' }),
      });
    }
    if (pathname.endsWith('/applications/status')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          applicationNumber: 'MIT-2026-0007',
          program: 'BSc Computer Science',
          academicYear: '2026/2027 Academic Year',
          status: 'UNDER_REVIEW',
        }),
      });
    }
    return route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        applicationNumber: 'MIT-2026-0007',
        status: 'PENDING',
        emailSent: true,
        emailMessage: 'A confirmation email has been sent; check your inbox and spam folder.',
      }),
    });
  });

  await page.goto('/public/admissions.html');
  await page.getByRole('link', { name: 'Apply now' }).first().click();
  await expect(page).toHaveURL(/\/admissions\.html#admissionForm$/);
  await page.getByLabel('Applicant name').fill('Test Applicant');
  await page.getByLabel('Email', { exact: true }).fill('applicant@example.test');
  await page.getByLabel('Programme').selectOption('7');
  await page.getByLabel('Academic year').selectOption('2');
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: async value => { window.copiedApplicationNumber = value; } },
  }));
  await page.getByRole('button', { name: 'Submit application' }).click();
  await expect(page.locator('#admissionStatus')).toContainText('MIT-2026-0007');
  await expect(page.locator('#admissionStatus')).toContainText('A confirmation email has been sent');
  await expect(page.locator('#admissionStatus')).toContainText('receive your username and a one-time temporary password separately');
  await expect(page.locator('#applicationConfirmation')).toBeVisible();
  await expect(page.locator('#submittedApplicationNumber')).toHaveText('MIT-2026-0007');
  await page.getByRole('button', { name: 'Copy application number' }).click();
  await expect(page.getByRole('button', { name: 'Copied' })).toBeVisible();
  expect(await page.evaluate(() => window.copiedApplicationNumber)).toBe('MIT-2026-0007');
  await expect(page.getByRole('link', { name: 'Read the applicant privacy notice' }).first()).toHaveAttribute('href', '/privacy.html#admissions-applicants');

  await page.getByLabel('Application number').fill('MIT-2026-0007');
  await page.getByLabel('Email used on application').fill('applicant@example.test');
  await page.getByRole('button', { name: 'Check status' }).click();
  await expect(page.locator('#statusResult')).toContainText('Status: Under Review');
  await expect(page.locator('#statusResult')).toContainText('Your application is being reviewed.');
});

test('public application submission shows processing and success feedback', async ({ page }) => {
  await page.route('**/admissions.html', route => route.fulfill({
    status: 200,
    contentType: 'text/html',
    body: fs.readFileSync(path.join(staticRoot, 'admissions.html'), 'utf8'),
  }));
  await page.route('**/js/auth.js', route => route.fulfill({
    status: 200,
    contentType: 'application/javascript',
    body: fs.readFileSync(path.join(staticRoot, 'js/auth.js'), 'utf8'),
  }));
  await page.route('**/api/public/admissions/**', async route => {
    const path = new URL(route.request().url()).pathname;
    await new Promise(resolve => setTimeout(resolve, 350));
    if (path.endsWith('/programs')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 7, code: 'BSC', name: 'Bachelor of Science' },
      ]) });
    } else if (path.endsWith('/academic-year')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        id: 2, name: '2027 Academic Year',
      }) });
    } else if (path.endsWith('/applications') && route.request().method() === 'POST') {
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        applicationNumber: 'APP-2027-001',
        emailSent: true,
        emailMessage: 'Application saved. A confirmation email has been sent.',
      }) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }
  });

  await page.goto('/admissions.html');
  if (!await page.evaluate(() => window.__mitRequestFeedbackInstalled === true)) {
    await page.addScriptTag({ path: path.join(staticRoot, 'js/auth.js') });
  }
  await expect.poll(() => page.evaluate(() => window.__mitRequestFeedbackInstalled === true)).toBe(true);
  await expect(page.locator('#programId')).toBeEnabled();
  await page.getByLabel('Applicant name').fill('Test Applicant');
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill('applicant@example.test');
  await page.getByLabel('Programme').selectOption('7');
  await page.getByLabel('Academic year').selectOption('2');
  await page.getByRole('button', { name: 'Submit application' }).click();

  await expect(page.locator('#mitRequestProcessing')).toBeVisible();
  await expect(page.locator('#mitRequestSuccess')).toContainText('completed successfully');
  await expect(page.locator('#admissionStatus')).toContainText('APP-2027-001');
});

test('public admissions page explains the application and current information gaps', async ({ page }) => {
  await page.goto('/public/admissions.html');
  await expect(page.getByRole('heading', { name: 'Common questions' })).toBeVisible();
  await page.getByText('What are the entry requirements, application fee, and closing date?').click();
  await expect(page.getByText('These details are not published in the current public admissions data.')).toBeVisible();
  await expect(page.getByRole('link', { name: '+256 741 902525', exact: true })).toHaveAttribute('href', 'tel:+256741902525');
});
