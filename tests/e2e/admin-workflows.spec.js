const { test, expect } = require('@playwright/test');

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
}

test('admin sidebar buttons open their matching modules and external workspaces', async ({ page }) => {
  await loginAdmin(page);
  await page.route('**/api/**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: '[]',
  }));

  const moduleLinks = page.locator('.sidebar-nav .nav-item[data-module]');
  await expect(moduleLinks).toHaveCount(13);
  for (const link of await moduleLinks.all()) {
    const moduleName = await link.getAttribute('data-module');
    const module = page.locator(`#${moduleName}Module`);
    await expect(module).toHaveCount(1);
    await link.click();
    await expect(module).toBeVisible();
    await expect(link).toHaveClass(/active/);
  }

  const workspaceLinks = [
    { name: 'Messages', href: 'messages.html' },
    { name: 'File Management', href: 'file-management.html' },
    { name: 'Operations Workspace', href: 'operations.html' },
  ];
  for (const workspace of workspaceLinks) {
    await expect(page.locator('.sidebar-nav .nav-item').filter({ hasText: workspace.name }))
      .toHaveAttribute('href', workspace.href);
  }
});

test('admin can create a user account from user management', async ({ page }) => {
  await loginAdmin(page);
  const users = [{ id: 1, username: 'admin', role: 'ADMIN', isActive: true }];
  let submittedUser;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/users' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(users) });
    } else if (url.pathname === '/api/admin/users' && request.method() === 'POST') {
      submittedUser = request.postDataJSON();
      users.push({ id: 2, ...submittedUser, isActive: true });
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(users[1]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Users' }).click();
  await page.getByRole('button', { name: 'Add User' }).click();
  await page.locator('#userForm [name="username"]').fill('new-student');
  await page.locator('#userForm [name="password"]').fill('temporary-pass-123');
  await page.locator('#userForm [name="role"]').selectOption('STUDENT');
  await page.locator('#userForm [name="email"]').fill('new-student@example.test');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.locator('#usersModule tbody')).toContainText('new-student');
  expect(submittedUser).toMatchObject({
    username: 'new-student',
    role: 'STUDENT',
    email: 'new-student@example.test',
  });
});

test('cancelling announcement audience prompt does not publish to everyone', async ({ page }) => {
  await loginAdmin(page);
  await page.route('**/api/**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: '[]',
  }));
  await page.evaluate(() => {
    let promptCount = 0;
    window.prompt = () => {
      promptCount += 1;
      return promptCount === 1 ? 'Restricted update' : promptCount === 2 ? 'Staff notice' : null;
    };
    window.getAnnouncementPromptCount = () => promptCount;
  });
  let announcementSubmitted = false;
  await page.route('**/api/announcements', async route => {
    if (route.request().method() === 'POST') {
      announcementSubmitted = true;
      await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.getByRole('button', { name: 'Publish Announcement' }).click();

  expect(await page.evaluate(() => window.getAnnouncementPromptCount())).toBe(3);
  expect(announcementSubmitted).toBe(false);
});

test('admin can admit a student and refresh the student roster', async ({ page }) => {
  await loginAdmin(page);
  const students = [];
  let submittedStudent;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/students' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: students, number: 0, totalPages: 1, totalElements: students.length }),
      });
    } else if (url.pathname === '/api/admin/students/admit' && request.method() === 'POST') {
      submittedStudent = request.postDataJSON();
      students.push({
        id: 31, fullName: submittedStudent.fullName, className: submittedStudent.className,
        email: submittedStudent.email, active: true,
      });
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(students[0]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Students' }).click();
  await page.getByRole('button', { name: /Add New Student/ }).click();
  await expect(page.locator('#admitStudentForm')).toContainText(
    'Used for sign-in and password recovery; it also initializes the profile contact email.'
  );
  await page.locator('#admitStudentForm [name="fullName"]').fill('Isolated Test Applicant');
  await page.locator('#admitStudentForm [name="username"]').fill('isolated-student');
  await page.locator('#admitStudentForm [name="password"]').fill('temporary-pass-123');
  await page.locator('#admitStudentForm [name="email"]').fill('isolated-student@example.test');
  await page.locator('#admitStudentForm [name="className"]').fill('BSc Computing Year 1');
  await page.getByRole('button', { name: 'Admit Student' }).click();

  await expect(page.locator('#studentsModule tbody')).toContainText('Isolated Test Applicant');
  expect(submittedStudent).toMatchObject({
    fullName: 'Isolated Test Applicant',
    username: 'isolated-student',
    email: 'isolated-student@example.test',
    className: 'BSc Computing Year 1',
  });
});

