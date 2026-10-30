const { test, expect } = require('@playwright/test');

let authTestClient = 100;

function nextForwardedIp() {
  return `198.51.100.${((authTestClient++ - 100) % 154) + 100}`;
}

async function loginTeacher(page) {
  return page.request.post('/api/auth/login', {
    data: { username: 'teacher1', password: 'teacher123' },
    headers: { 'x-forwarded-for': nextForwardedIp() },
  });
}

test('teacher dashboard has independent course selectors', async ({ page }) => {
  const loginResponse = await loginTeacher(page);
  expect(loginResponse.ok()).toBeTruthy();

  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'teacher1');
  });
  await page.goto('/teacher-dashboard.html');
  await expect(page.locator('#rosterCourseSelect')).toHaveCount(1);
  await expect(page.locator('#attendanceCourseSelect')).toHaveCount(1);
  await expect(page.locator('#courseSelect')).toHaveCount(0);
  await expect(page.locator('#attendanceStudent')).toHaveCount(0);
});

test('teacher approves a complete present and absent class roster', async ({ page }) => {
  const loginResponse = await loginTeacher(page);
  expect(loginResponse.ok()).toBeTruthy();

  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'teacher1');
  });

  let submittedAttendance;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/teacher/me') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 3, fullName: 'Ruth Nakato' }) });
    } else if (url.pathname === '/api/teacher/course-units') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 5, unitCode: 'CSC201', name: 'Data Structures', courseId: 2, courseName: 'Computer Science', courseCode: 'CSC201' },
      ]) });
    } else if (url.pathname === '/api/teacher/my-courses') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 2, courseCode: 'CSC201', name: 'Data Structures' },
      ]) });
    } else if (url.pathname === '/api/teacher/course-units/5/students') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 7, fullName: 'Alice Student', registrationNumber: 'MIT-007', cgpa: 3.5 },
        { id: 8, fullName: 'Brian Student', registrationNumber: 'MIT-008', cgpa: 3.2 },
      ]) });
    } else if (url.pathname === '/api/teacher/courses/2/students') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 7, fullName: 'Alice Student', registrationNumber: 'MIT-007', cgpa: 3.5 },
        { id: 8, fullName: 'Brian Student', registrationNumber: 'MIT-008', cgpa: 3.2 },
      ]) });
    } else if (url.pathname === '/api/teacher/courses/2/attendance') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (url.pathname === '/api/teacher/attendance/approve') {
      submittedAttendance = route.request().postDataJSON();
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(submittedAttendance.records) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/teacher-dashboard.html');
  await expect(page.locator('#courseAttendanceTable tbody tr')).toHaveCount(2);
  await page.getByRole('radio', { name: 'Absent for Alice Student' }).check();
  await page.getByRole('radio', { name: 'Present for Brian Student' }).check();
  await expect(page.getByRole('radio', { name: 'Present for Alice Student' })).not.toBeChecked();
  await expect(page.getByRole('radio', { name: 'Absent for Alice Student' })).toBeChecked();
  await page.getByRole('button', { name: 'Approve Attendance Record' }).click();

  await expect(page.locator('#attendanceStatus')).toContainText('Approved 2 attendance records');
  expect(submittedAttendance.courseId).toBe(2);
  expect(submittedAttendance.records).toEqual([
    { studentId: 7, present: false, late: false, remarks: 'Marked absent by teacher' },
    { studentId: 8, present: true, late: false, remarks: 'Marked present by teacher' },
  ]);
});

test('teacher can review published assignments', async ({ page }) => {
  const loginResponse = await loginTeacher(page);
  expect(loginResponse.ok()).toBeTruthy();
  const assignmentsResponse = await page.request.get('/api/portal/teacher/assignments');
  expect(assignmentsResponse.status()).toBe(200);
  expect(await assignmentsResponse.json()).toEqual([]);

  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'teacher1');
  });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/teacher/me') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 3, fullName: 'Ruth Nakato' }) });
    } else if (url.pathname === '/api/teacher/course-units') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (url.pathname === '/api/portal/teacher/assignments') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 31, courseId: 12, title: 'Data Structures Lab', description: 'Submit the graph traversal exercise.', dueAt: '2026-10-14T17:00:00' },
      ]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/teacher-dashboard.html');
  await expect(page.getByRole('heading', { name: 'Published assignments' })).toBeVisible();
  await expect(page.locator('#teacherAssignmentList')).toContainText('Data Structures Lab');
  await expect(page.locator('#teacherAssignmentList')).toContainText('Course #12');
  await expect(page.locator('#teacherAssignmentList')).toContainText('2026-10-14 17:00');
});

