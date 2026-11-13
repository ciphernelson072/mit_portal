const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

async function setRoleHint(page, username, role) {
  await page.addInitScript(({ sessionRole, sessionUser }) => {
    localStorage.setItem('mitRole', sessionRole);
    localStorage.setItem('mitUsername', sessionUser);
  }, { sessionRole: role, sessionUser: username });
}

async function serveProtectedWorkspace(page, filename) {
  const filePath = path.resolve(__dirname, '../../src/main/resources/static', filename);
  await page.route(`**/${filename}`, route => route.fulfill({
    status: 200,
    contentType: 'text/html',
    body: fs.readFileSync(filePath, 'utf8'),
  }));
}

test('student profile photo opens in an accessible full-screen viewer', async ({ page }) => {
  await setRoleHint(page, 'synthetic-student', 'STUDENT');
  await serveProtectedWorkspace(page, 'dashboard.html');
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/student/profile') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        id: 7, fullName: 'Alice Student', profileImagePath: 'students/7_photo.jpg',
      }) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/dashboard.html');
  const profilePhoto = page.locator('.profile-image');
  await expect(profilePhoto).toHaveAttribute('src', /students%2F7_photo\.jpg/);
  await profilePhoto.click();
  await expect(page.locator('#photoViewerDialog')).toBeVisible();
  await expect(page.locator('#photoViewerImage')).toHaveAttribute('src', /students%2F7_photo\.jpg/);
  await page.keyboard.press('Escape');
  await expect(page.locator('#photoViewerDialog')).not.toBeVisible();
});

test('teacher can view their own and assigned student photos, export scoped CSVs, and use workspace menus', async ({ page }) => {
  await setRoleHint(page, 'synthetic-teacher', 'TEACHER');
  await serveProtectedWorkspace(page, 'teacher-dashboard.html');
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const responses = {
      '/api/teacher/me': { id: 3, fullName: 'Ruth Nakato', profileImagePath: 'teachers/3_photo.jpg' },
      '/api/teacher/dashboard': { assignedCourseCount: 1, enrolledStudentCount: 1, pendingGradeCount: 1 },
      '/api/teacher/my-courses': [{ id: 2, courseCode: 'CSC201', name: 'Data Structures' }],
      '/api/teacher/courses/2/students': [{
        id: 7, fullName: 'Alice Student', registrationNumber: 'MIT-007', cgpa: 3.5,
        profileImagePath: 'students/7_photo.jpg',
      }],
      '/api/teacher/courses/2/grades': [{
        id: 11, studentId: 7, studentName: 'Alice Student', gradeValue: 'A', status: 'PENDING', remarks: '',
      }],
    };
    const body = responses[url.pathname] ?? [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });

  await page.goto('/teacher-dashboard.html');
  await expect(page.locator('#teacherProfileImage')).toHaveAttribute('src', /teachers%2F3_photo\.jpg/);
  await page.locator('#teacherProfileImage').click();
  await expect(page.locator('#photoViewerDialog')).toBeVisible();
  await page.locator('[data-photo-viewer-close]').click();

  const rosterPhoto = page.locator('#rosterTable tbody img[data-photo-viewer]');
  await expect(rosterPhoto).toHaveAttribute('src', /students%2F7_photo\.jpg/);
  await rosterPhoto.click();
  await expect(page.locator('#photoViewerDialog')).toBeVisible();
  await page.keyboard.press('Escape');

  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
  }
  await page.setViewportSize({ width: 1280, height: 800 });

  await page.getByRole('button', { name: 'Theme:' }).click();
  await expect(page.locator('#themeMenu')).toBeVisible();
  await page.getByRole('button', { name: '🌙 Dark' }).click();
  await expect(page.locator('body')).toHaveClass(/dark-mode/);
  await expect(page.locator('.sidebar-nav a[href="#teacher-roster"]')).toBeVisible();

  const rosterDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export Roster CSV' }).click();
  expect((await rosterDownload).suggestedFilename()).toBe('teacher-roster-CSC201.csv');
  const gradesDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export Grades CSV' }).click();
  expect((await gradesDownload).suggestedFilename()).toBe('teacher-grades-CSC201.csv');
});

test('admin can download JSON and dataset-separated CSV exports', async ({ page }) => {
  await setRoleHint(page, 'synthetic-admin', 'ADMIN');
  await serveProtectedWorkspace(page, 'admin-dashboard.html');
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let body = [];
    if (url.pathname === '/api/admin/statistics') body = { totalStudents: 1, totalTeachers: 1 };
    else if (url.pathname === '/api/admin/users') body = [{ id: 1, username: 'admin', role: 'ADMIN' }];
    else if (['/api/admin/students', '/api/admin/teachers', '/api/admin/courses'].includes(url.pathname)) {
      const resource = url.pathname.split('/').pop();
      body = { content: [{ id: 7, name: `sample-${resource}` }], totalPages: 1 };
    } else if (url.pathname === '/api/admin/grades') body = [{ id: 11, gradeValue: 'A' }];
    else if (url.pathname === '/api/admin/fees') body = [{ id: 21, amount: 100 }];
    else if (url.pathname === '/api/portal/admin/audit') body = [{ id: 31, action: 'TEST' }];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });

  await page.goto('/admin-dashboard.html');
  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
  }
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.locator('.nav-item').filter({ hasText: 'Settings' }).click();
  const jsonDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download Admin Data Export' }).click();
  expect((await jsonDownload).suggestedFilename()).toBe('portal-admin-data-export.json');
  await expect(page.locator('#adminCsvExports')).toBeVisible();
  const csvDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Students CSV' }).click();
  expect((await csvDownload).suggestedFilename()).toBe('portal-students.csv');
});