test('admission enrollment identifies the matched profile contact email', async ({ page }) => {
  await loginAdmin(page);
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/admissions/applications' && route.request().method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{
          id: 77, applicationNumber: 'APP-77', applicantName: 'Contact Match',
          email: 'contact@example.test', programName: 'Computing', academicYearId: 2,
          academicYearName: '2027 Academic Year', status: 'ACCEPTED', enrolled: false,
        }]),
      });
    } else if (url.pathname === '/api/admissions/applications/77/enrollment-candidates') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{
          id: 19, fullName: 'Contact Match', username: 'contact-match',
          email: 'contact@example.test', registrationNumber: 'MIT/2027/00019', enrolled: false,
        }]),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Admissions' }).click();
  await page.getByRole('button', { name: 'Enroll student' }).click();

  await expect(page.locator('#modalBox')).toContainText(
    'profile contact email matches the applicant email'
  );
  await expect(page.locator('#admissionStudentId')).toContainText(
    'Contact email: contact@example.test'
  );
});

test('admin can hire a teacher and see the staff roster update', async ({ page }) => {
  await loginAdmin(page);
  const teachers = [];
  let submittedTeacher;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/teachers' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: teachers, number: 0, totalPages: 1, totalElements: teachers.length }),
      });
    } else if (url.pathname === '/api/admin/institution/departments/active' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{ id: 7, name: 'Computing', facultyName: 'Technology', campusName: 'Main Campus' }]),
      });
    } else if (url.pathname === '/api/admin/users' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{
          id: 9, username: 'hr-account', role: 'ADMIN', email: 'account@example.test', isActive: true,
        }]),
      });
    } else if (url.pathname === '/api/admin/teachers/hire' && request.method() === 'POST') {
      submittedTeacher = request.postDataJSON();
      teachers.push({
        id: 41, fullName: submittedTeacher.fullName, department: submittedTeacher.department,
        qualification: submittedTeacher.qualification, email: submittedTeacher.email, active: true,
      });
      await route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({
          ...teachers[0],
          emailSent: true,
          emailDeliveryMessage: 'One-time sign-in details sent to the teacher email.',
        }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Staff' }).click();
  await expect(page.locator('#hrStaffUser')).toContainText('Account email: account@example.test');
  await page.getByRole('button', { name: /Add Staff Member/ }).click();
  await expect(page.locator('#hireStaffForm')).toContainText(
    'Used for sign-in and password recovery; it also initializes the profile contact email.'
  );
  await page.locator('#hireStaffForm [name="fullName"]').fill('Test Lecturer');
  await page.locator('#hireStaffForm [name="username"]').fill('test-lecturer');
  await page.locator('#hireStaffForm [name="password"]').fill('temporary-pass-123');
  await page.locator('#hireStaffForm [name="departmentId"]').selectOption('7');
  await page.locator('#hireStaffForm [name="department"]').fill('Computing');
  await page.locator('#hireStaffForm [name="yearsOfExperience"]').fill('5');
  await page.locator('#hireStaffForm [name="qualification"]').fill('MSc Computer Science');
  await page.locator('#hireStaffForm [name="email"]').fill('lecturer@example.test');
  await page.getByRole('button', { name: 'Hire Staff' }).click();

  await expect(page.locator('#staffModule tbody')).toContainText('Test Lecturer');
  await expect(page.locator('#staffStatus')).toContainText('One-time sign-in details sent');
  expect(submittedTeacher).toMatchObject({
    fullName: 'Test Lecturer',
    username: 'test-lecturer',
    department: 'Computing',
    qualification: 'MSc Computer Science',
    email: 'lecturer@example.test',
    yearsOfExperience: 5,
  });
});