test('teacher can select a course grade and submit it for approval', async ({ page }) => {
  const loginResponse = await loginTeacher(page);
  expect(loginResponse.ok()).toBeTruthy();
  const ownCourseGrades = await page.request.get('/api/teacher/courses/1/grades');
  expect(ownCourseGrades.status()).toBe(200);
  expect(await ownCourseGrades.json()).not.toHaveLength(0);
  const otherTeachersGrades = await page.request.get('/api/teacher/courses/2/grades');
  expect(otherTeachersGrades.status()).toBe(403);

  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'teacher1');
  });

  let submittedGradeId;
  let submittedMarks;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/teacher/me') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 3, fullName: 'Ruth Nakato' }) });
    } else if (url.pathname === '/api/teacher/my-courses') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 2, courseCode: 'CSC201', name: 'Data Structures' },
      ]) });
    } else if (url.pathname === '/api/teacher/courses/2/students') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 7, fullName: 'Alice Student', registrationNumber: 'MIT-007', cgpa: 3.5 },
      ]) });
    } else if (url.pathname === '/api/teacher/courses/2/attendance') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (url.pathname === '/api/teacher/courses/2/grades') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 61, studentId: 7, studentName: 'Alice Student', gradeValue: 'PEND', totalMark: null, remarks: 'Good work', status: 'PENDING' },
      ]) });
    } else if (url.pathname === '/api/teacher/courses/2/assessment-components') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 12, componentCode: 'CW', displayName: 'Coursework', componentType: 'COURSEWORK', weightPercentage: 100, maxScore: 100, required: true },
      ]) });
    } else if (url.pathname === '/api/teacher/grades/61/assessment-marks' && route.request().method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { componentId: 12, componentCode: 'CW', displayName: 'Coursework', componentType: 'COURSEWORK', score: null, maxScore: 100, weightPercentage: 100 },
      ]) });
    } else if (url.pathname === '/api/teacher/grades/61/assessment-marks' && route.request().method() === 'PUT') {
      submittedMarks = route.request().postDataJSON();
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ gradeId: 61, status: 'COMPLETE', complete: true, totalMark: 80 }) });
    } else if (url.pathname === '/api/teacher/grades/61/submit') {
      submittedGradeId = 61;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 61, status: 'PENDING' }) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/teacher-dashboard.html');
  const gradebook = page.locator('#teacherGradeList');
  await expect(gradebook).toContainText('Alice Student');
  await page.locator('[data-edit-grade="61"]').click();
  await expect(page.locator('#gradeRecordId')).toHaveValue('61');
  await expect(page.locator('#gradeStudent')).toHaveValue('7');
  await expect(page.locator('#gradeRemarks')).toHaveValue('Good work');
  await page.locator('[data-component-score="12"]').fill('80');
  await page.getByRole('button', { name: 'Save assessment marks' }).click();
  await expect(page.locator('#gradeStatus')).toContainText('Calculated total: 80.00%');
  await gradebook.locator('[data-submit-grade="61"]').click();
  await expect(page.locator('#gradeStatus')).toContainText('Grade submitted for administrator approval');
  expect(submittedGradeId).toBe(61);
  expect(submittedMarks).toMatchObject({ marks: [{ componentId: 12, score: 80 }], remarks: 'Good work' });
});

test('teacher can review materials across assigned courses', async ({ page }) => {
  const loginResponse = await loginTeacher(page);
  expect(loginResponse.ok()).toBeTruthy();

  await page.addInitScript(() => {
    localStorage.setItem('mitRole', 'TEACHER');
    localStorage.setItem('mitUsername', 'teacher1');
  });

  const materialRequests = [];
  let courseTwoMaterials = [
    { id: 41, title: 'Graph Lab Notes', description: 'Week 4 reference', fileUrl: 'courses/2/graph-notes.pdf', uploadedAt: '2026-10-01T09:30:00', uploadedBy: 'teacher1' },
  ];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/teacher/me') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 3, fullName: 'Ruth Nakato' }) });
    } else if (url.pathname === '/api/teacher/course-units') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 5, unitCode: 'CSC201', name: 'Data Structures', courseId: 2, courseName: 'Computer Science', courseCode: 'CSC201' },
        { id: 6, unitCode: 'CSC202', name: 'Database Systems', courseId: 2, courseName: 'Computer Science', courseCode: 'CSC201' },
        { id: 7, unitCode: 'MTH301', name: 'Calculus', courseId: 3, courseName: 'Mathematics', courseCode: 'MTH301' },
      ]) });
    } else if (url.pathname === '/api/teacher/my-courses') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 2, courseCode: 'CSC201', name: 'Computer Science' },
        { id: 3, courseCode: 'MTH301', name: 'Mathematics' },
      ]) });
    } else if (url.pathname === '/api/materials/course/2') {
      materialRequests.push(url.pathname);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(courseTwoMaterials) });
    } else if (url.pathname === '/api/materials/course/3') {
      materialRequests.push(url.pathname);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 42, title: 'Calculus Workbook', description: 'Practice problems', fileUrl: 'courses/3/workbook.pdf', uploadedAt: '2026-09-29T14:00:00', uploadedBy: 'teacher1' },
      ]) });
    } else if (url.pathname === '/api/materials' && route.request().method() === 'POST') {
      courseTwoMaterials = [...courseTwoMaterials, {
        id: 43, title: 'Week 5 Slides', description: 'Lecture slides', fileUrl: 'courses/2/week-5.pdf', uploadedAt: '2026-10-02T10:00:00', uploadedBy: 'teacher1',
      }];
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(courseTwoMaterials[1]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/teacher-dashboard.html');
  const materials = page.locator('#teacherMaterialList');
  await expect(materials).toContainText('Graph Lab Notes');
  await expect(materials).toContainText('Calculus Workbook');
  await expect(materials).toContainText('CSC201');
  await expect(materials).toContainText('MTH301');
  await expect(materials.getByRole('link', { name: 'Download' })).toHaveCount(2);
  await expect(materials.getByRole('link', { name: 'Download' }).first()).toHaveAttribute('href', '/api/materials/41/download');
  expect(materialRequests.sort()).toEqual(['/api/materials/course/2', '/api/materials/course/3']);

  await page.locator('#materialTitle').fill('Week 5 Slides');
  await page.locator('#materialDescription').fill('Lecture slides');
  await page.locator('#materialFile').setInputFiles({
    name: 'week-5.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4'),
  });
  await page.getByRole('button', { name: 'Publish Material' }).click();
  await expect(page.locator('#materialStatus')).toContainText('Material published successfully');
  await expect(materials).toContainText('Week 5 Slides');
});