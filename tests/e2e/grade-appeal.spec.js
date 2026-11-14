const { test, expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');

async function serveStaticApp(page, handleApi) {
  const staticRoot = path.resolve(__dirname, '../../src/main/resources/static');

  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith('/api/')) {
      const { payload = [], status = 200 } = await handleApi(route, url) || {};
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(payload),
      });
      return;
    }

    const relativePath = decodeURIComponent(url.pathname).replace(/^\/+/, '');
    const filePath = path.resolve(staticRoot, relativePath);
    const relativeToRoot = path.relative(staticRoot, filePath);
    if (relativeToRoot.startsWith('..') || path.isAbsolute(relativeToRoot)) {
      await route.abort();
      return;
    }
    let body;
    try {
      body = await fs.readFile(filePath);
    } catch (error) {
      if (error.code !== 'ENOENT' && error.code !== 'EISDIR') throw error;
      await route.fulfill({ status: 404, body: '' });
      return;
    }
    const contentType = path.extname(filePath) === '.css' ? 'text/css'
      : path.extname(filePath) === '.js' ? 'text/javascript'
        : path.extname(filePath) === '.html' ? 'text/html' : 'application/octet-stream';
    await route.fulfill({ status: 200, contentType, body });
  });
}

test('student can appeal a published grade and track the appeal status', async ({ page }) => {
  let appeals = [];
  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'STUDENT');
    localStorage.setItem('mitUsername', 'appeal-student');
  });
  await serveStaticApp(page, async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/student/grades') {
      return { payload: [{
        id: 41,
        courseCode: 'CSC201',
        courseName: 'Data Structures',
        totalMark: 78,
        gradeValue: 'B+',
        gpaScore: 4.5,
        status: 'APPROVED',
        assessmentComponents: [],
      }] };
    }
    if (url.pathname === '/api/student/profile') {
      return { payload: { id: 7, fullName: 'Appeal Student', cgpa: 4.5 } };
    }
    if (url.pathname === '/api/reports/academic/7/status') {
      return { payload: { published: true, approved: true, publicationStatus: 'PUBLISHED' } };
    }
    if (url.pathname === '/api/student/grade-appeals' && route.request().method() === 'POST') {
      const request = route.request().postDataJSON();
      const appeal = {
        id: 3,
        gradeId: request.gradeId,
        courseCode: 'CSC201',
        courseName: 'Data Structures',
        reason: request.reason,
        status: 'OPEN',
      };
      appeals = [appeal];
      return { payload: appeal, status: 201 };
    }
    if (url.pathname === '/api/student/grade-appeals') return { payload: appeals };
    if (url.pathname === '/api/student/my-status') return { payload: { missingResultCourses: [] } };
    return { payload: [] };
  });

  await page.goto('/academics.html#grades');
  await page.getByRole('button', { name: 'Request review' }).click();
  await page.getByLabel('Explain why you are requesting a review').fill('Please verify the assessment total.');
  await page.getByRole('button', { name: 'Submit appeal' }).click();

  await expect(page.locator('#studentGradeAppealList')).toContainText('OPEN');
  await expect(page.locator('#studentGradeAppealList')).toContainText('Please verify the assessment total.');
  await expect(page.locator('.grades-table tbody')).toContainText('Appeal in progress');
  await expect(page.locator('.grades-table tbody button', { hasText: 'Request review' })).toHaveCount(0);
});

test('teacher can send a consultation response without rendering unsafe content', async ({ page }) => {
  const appeal = {
    id: 3,
    courseCode: '<img src=x onerror=alert(1)>',
    studentName: 'Appeal Student',
    reason: '<script>alert(1)</script>Please verify the assessment total.',
    status: 'TEACHER_REVIEW',
  };
  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'appeal-teacher');
  });
  await serveStaticApp(page, async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/teacher/me') return { payload: { fullName: 'Appeal Teacher' } };
    if (url.pathname === '/api/teacher/dashboard') {
      return { payload: {
        assignedCourseCount: 0,
        enrolledStudentCount: 0,
        pendingGradeCount: 0,
        upcomingAssignmentCount: 0,
        pendingAttendanceCount: 0,
      } };
    }
    if (url.pathname.startsWith('/api/teacher/grade-appeals/') && route.request().method() === 'POST') {
      appeal.teacherResponse = route.request().postDataJSON().response;
      appeal.status = 'READY_FOR_DECISION';
      return { payload: appeal };
    }
    if (url.pathname === '/api/teacher/grade-appeals') {
      return { payload: appeal.status === 'TEACHER_REVIEW' ? [appeal] : [] };
    }
    return { payload: [] };
  });

  await page.goto('/teacher-dashboard.html');
  const appealList = page.locator('#teacherGradeAppealList');
  await expect(appealList).toContainText('<img src=x onerror=alert(1)>');
  await expect(appealList.locator('img, script')).toHaveCount(0);
  await page.getByLabel('Optional response to the administrator').fill('Assessment records were checked.');
  await page.getByRole('button', { name: 'Send response to administrator' }).click();
  await expect(appealList).toContainText('No grade appeals currently require teacher review.');
  expect(appeal.teacherResponse).toBe('Assessment records were checked.');
});

test('administrator consults the teacher and records a decision safely', async ({ page }) => {
  const appeal = {
    id: 3,
    studentName: '<img src=x onerror=alert(1)>',
    registrationNumber: 'MIT-001',
    courseCode: 'CSC201',
    courseName: 'Data Structures',
    reason: '<script>alert(1)</script>Please verify the assessment total.',
    status: 'OPEN',
  };
  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'ADMIN');
    localStorage.setItem('mitUsername', 'appeal-admin');
  });
  await serveStaticApp(page, async route => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith('/api/admin/grade-appeals/') && route.request().method() === 'POST') {
      if (url.pathname.endsWith('/teacher-consultation')) appeal.status = 'TEACHER_REVIEW';
      if (url.pathname.endsWith('/confirm-teacher-consultation')) {
        appeal.status = 'READY_FOR_DECISION';
        appeal.teacherConsultedAt = '2026-11-10T12:00:00';
      }
      if (url.pathname.endsWith('/decision')) {
        const request = route.request().postDataJSON();
        appeal.status = request.decision;
        appeal.decisionNote = request.note;
      }
      return { payload: appeal };
    }
    if (url.pathname === '/api/admin/grade-appeals') return { payload: [appeal] };
    if (url.pathname === '/api/admin/grades') return { payload: [] };
    return { payload: [] };
  });

  await page.goto('/admin-dashboard.html');
  await page.locator('[data-module="grades"]').click();
  const appealList = page.locator('#adminGradeAppealList');
  await expect(appealList).toContainText('OPEN');
  await expect(appealList.locator('img, script')).toHaveCount(0);
  await expect(appealList).toContainText('<img src=x onerror=alert(1)>');
  await expect(appealList).toContainText('<script>alert(1)</script>');
  await page.getByRole('button', { name: 'Request teacher consultation' }).click();
  await page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Confirm consulted; no written response' }).click();
  await expect(appealList).toContainText('READY_FOR_DECISION');
  await page.once('dialog', dialog => dialog.accept('Separate correction authorization required.'));
  await page.getByRole('button', { name: 'Approve appeal' }).click();
  await expect(page.locator('#gradeAppealAdminStatus')).toContainText('separate authorization');
  await expect(appealList).toContainText('APPROVED');
  expect(appeal.decisionNote).toBe('Separate correction authorization required.');
});