test('admin is told when a teacher account is created but sign-in email failed', async ({ page }) => {
  await loginAdmin(page);
  const teachers = [];

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/teachers' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: teachers, number: 0, totalPages: 1, totalElements: teachers.length }),
      });
    } else if (url.pathname === '/api/admin/institution/departments/active' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{ id: 7, name: 'Computing', facultyName: 'Technology', campusName: 'Main Campus' }]),
      });
    } else if (url.pathname === '/api/admin/teachers/hire' && request.method() === 'POST') {
      const requestBody = request.postDataJSON();
      teachers.push({ id: 42, fullName: requestBody.fullName, email: requestBody.email, active: true });
      await route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({
          ...teachers[0],
          emailSent: false,
          emailDeliveryMessage: 'Teacher account created, but email delivery failed. Arrange sign-in details using the approved secure handoff; do not submit the hire request again.',
        }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Staff' }).click();
  await page.getByRole('button', { name: /Add Staff Member/ }).click();
  await page.locator('#hireStaffForm [name="fullName"]').fill('Email Failure Lecturer');
  await page.locator('#hireStaffForm [name="username"]').fill('email-failure-lecturer');
  await page.locator('#hireStaffForm [name="password"]').fill('temporary-pass-123');
  await page.locator('#hireStaffForm [name="departmentId"]').selectOption('7');
  await page.locator('#hireStaffForm [name="email"]').fill('lecturer@example.test');
  await page.getByRole('button', { name: 'Hire Staff' }).click();

  await expect(page.locator('#staffModule tbody')).toContainText('Email Failure Lecturer');
  await expect(page.locator('#staffStatus')).toContainText('email delivery failed');
  await expect(page.locator('#staffStatus')).toContainText('do not submit the hire request again');
  await expect(page.locator('#staffStatus')).not.toContainText('temporary-pass-123');
});

test('admin can create a course with category and teacher assignment', async ({ page }) => {
  await loginAdmin(page);
  const courses = [];
  const teacher = { id: 9, fullName: 'Test Lecturer' };
  let submittedCourse;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/courses' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: courses, number: 0, totalPages: 1, totalElements: courses.length }),
      });
    } else if (url.pathname === '/api/admin/teachers' && request.method() === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: [teacher], number: 0, totalPages: 1, totalElements: 1 }),
      });
    } else if (url.pathname === '/api/admin/course-categories') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([
          { id: 3, name: 'Sciences', code: 'SCIENCES', active: true },
          { id: 4, name: 'Arts', code: 'ARTS', active: true },
        ]),
      });
    } else if (url.pathname === '/api/admin/course-units') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (url.pathname === '/api/admin/courses' && request.method() === 'POST') {
      submittedCourse = request.postDataJSON();
      courses.push({
        id: 51, name: submittedCourse.name, courseCode: submittedCourse.courseCode,
        category: 'Sciences', credits: submittedCourse.credits, teacherFullName: teacher.fullName,
      });
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(courses[0]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Courses' }).click();
  await page.getByRole('button', { name: /Add New Course/ }).click();
  await page.locator('#createCourseForm [name="name"]').fill('Browser Validation Course');
  await page.locator('#createCourseForm [name="courseCode"]').fill('BVC101');
  await page.locator('#createCourseForm [name="categoryId"]').selectOption('3');
  await page.locator('#createCourseForm [name="semester"]').fill('Semester 1');
  await page.locator('#createCourseForm [name="credits"]').fill('3');
  await page.locator('#createCourseForm [name="teacherId"]').selectOption('9');
  await page.getByRole('button', { name: 'Create Course' }).click();

  await expect(page.locator('#coursesModule .courses-grid')).toContainText('BVC101');
  expect(submittedCourse).toMatchObject({
    name: 'Browser Validation Course',
    courseCode: 'BVC101',
    categoryId: 3,
    teacherId: 9,
    semester: 'Semester 1',
    credits: 3,
  });
});

test('admin course category filter uses the categories returned by the server', async ({ page }) => {
  await loginAdmin(page);
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/admin/courses') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { id: 1, name: 'Introduction to Computing', courseCode: 'CST101', category: 'Sciences', teacherFullName: 'Test Lecturer', credits: 3 },
            { id: 2, name: 'Academic Writing', courseCode: 'ART101', category: 'Arts', teacherFullName: 'Test Lecturer', credits: 2 },
          ],
          number: 0, totalPages: 1, totalElements: 2,
        }),
      });
    } else if (url.pathname === '/api/admin/teachers') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: [], number: 0, totalPages: 1, totalElements: 0 }),
      });
    } else if (url.pathname === '/api/admin/course-categories') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([
          { id: 1, name: 'Sciences', code: 'SCIENCES', active: true },
          { id: 2, name: 'Arts', code: 'ARTS', active: true },
        ]),
      });
    } else if (url.pathname === '/api/admin/course-units') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Courses' }).click();
  const categoryFilter = page.locator('[data-admin-filter="course-category"]');
  await expect(categoryFilter.locator('option')).toHaveText(['All Categories', 'Sciences', 'Arts']);
  await expect(page.locator('#coursesModule .course-card')).toHaveCount(2);

  await categoryFilter.selectOption({ label: 'Arts' });
  await expect(page.locator('#coursesModule .course-card:visible')).toHaveCount(1);
  await expect(page.locator('#coursesModule .course-card:visible')).toContainText('ART101');
});

test('admin settings direct calendar and fees to their real operational modules', async ({ page }) => {
  await loginAdmin(page);
  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Settings' }).click();

  const settings = page.locator('#settingsModule');
  await expect(settings).toContainText('Academic years, campuses, faculties, departments, and programmes are managed in Academic Operations.');
  await expect(settings).toContainText('Student fee records are created and reconciled in Finance.');
  await expect(settings.locator('input[type="date"], input[type="text"]')).toHaveCount(0);
  await expect(settings.getByRole('button', { name: 'Save Changes' })).toHaveCount(0);

  await settings.getByRole('button', { name: 'Open Academic Operations' }).click();
  await expect(page.locator('#academicModule')).toHaveClass(/active/);
  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Finance' }).click();
  await expect(page.locator('#financeModule')).toHaveClass(/active/);
});

test('academic operations selects existing courses, examinations, and students by name', async ({ page }) => {
  await loginAdmin(page);
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === '/api/admin/courses') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          content: [{ id: 17, courseCode: 'QA-CS101', name: 'Introduction to Computing' }],
          number: 0, totalPages: 1, totalElements: 1,
        }),
      });
    } else if (pathname === '/api/admin/students') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          content: [{ id: 23, fullName: 'Alex Example', registrationNumber: 'QA-REG-23', username: 'qa-alex' }],
          number: 0, totalPages: 1, totalElements: 1,
        }),
      });
    } else if (pathname === '/api/portal/admin/exams') {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{ id: 31, courseId: 17, title: 'QA Midterm', startsAt: '2026-11-01T09:00:00', venue: 'Test Hall' }]),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Academic Operations' }).click();
  await expect(page.locator('#timetableCourseId')).toContainText('QA-CS101 · Introduction to Computing (#17)');
  await expect(page.locator('#examCourseId')).toContainText('QA-CS101 · Introduction to Computing (#17)');
  await expect(page.locator('#resultExamId')).toContainText('QA Midterm · 2026-11-01 09:00:00 (#31)');
  await expect(page.locator('#resultStudentId')).toContainText('Alex Example · QA-REG-23 (#23)');
});

test('admin can update and inspect a student service request', async ({ page }) => {
  await loginAdmin(page);
  const requests = [{
    id: 71, subject: 'Registration correction', studentName: 'Test Student',
    studentUsername: 'student1', type: 'REGISTRATION', createdAt: '2026-10-09T10:00:00',
    updatedAt: '2026-10-09T10:00:00', status: 'OPEN', details: 'Please correct the name on my registration record.',
    assigneeId: null, assigneeUsername: null,
  }];
  let submittedStatus;
  let submittedAssignment;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/student/requests/admin' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(requests) });
    } else if (url.pathname === '/api/student/requests/admin/assignees' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 14, username: 'admin' }, { id: 15, username: 'registrar' },
      ]) });
    } else if (url.pathname === '/api/student/requests/admin/71' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(requests[0]) });
    } else if (url.pathname === '/api/student/requests/admin/71/history' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { eventType: 'CREATED', actorUsername: 'student1', toStatus: 'OPEN', createdAt: '2026-10-09T10:00:00' },
        { eventType: 'STATUS_CHANGED', actorUsername: 'admin', fromStatus: 'OPEN', toStatus: 'IN_PROGRESS', createdAt: '2026-10-10T11:00:00' },
        { eventType: 'ASSIGNED', actorUsername: 'admin', toAssigneeUsername: 'registrar', createdAt: '2026-10-10T11:30:00' },
      ]) });
    } else if (url.pathname === '/api/student/requests/admin/71/status' && request.method() === 'PATCH') {
      submittedStatus = request.postDataJSON();
      requests[0].status = submittedStatus.status;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(requests[0]) });
    } else if (url.pathname === '/api/student/requests/admin/71/assignee' && request.method() === 'PATCH') {
      submittedAssignment = request.postDataJSON();
      requests[0].assigneeId = submittedAssignment.assigneeId;
      requests[0].assigneeUsername = 'registrar';
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(requests[0]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Requests' }).click();
  const requestRow = page.locator('#requestsModule tbody tr').filter({ hasText: 'Registration correction' });
  await expect(requestRow).toContainText('Test Student');
  await requestRow.getByLabel('Administrator owner for request 71').selectOption('15');
  await expect(requestRow.getByLabel('Administrator owner for request 71')).toHaveValue('15');
  expect(submittedAssignment).toEqual({ assigneeId: 15 });
  await requestRow.getByLabel('Status for request 71').selectOption('IN_PROGRESS');
  await expect(requestRow.getByLabel('Status for request 71')).toHaveValue('IN_PROGRESS');
  expect(submittedStatus).toEqual({ status: 'IN_PROGRESS' });

  await requestRow.getByRole('button', { name: 'View' }).click();
  await expect(page.locator('#modalBox')).toContainText('Please correct the name');
  await expect(page.locator('#modalBox')).toContainText('Status changed from OPEN to IN_PROGRESS by admin');
  await expect(page.locator('#modalBox')).toContainText('Assigned to registrar by admin');
  await expect(page.locator('#modalBox')).toContainText('2026-10-10T11:30:00');
});

test('admin can create a student fee and review the finance row', async ({ page }) => {
  await loginAdmin(page);
  const fees = [];
  let submittedFee;
  const prompts = ['7', 'Semester tuition', '50000', '2026-12-01'];
  let promptIndex = 0;
  page.on('dialog', dialog => dialog.accept(prompts[promptIndex++]));

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/fees' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(fees) });
    } else if (url.pathname === '/api/finance/payments/attempts/pending') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (url.pathname === '/api/fees' && request.method() === 'POST') {
      submittedFee = request.postDataJSON();
      fees.push({
        id: 81, studentId: submittedFee.studentId, studentName: 'Test Student', studentUsername: 'student1',
        description: submittedFee.description, amount: submittedFee.amount, dueDate: submittedFee.dueDate, paid: false,
      });
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(fees[0]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Finance' }).click();
  await page.getByRole('button', { name: 'Create Fee' }).click();

  const feeRows = page.locator('#financeModule > table tbody');
  await expect(feeRows).toContainText('Test Student');
  await expect(feeRows).toContainText('UGX 50,000');
  await expect(feeRows).toContainText('2026-12-01');
  expect(submittedFee).toEqual({
    studentId: 7,
    description: 'Semester tuition',
    amount: 50000,
    dueDate: '2026-12-01',
  });
});

test('public admissions form uses the full desktop width and fits mobile', async ({ page }) => {
  await page.route('**/api/public/admissions/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    const body = pathname.endsWith('/programs')
      ? [{ id: 7, code: 'BSC', name: 'Bachelor of Science in Computing' }]
      : { id: 2, name: '2027/2028 Academic Year' };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/admissions.html');
  await expect(page.locator('#submitAdmissionButton')).toBeEnabled();
  const desktopLayout = await page.locator('.workspace').evaluate(element => ({
    workspaceWidth: element.getBoundingClientRect().width,
    formWidth: element.querySelector('#admissionForm').getBoundingClientRect().width,
  }));
  expect(desktopLayout.workspaceWidth).toBeGreaterThan(1000);
  expect(desktopLayout.formWidth).toBeGreaterThan(1000);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test('partial email campaign failures stay visible and only failed recipients remain selected', async ({ page }) => {
  await loginAdmin(page);
  const recipients = [
    { id: 21, username: 'student-sent', role: 'STUDENT', email: 'sent@example.test' },
    { id: 22, username: 'teacher-failed', role: 'TEACHER', email: 'failed@example.test' },
    { id: 23, username: 'admin-account', role: 'ADMIN', email: 'admin@example.test' },
  ];
  let submittedRecipientIds;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === '/api/auth/authorities') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '["ADMIN"]' });
    } else if (pathname === '/api/admin/email/recipients' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(recipients) });
    } else if (pathname === '/api/admin/email/campaign' && request.method() === 'POST') {
      submittedRecipientIds = request.postDataJSON().recipientIds;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recipientCount: 2,
          sentCount: 1,
          failedCount: 1,
          failedUsernames: ['teacher-failed'],
        }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });
  page.on('dialog', dialog => dialog.accept());

  await page.goto('/messages.html');
  await expect(page.locator('#adminEmailCampaign')).toBeVisible();
  await expect(page.locator('#emailRecipientList')).toContainText('Contact email');
  await expect(page.locator('#emailRecipientList')).toContainText('Account email');
  await page.locator('#emailRecipientList input[data-email-recipient-id="21"]').check();
  await page.locator('#emailRecipientList input[data-email-recipient-id="22"]').check();
  await page.locator('#emailCampaignSubject').fill('Service notice');
  await page.locator('#emailCampaignContent').fill('Please review your request status.');
  await page.locator('#sendEmailCampaign').click();

  const status = page.locator('#emailCampaignStatus');
  await expect(status).toContainText('Sent 1 of 2');
  await expect(status).toContainText('teacher-failed');
  await expect(status).toHaveClass(/error/);
  await expect(page.locator('#emailRecipientList input[data-email-recipient-id="21"]')).not.toBeChecked();
  await expect(page.locator('#emailRecipientList input[data-email-recipient-id="22"]')).toBeChecked();
  expect(submittedRecipientIds).toEqual([21, 22]);
});

test('campaign clears all selections when a reported failed recipient cannot be matched', async ({ page }) => {
  await loginAdmin(page);
  const recipients = [
    { id: 31, username: 'student-sent', role: 'STUDENT', email: 'sent@example.test' },
    { id: 32, username: 'teacher-before-update', role: 'TEACHER', email: 'failed@example.test' },
  ];

  await page.route('**/api/**', async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === '/api/auth/authorities') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '["ADMIN"]' });
    } else if (pathname === '/api/admin/email/recipients' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(recipients) });
    } else if (pathname === '/api/admin/email/campaign' && request.method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recipientCount: 2,
          sentCount: 1,
          failedCount: 1,
          failedUsernames: ['teacher-after-update'],
        }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });
  page.on('dialog', dialog => dialog.accept());

  await page.goto('/messages.html');
  await expect(page.locator('#adminEmailCampaign')).toBeVisible();
  await page.locator('#emailRecipientList input[data-email-recipient-id="31"]').check();
  await page.locator('#emailRecipientList input[data-email-recipient-id="32"]').check();
  await page.locator('#emailCampaignSubject').fill('Service notice');
  await page.locator('#emailCampaignContent').fill('Please review your request status.');
  await page.locator('#sendEmailCampaign').click();

  await expect(page.locator('#emailCampaignStatus'))
    .toContainText('Failed recipients could not all be matched; selection was cleared');
  await expect(page.locator('#emailRecipientList input[data-email-recipient-id="31"]')).not.toBeChecked();
  await expect(page.locator('#emailRecipientList input[data-email-recipient-id="32"]')).not.toBeChecked();
});
