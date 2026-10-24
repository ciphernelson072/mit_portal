const { test, expect } = require('@playwright/test');

let authTestClient = 1;

async function login(page, username, password, adminLogin = false) {
  const role = adminLogin ? (username === 'admin' ? 'ADMIN' : 'TEACHER') : (username === 'admin' ? 'ADMIN' : username === 'teacher1' ? 'TEACHER' : 'STUDENT');
  const forwardedIp = `198.51.100.${authTestClient++}`;
  const loginResponse = await page.request.post('/api/auth/login', {
    data: { username, password },
    headers: { 'x-forwarded-for': forwardedIp },
  });
  expect(loginResponse.ok()).toBeTruthy();
  expect(await loginResponse.json()).toMatchObject({ username, role });

  await page.addInitScript(({ sessionRole, sessionUser }) => {
    localStorage.setItem('mitRole', sessionRole);
    localStorage.setItem('mitUsername', sessionUser);
  }, { sessionRole: role, sessionUser: username });

  const targetPage = role === 'ADMIN' ? '/admin-dashboard.html' : role === 'TEACHER' ? '/teacher-dashboard.html' : '/operations.html';
  await page.goto(targetPage);
  await expect(page).toHaveURL(new RegExp(role === 'ADMIN' ? 'admin-dashboard\.html' : role === 'TEACHER' ? 'teacher-dashboard\.html' : 'operations\.html'));
}

async function expectNoHorizontalOverflow(page) {
  await expect.poll(() => page.evaluate(() => ({
    width: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth,
  }))).toEqual({ width: 390, viewport: 390 });
}

test('Prometheus metrics are available only after authentication', async ({ page }) => {
  const anonymousResponse = await page.request.get('/actuator/prometheus');
  expect(anonymousResponse.status()).toBe(401);

  await login(page, 'admin', 'admin123', true);
  const authenticatedResponse = await page.request.get('/actuator/prometheus');
  expect(authenticatedResponse.status()).toBe(200);
  expect(await authenticatedResponse.text()).toContain('jvm_memory_used_bytes');
});

test('admin login routes first-time password changes before opening the dashboard', async ({ page }) => {
  await page.route('**/api/auth/login', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      role: 'ADMIN',
      username: 'ciphernelson072',
      passwordChangeRequired: true,
    }),
  }));

  await page.goto('/admin-login.html');
  await page.evaluate(() => {
    localStorage.setItem('mitRole', 'STUDENT');
    localStorage.setItem('mitUsername', 'old-student-session');
  });
  await page.locator('#username').fill('ciphernelson072');
  await page.locator('#password').fill('temporary-password');
  await page.locator('.btn-login').click();

  await expect(page).toHaveURL(/force-password-change\.html/);
  await expect(page.locator('#passwordChangeForm')).toBeVisible();
  await expect.poll(() => page.evaluate(() => ({
    role: localStorage.getItem('mitRole'),
    username: localStorage.getItem('mitUsername'),
    passwordChangeRequired: localStorage.getItem('mitPasswordChangeRequired'),
  }))).toEqual({
    role: 'ADMIN',
    username: 'ciphernelson072',
    passwordChangeRequired: 'true',
  });
});

test('admin password change returns to the staff login form', async ({ page }) => {
  await page.route('**/api/auth/change-password', route => route.fulfill({ status: 200 }));
  await page.route('**/api/auth/logout', route => route.fulfill({ status: 200 }));
  await page.goto('/admin-login.html');
  await page.evaluate(() => {
    localStorage.setItem('mitRole', 'ADMIN');
    localStorage.setItem('mitUsername', 'ciphernelson072');
    localStorage.setItem('mitPasswordChangeRequired', 'true');
  });
  await page.goto('/force-password-change.html');

  await page.locator('#currentPassword').fill('temporary-password');
  await page.locator('#newPassword').fill('New-strong-password-2026!');
  await page.locator('#confirmPassword').fill('New-strong-password-2026!');
  await page.locator('#passwordChangeSubmit').click();

  await expect(page).toHaveURL(/admin-login\.html/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('mitRole'))).toBeNull();
});

test.describe('responsive portal surfaces', () => {
  test.use({ viewport: { width: 390, height: 800 } });

  test('admin dashboard and file management fit mobile viewport', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    await page.goto('/admin-dashboard.html');
    await expectNoHorizontalOverflow(page);
    await page.goto('/file-management.html');
    await expectNoHorizontalOverflow(page);
  });

  test('teacher dashboard fits mobile viewport', async ({ page }) => {
    await login(page, 'teacher1', 'teacher123', true);
    await page.goto('/teacher-dashboard.html');
    await expectNoHorizontalOverflow(page);
  });

  test('unauthorized HTML navigation shows a useful page', async ({ page }) => {
    const response = await page.goto('/teacher-dashboard.html');
    expect(response.status()).toBe(200);
    await expect(page).toHaveURL(/unauthorized\.html\?reason=login/);
    await expect(page.getByRole('heading', { name: 'Access restricted' })).toBeVisible();
  });

  test('student can reach the role-aware operations workspace', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.goto('/operations.html');
    await expect(page.locator('#roleBadge')).toHaveText('STUDENT');
    await expect(page.locator('.workflow-card')).not.toHaveCount(0);
    await expectNoHorizontalOverflow(page);
  });

  test('student checkout shows configured channels and submits for verification', async ({ page }) => {
    await login(page, 'student1', 'student123');
    let submittedAttempt;
    await page.route('**/api/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname === '/api/fees') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
          { id: 42, description: 'Semester tuition', amount: 50000, paid: false, dueDate: '2026-10-30' },
        ]) });
      } else if (url.pathname === '/api/finance/payments/destinations') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
          { channel: 'BANK_TRANSFER', label: 'Bank transfer', available: true, destination: 'MIT Collections | 0000000000 | Test Branch', instructions: 'Use your student number and fee ID.' },
          { channel: 'PAYPAL', label: 'PayPal', available: false, destination: 'PayPal account not configured', instructions: 'Keep the PayPal transaction ID.' },
          { channel: 'MASTERCARD', label: 'Mastercard', available: false, destination: 'Hosted card gateway not configured', instructions: 'Never enter card details in this portal.' },
          { channel: 'AIRTEL_MONEY', label: 'Airtel Money', available: false, destination: 'Merchant details not configured', instructions: 'Keep the mobile-money transaction ID.' },
          { channel: 'MTN_MOMO', label: 'MTN MoMo', available: false, destination: 'Merchant details not configured', instructions: 'Keep the mobile-money transaction ID.' },
        ]) });
      } else if (url.pathname === '/api/finance/payments/attempts/mine') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(submittedAttempt ? [submittedAttempt] : []) });
      } else if (url.pathname === '/api/finance/payments/attempts' && request.method() === 'POST') {
        submittedAttempt = { id: 1, feeId: 42, amount: 50000, paymentChannel: 'BANK_TRANSFER', providerReference: JSON.parse(request.postData()).providerReference, status: 'PENDING' };
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(submittedAttempt) });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
    });

    await page.goto('/finance.html');
    await page.getByRole('button', { name: /Pending Payments/ }).click();
    await page.getByRole('button', { name: 'Choose payment method' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('#paymentChannels')).toContainText('MIT Collections');
    await expect(page.locator('input[value="MASTERCARD"]')).toBeDisabled();
    await expect(page.locator('input[name="cardNumber"]')).toHaveCount(0);

    await page.locator('input[value="BANK_TRANSFER"]').check();
    await expect(page.locator('#selectedPaymentInstructions')).toContainText('student number and fee ID');
    await page.locator('#providerReference').fill('bank-transfer-reference-42');
    await page.getByRole('button', { name: 'Submit for verification' }).click();
    await expect.poll(() => submittedAttempt?.paymentChannel).toBe('BANK_TRANSFER');
    await expect(page.locator('#paymentAttemptsList')).toContainText('PENDING');
    await expect(page.locator('#paymentAttemptsList')).toContainText('bank-transfer-reference-42');
  });

  test('admin can confirm or reject queued payment attempts', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    await page.setViewportSize({ width: 1280, height: 900 });
    const pending = [
      { id: 81, feeId: 42, studentId: 7, amount: 50000, paymentChannel: 'BANK_TRANSFER', providerReference: 'bank-ref-confirm-81', createdAt: '2026-10-02T08:30:00', status: 'PENDING' },
      { id: 82, feeId: 43, studentId: 8, amount: 25000, paymentChannel: 'MTN_MOMO', providerReference: 'mtn-ref-reject-82', createdAt: '2026-10-02T09:00:00', status: 'PENDING' },
    ];
    let rejectionPayload;
    await page.route('**/api/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname === '/api/admin/fees') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
          { id: 42, amount: 50000, paid: false, dueDate: '2026-10-30', studentName: 'Test Student', studentUsername: 'student1' },
        ]) });
      } else if (url.pathname === '/api/finance/payments/attempts/pending') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pending) });
      } else if (url.pathname.endsWith('/81/confirm') && request.method() === 'POST') {
        pending.splice(pending.findIndex(attempt => attempt.id === 81), 1);
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 81, status: 'COMPLETED' }) });
      } else if (url.pathname.endsWith('/82/reject') && request.method() === 'POST') {
        rejectionPayload = JSON.parse(request.postData());
        pending.splice(pending.findIndex(attempt => attempt.id === 82), 1);
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 82, status: 'FAILED', reviewNote: rejectionPayload.reason }) });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
    });

    await page.getByRole('link', { name: 'Finance' }).click();
    await expect(page.locator('#paymentAttemptTable')).toContainText('bank-ref-confirm-81');
    await expect(page.locator('#paymentAttemptTable')).toContainText('mtn-ref-reject-82');
    await expect(page.getByRole('button', { name: 'Mark Paid' })).toHaveCount(0);
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('row').filter({ hasText: 'bank-ref-confirm-81' }).getByRole('button', { name: 'Confirm verified' }).click();
    await expect(page.locator('#paymentAttemptTable')).not.toContainText('bank-ref-confirm-81');

    page.once('dialog', dialog => dialog.accept('Provider statement did not match'));
    await page.getByRole('row').filter({ hasText: 'mtn-ref-reject-82' }).getByRole('button', { name: 'Reject' }).click();
    await expect(page.locator('#paymentAttemptTable')).toContainText('No pending payment attempts');
    expect(rejectionPayload).toEqual({ reason: 'Provider statement did not match' });
  });

  test('student notifications page renders real notification data', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/notifications') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 1, title: 'Registration Deadline', message: 'Submit your registration form by Friday.', createdAt: '2026-10-02T08:00:00Z', read: false },
            { id: 2, title: 'Grade Published', message: 'Your CS101 grade is now available.', createdAt: '2026-09-30T10:00:00Z', read: true },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/notifications.html');
    await expect(page.getByRole('heading', { name: 'Notifications', exact: true })).toBeVisible();
    await expect(page.locator('#notificationsList .notification-item')).toHaveCount(2);
    await expect(page.locator('#notificationsList')).toContainText('Registration Deadline');
    await expect(page.locator('#notificationsList')).toContainText('Grade Published');
  });

  test('student messages page renders real inbox and sent data', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/messages/received') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 10, senderUsername: 'teacher1', subject: 'Assessment reminder', content: 'Please review your lab submission.', sentAt: '2026-10-01T09:00:00Z', read: false },
          ]),
        });
        return;
      }
      if (url.pathname === '/api/messages/sent') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 12, senderUsername: 'student1', receiverUsername: 'admin', subject: 'Fee clarification', content: 'I need help with the outstanding balance.', sentAt: '2026-09-29T15:00:00Z' },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/messages.html');
    await expect(page.getByRole('heading', { name: 'Messages', exact: true })).toBeVisible();
    await expect(page.locator('#inboxList .message-item')).toHaveCount(1);
    await expect(page.locator('#sentList .message-item')).toHaveCount(1);
    await expect(page.locator('#inboxList')).toContainText('Assessment reminder');
    await expect(page.locator('#sentList')).toContainText('Fee clarification');
  });

  test('student can send a message to another active portal account', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.goto('/messages.html');
    await page.getByRole('button', { name: 'Compose' }).click();

    const subject = `E2E message ${Date.now()}`;
    const composeForm = page.locator('#composeForm');
    await page.getByLabel('Recipient Username').fill('teacher1');
    await composeForm.getByLabel('Subject').fill(subject);
    await composeForm.getByLabel('Message').fill('Message delivery regression check.');

    const sendResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/messages') &&
      response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: 'Send message' }).click();

    expect((await sendResponse).status()).toBe(201);
    await expect(page.locator('#sentList')).toContainText(subject);
    await expect(page.locator('#composeCard')).toBeHidden();
  });

  test('teacher and admin can use messages and return to their own dashboard', async ({ page }) => {
    for (const account of [
      { username: 'teacher1', password: 'teacher123', dashboard: 'teacher-dashboard.html' },
      { username: 'admin', password: 'admin123', dashboard: 'admin-dashboard.html' },
    ]) {
      await login(page, account.username, account.password, true);
      await page.route('**/api/messages/**', route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '[]',
      }));
      await page.goto('/messages.html');
      await expect(page.locator('.header-actions a')).toHaveAttribute('href', account.dashboard);
      await expect(page.getByRole('heading', { name: 'Messages', exact: true })).toBeVisible();
    }
  });

  test('student assignment submission uses an accessible validated form', async ({ page }) => {
    await login(page, 'student1', 'student123');
    let submittedBody;
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/portal/student/assignments/42/submissions') {
        submittedBody = route.request().postDataJSON();
        await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
        return;
      }
      const body = url.pathname === '/api/portal/student/assignments'
        ? [{ id: 42, title: 'Database normalization', description: 'Explain the process.', dueAt: '2027-01-01T12:00:00' }]
        : url.pathname === '/api/student/profile' ? { id: 7, cgpa: 3.5 } : [];
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/academics.html#assignments');
    const submit = page.getByRole('button', { name: 'Submit', exact: true });
    await expect(submit).toBeVisible();
    await submit.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByLabel('Your submission (up to 2,000 characters)').fill('Normalization organizes data to reduce duplication.');
    const successAlert = page.waitForEvent('dialog').then(async dialog => {
      expect(dialog.message()).toBe('Assignment submitted successfully.');
      await dialog.accept();
    });
    await Promise.all([
      page.getByRole('button', { name: 'Submit', exact: true }).last().click(),
      successAlert,
    ]);
    await expect.poll(() => submittedBody).toEqual({ content: 'Normalization organizes data to reduce duplication.' });
  });

  test('academics displays course metadata and approved-credit calculations', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      let body = [];
      if (url.pathname === '/api/student/grades') {
        body = [
          { id: 1, courseCode: 'CS401', courseName: 'Database Systems', credits: 3, gradeValue: 'A', gpaScore: 4, semester: 'Semester 4', status: 'APPROVED' },
          { id: 2, courseCode: 'CS402', courseName: 'Data Mining', credits: 2, gradeValue: 'B', gpaScore: 3, semester: 'Semester 4', status: 'APPROVED' },
        ];
      } else if (url.pathname === '/api/student/profile') {
        body = { id: 7, cgpa: 3.5 };
      } else if (url.pathname === '/api/academic-records/7/transcript') {
        body = { studentName: 'Test Student', cgpa: 3.5, courseCount: 0, grades: [] };
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/academics.html#grades');
    const row = page.locator('.grades-table tbody tr').first();
    await expect(row.locator('td')).toHaveText(['CS401', 'Database Systems', '3', 'A', '4.00', 'Semester 4']);
    await expect(page.locator('.gpa-card .gpa-value').nth(1)).toHaveText('3.60');
    await expect(page.locator('.gpa-card .gpa-value').nth(2)).toHaveText('5');
  });

  test('student documents page renders real uploaded files and upload form', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/student/documents') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 7, name: 'Admission Letter.pdf', uploadedAt: '2026-09-15T12:00:00Z', status: 'APPROVED' },
            { id: 8, name: 'ID Copy.png', uploadedAt: '2026-09-28T17:30:00Z', status: 'PENDING' },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/documents.html');
    await expect(page.getByRole('heading', { name: 'Documents', exact: true })).toBeVisible();
    await expect(page.locator('#documentsList .document-item')).toHaveCount(2);
    await expect(page.locator('#documentsList')).toContainText('Admission Letter.pdf');
    await expect(page.locator('#documentsList')).toContainText('ID Copy.png');
    await expect(page.locator('#uploadForm')).toBeVisible();
  });

  test('student scholarships page renders real scholarship offers', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/portal/scholarships') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 9, name: 'Merit Award', description: 'For academic excellence', amount: 2500000, open: true },
            { id: 10, name: 'Need Based Fund', description: 'For demonstrated need', amount: 1800000, open: true },
          ]),
        });
        return;
      }
      if (url.pathname === '/api/portal/student/scholarship-applications') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 1, scholarshipId: 9, status: 'PENDING' },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/scholarships.html');
    await expect(page.getByRole('heading', { name: 'Scholarships', exact: true })).toBeVisible();
    await expect(page.locator('#scholarshipList .scholarship-card')).toHaveCount(2);
    await expect(page.locator('#scholarshipList')).toContainText('Merit Award');
    await expect(page.locator('#scholarshipList')).toContainText('Need Based Fund');
    await expect(page.locator('#scholarshipList')).toContainText('Apply');
  });

  test('student support tickets page renders real help desk requests', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/student/requests') {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([
              { id: 33, type: 'SUPPORT_TICKET', subject: 'Missing lecture recording', details: 'I need the April 18 recording.', status: 'OPEN', createdAt: '2026-09-30T08:00:00Z' },
              { id: 34, type: 'FINANCE', subject: 'Fee mismatch', details: 'My balance is incorrect.', status: 'IN_PROGRESS', createdAt: '2026-10-01T10:00:00Z' },
            ]),
          });
          return;
        }
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({ id: 35, type: 'HOSTEL', subject: 'Room change request', details: 'Need a quieter room.', status: 'OPEN', createdAt: '2026-10-02T09:00:00Z' }),
          });
          return;
        }
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/support-tickets.html');
    await expect(page.getByRole('heading', { name: 'Support Tickets', exact: true })).toBeVisible();
    await expect(page.locator('#ticketsList .ticket-item')).toHaveCount(2);
    await expect(page.locator('#ticketsList')).toContainText('Missing lecture recording');
    await expect(page.locator('#ticketsList')).toContainText('Fee mismatch');
    await expect(page.locator('#ticketForm')).toBeVisible();
  });

  test('student accommodation and transport page renders real campus service data', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/campus/student/hostel') {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([
              { id: 11, studentId: 7, details: 'Need single room due to medical reasons', status: 'OPEN', createdAt: '2026-09-15T10:00:00Z' },
              { id: 12, studentId: 7, details: 'Request for quiet block', status: 'APPROVED', createdAt: '2026-09-28T10:00:00Z' },
            ]),
          });
          return;
        }
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({ id: 13, studentId: 7, details: 'Need safer access near the library', status: 'OPEN', createdAt: '2026-10-02T09:00:00Z' }),
          });
          return;
        }
      }
      if (url.pathname === '/api/campus/student/transport-pass') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 6, studentId: 7, status: 'ACTIVE', validUntil: '2026-12-31' }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/hostel.html');
    await expect(page.getByRole('heading', { name: 'Accommodation & Transport', exact: true })).toBeVisible();
    await expect(page.locator('#hostelList .hostel-item')).toHaveCount(2);
    await expect(page.locator('#hostelList')).toContainText('Need single room due to medical reasons');
    await expect(page.locator('#transportPass')).toContainText('ACTIVE');
    await expect(page.locator('#hostelForm')).toBeVisible();
  });

  test('student announcements page renders live portal announcements', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/announcements') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 7, title: 'Orientation week starts Monday', content: 'All new students should collect their IDs at the registrar desk.', createdAt: '2026-10-02T08:00:00Z', authorName: 'admin' },
            { id: 8, title: 'Library access update', content: 'The library opens earlier this week for exam revision.', createdAt: '2026-10-01T15:00:00Z', authorName: 'admin' },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/announcements.html');
    await expect(page.getByRole('heading', { name: 'Announcements', exact: true })).toBeVisible();
    await expect(page.locator('#announcementList .announcement-item')).toHaveCount(2);
    await expect(page.locator('#announcementList')).toContainText('Orientation week starts Monday');
    await expect(page.locator('#announcementList')).toContainText('Library access update');
  });

  test('student library page renders catalog and active loans', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/library/catalog') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 1, isbn: '978-1-2345-6789-0', title: 'Data Structures Essentials', author: 'N. Mutesa', totalCopies: 4, availableCopies: 2, active: true },
            { id: 2, isbn: '978-1-2345-6789-1', title: 'Algorithms in Practice', author: 'K. Otim', totalCopies: 3, availableCopies: 1, active: true },
          ]),
        });
        return;
      }
      if (url.pathname === '/api/library/loans/mine') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 10, libraryItemId: 1, studentId: 7, borrowedAt: '2026-09-25T08:00:00Z', dueDate: '2026-10-13', status: 'BORROWED' },
          ]),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/library.html');
    await expect(page.getByRole('heading', { name: 'Library', exact: true })).toBeVisible();
    await expect(page.locator('#catalogList .catalog-item')).toHaveCount(2);
    await expect(page.locator('#catalogList')).toContainText('Data Structures Essentials');
    await expect(page.locator('#loanList')).toContainText('Data Structures Essentials');
    await expect(page.locator('#catalogList')).toContainText('available');
  });

  test('student research page renders projects and thesis submission form', async ({ page }) => {
    await login(page, 'student1', 'student123');
    let proposalBody;
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/research/projects/mine') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 11, title: 'AI for campus safety', abstractText: 'Using predictive analytics for better campus security.', supervisorUserId: 22, status: 'APPROVED', createdAt: '2026-09-16T09:00:00Z' },
            { id: 12, title: 'Digital library indexing', abstractText: 'Improving search and metadata quality for institutional research access.', supervisorUserId: 23, status: 'PROPOSED', createdAt: '2026-09-28T09:00:00Z' },
          ]),
        });
        return;
      }
      if (url.pathname === '/api/research/supervisors') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ userId: 22, fullName: 'Ruth Nakato', department: 'Computer Science' }]),
        });
        return;
      }
      if (url.pathname === '/api/research/theses/mine') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ id: 20, researchProjectId: 11, notes: 'Submitted for review', status: 'SUBMITTED', submittedAt: '2026-10-02T10:00:00Z' }]),
        });
        return;
      }
      if (url.pathname === '/api/research/projects') {
        proposalBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 13, title: 'Smart attendance forecasting', abstractText: 'Predict risk patterns in attendance trends.', supervisorUserId: 24, status: 'PROPOSED', createdAt: '2026-10-02T09:00:00Z' }),
        });
        return;
      }
      if (url.pathname === '/api/research/theses') {
        expect(route.request().headers()['content-type']).toContain('multipart/form-data');
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 20, researchProjectId: 11, notes: 'Submitted for review', status: 'SUBMITTED', submittedAt: '2026-10-02T10:00:00Z' }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.goto('/research.html');
    await expect(page.getByRole('heading', { name: 'Research', exact: true })).toBeVisible();
    await expect(page.locator('#projectList .project-item')).toHaveCount(2);
    await expect(page.locator('#projectList')).toContainText('AI for campus safety');
    await expect(page.locator('#projectForm')).toBeVisible();
    await expect(page.locator('#thesisForm')).toBeVisible();
    await expect(page.locator('#supervisorUserSelect option')).toContainText(['Ruth Nakato · Computer Science']);
    await expect(page.locator('#thesisProjectSelect option')).toHaveCount(1);
    await expect(page.locator('#thesisList')).toContainText('Submitted for review');
    await page.locator('#projectForm input[name="title"]').fill('Digital access and retrieval');
    await page.locator('#projectForm select[name="supervisorUserId"]').selectOption('22');
    await page.getByRole('button', { name: 'Save proposal' }).click();
    await expect.poll(() => proposalBody).toMatchObject({ title: 'Digital access and retrieval', supervisorUserId: 22 });
    await page.locator('#thesisForm input[name="file"]').setInputFiles({
      name: 'final-thesis.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.7 test'),
    });
    await page.locator('#thesisForm textarea[name="notes"]').fill('Submitted for review');
    await page.getByRole('button', { name: 'Submit thesis' }).click();
    await expect(page.locator('#thesisStatus')).toHaveText('Thesis submitted successfully.');
  });

  test('student login validates required input before submission', async ({ page }) => {
    await page.goto('/index.html');
    await page.getByRole('button', { name: /SIGN IN/ }).click();
    await expect(page.locator('#username')).toHaveJSProperty('validationMessage', 'Please fill out this field.');
    await expect(page).toHaveURL(/index\.html/);
  });

  test('newly provisioned student must change temporary password before portal access', async ({ page }) => {
    let changedPassword;
    await page.route('**/api/auth/**', async route => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (path === '/api/auth/login') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
          role: 'STUDENT', username: 'new-student', passwordChangeRequired: true,
        }) });
      } else if (path === '/api/auth/change-password') {
        changedPassword = request.postDataJSON();
        await route.fulfill({ status: 200 });
      } else if (path === '/api/auth/logout') {
        await route.fulfill({ status: 200 });
      } else {
        await route.fulfill({ status: 403 });
      }
    });

    await page.goto('/index.html');
    await page.locator('#username').fill('new-student');
    await page.locator('#password').fill('TemporaryPass123');
    await page.getByRole('button', { name: /SIGN IN/ }).click();
    await expect(page).toHaveURL(/force-password-change\.html/);
    await expect(page.getByRole('heading', { name: 'Set a new password' })).toBeVisible();

    await page.locator('#currentPassword').fill('TemporaryPass123');
    await page.locator('#newPassword').fill('PermanentPass456');
    await page.locator('#confirmPassword').fill('PermanentPass456');
    await page.getByRole('button', { name: 'Change password' }).click();
    await expect(page).toHaveURL(/index\.html/);
    expect(changedPassword).toEqual({
      currentPassword: 'TemporaryPass123',
      newPassword: 'PermanentPass456',
    });
  });

  test('login forms stay contained and do not collide across viewport sizes', async ({ page }) => {
    for (const viewport of [
      { width: 320, height: 420 },
      { width: 320, height: 640 },
      { width: 360, height: 568 },
      { width: 360, height: 740 },
      { width: 390, height: 844 },
      { width: 768, height: 1024 },
      { width: 1440, height: 900 },
    ]) {
      await page.setViewportSize(viewport);
      for (const route of [
        { url: '/index.html', container: '.card' },
        { url: '/reset-password.html', container: '.card' },
        { url: '/verify-email.html', container: '.card' },
        { url: '/admin-login.html', container: '.login-container' },
        { url: '/force-password-change.html', container: 'main' },
      ]) {
        if (route.url === '/force-password-change.html') {
          await login(page, 'student1', 'student123');
          await page.evaluate(() => {
            localStorage.setItem('mitPasswordChangeRequired', 'true');
          });
        }
        await page.goto(route.url);
        const layout = await page.evaluate(containerSelector => {
          const container = document.querySelector(containerSelector);
          const bounds = element => {
            const rect = element.getBoundingClientRect();
            return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
          };
          const containerBounds = bounds(container);
          const controls = [...container.querySelectorAll('input:not([type="checkbox"]), button[type="submit"]')]
            .filter(element => element.getClientRects().length)
            .map(bounds);
          return {
            viewportWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth,
            documentHeight: document.documentElement.scrollHeight,
            container: containerBounds,
            controls,
          };
        }, route.container);
        expect(layout.scrollWidth, `${route.url} horizontal overflow at ${viewport.width}px`).toBeLessThanOrEqual(viewport.width);
        expect(layout.container.left, `${route.url} left edge at ${viewport.width}px`).toBeGreaterThanOrEqual(0);
        expect(layout.container.right, `${route.url} right edge at ${viewport.width}px`).toBeLessThanOrEqual(viewport.width);
        expect(layout.container.top, `${route.url} top edge at ${viewport.height}px`).toBeGreaterThanOrEqual(0);
        expect(layout.documentHeight, `${route.url} clipped content at ${viewport.height}px`).toBeGreaterThanOrEqual(layout.container.bottom);
        for (let index = 0; index < layout.controls.length; index++) {
          const control = layout.controls[index];
          expect(control.left, `${route.url} control left at ${viewport.width}px`).toBeGreaterThanOrEqual(layout.container.left);
          expect(control.right, `${route.url} control right at ${viewport.width}px`).toBeLessThanOrEqual(layout.container.right);
          if (index > 0) {
            expect(control.top, `${route.url} controls overlap at ${viewport.width}px`).toBeGreaterThanOrEqual(layout.controls[index - 1].bottom);
          }
        }
      }
    }
  });

  test('student, teacher, and admin dashboard headers and workspaces do not collide', async ({ page }) => {
    test.setTimeout(90000);
    const viewports = [
      { width: 320, height: 640 },
      { width: 390, height: 844 },
      { width: 768, height: 1024 },
      { width: 1024, height: 768 },
      { width: 1440, height: 900 },
    ];
    const roles = [
      { username: 'student1', password: 'student123', adminLogin: false, url: '/dashboard.html', checks: [['.header-logo', '.header-actions'], ['.profile-summary', '.dashboard-grid']] },
      { username: 'teacher1', password: 'teacher123', adminLogin: true, url: '/teacher-dashboard.html', checks: [['.sidebar', '.main-content'], ['.header-title', '.theme-controls'], ['.theme-controls', '.teacher-notifications']] },
      { username: 'admin', password: 'admin123', adminLogin: true, url: '/admin-dashboard.html', checks: [['.sidebar', '.main-content'], ['.header-title', '.header-actions']] },
    ];

    for (const role of roles) {
      await login(page, role.username, role.password, role.adminLogin);
      for (const viewport of viewports) {
        await page.setViewportSize(viewport);
        await page.goto(role.url);
        await expect.poll(() => page.evaluate(() => ({
          width: document.documentElement.scrollWidth,
          viewport: document.documentElement.clientWidth,
        }))).toEqual({ width: viewport.width, viewport: viewport.width });
        if (viewport.width <= 480 && role.url === '/admin-dashboard.html') {
          await expect(page.locator('.sidebar-nav')).toBeVisible();
          await expect(page.locator('.sidebar-nav .nav-item').filter({ hasText: 'Students' })).toBeVisible();
          expect(await page.locator('.sidebar-nav').evaluate(element => element.scrollWidth > element.clientWidth)).toBeTruthy();
        }
        if (viewport.width <= 480 && role.url === '/teacher-dashboard.html') {
          await expect(page.locator('.sidebar-footer .btn-logout')).toBeVisible();
        }

        const overlaps = await page.evaluate(pairs => pairs.map(([leftSelector, rightSelector]) => {
          const leftElement = document.querySelector(leftSelector);
          const rightElement = document.querySelector(rightSelector);
          if (!leftElement || !rightElement) return { pair: [leftSelector, rightSelector], overlap: false };
          const left = leftElement.getBoundingClientRect();
          const right = rightElement.getBoundingClientRect();
          if (!leftElement.getClientRects().length || !rightElement.getClientRects().length) {
            return { pair: [leftSelector, rightSelector], overlap: false };
          }
          return {
            pair: [leftSelector, rightSelector],
            overlap: left.left < right.right && left.right > right.left && left.top < right.bottom && left.bottom > right.top,
            bounds: [left.left, left.top, left.right, left.bottom, right.left, right.top, right.right, right.bottom],
          };
        }), role.checks);
        expect(overlaps.filter(item => item.overlap), `${role.url} regions overlap at ${viewport.width}px`).toEqual([]);
      }
    }
  });

  test('Operations search and JSON validation are interactive', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    await page.goto('/operations.html');
    await expect(page.locator('.workflow-card').first()).toBeVisible();
    const initialCards = await page.locator('.workflow-card').count();
    await page.locator('#searchInput').fill('library');
    await expect(page.locator('.workflow-card')).toHaveCount(1);
    expect(initialCards).toBeGreaterThan(1);
    await page.locator('.endpoint button').filter({ hasText: 'Open' }).first().click();
    await expect(page.locator('#fieldGrid [data-field]')).not.toHaveCount(0);
    await expect(page.locator('#requestDialog')).toBeVisible();
  });

  test('Operations home and logout destinations follow the authenticated role', async ({ page }) => {
    const roleCases = [
      { username: 'admin', password: 'admin123', adminLogin: true, role: 'ADMIN', dashboard: 'admin-dashboard.html', loginPage: 'admin-login.html' },
      { username: 'teacher1', password: 'teacher123', adminLogin: true, role: 'TEACHER', dashboard: 'teacher-dashboard.html', loginPage: 'admin-login.html' },
      { username: 'student1', password: 'student123', adminLogin: false, role: 'STUDENT', dashboard: 'dashboard.html', loginPage: 'index.html' },
    ];

    for (const roleCase of roleCases) {
      const loginResponse = await page.request.post('/api/auth/login', {
        data: { username: roleCase.username, password: roleCase.password },
        headers: { 'x-forwarded-for': `198.51.100.${authTestClient++}` },
      });
      expect(loginResponse.ok()).toBeTruthy();
      expect(await loginResponse.json()).toMatchObject({ role: roleCase.role });
      await page.goto('/admin-login.html');
      await page.evaluate(({ role, username }) => {
        localStorage.setItem('mitRole', role);
        localStorage.setItem('mitUsername', username);
      }, { role: roleCase.role, username: roleCase.username });
      await page.goto('/operations.html');
      await expect(page.locator('#roleBadge')).toHaveText(roleCase.role);
      await expect(page.locator('#homeLink')).toHaveAttribute('href', roleCase.dashboard);
      await page.locator('#logoutButton').click();
      await expect(page).toHaveURL(new RegExp(`/${roleCase.loginPage}$`));
      await page.waitForLoadState('domcontentloaded');
      expect(await page.evaluate(() => localStorage.getItem('mitRole'))).toBeNull();
    }
  });

  test('Operations does not trust a forged localStorage administrator role', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/auth/authorities', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(['STUDENT']),
    }));
    await page.evaluate(() => localStorage.setItem('mitRole', 'ADMIN'));
    await page.goto('/operations.html');

    await expect(page.locator('#roleBadge')).toHaveText('STUDENT');
    await expect(page.locator('.workflow-card').first()).toBeVisible();
    await expect(page.locator('.endpoint').filter({ hasText: 'Create student fee' })).toHaveCount(0);
    await expect(page.locator('.endpoint').filter({ hasText: 'Create room' })).toHaveCount(0);
  });

  test('Operations shows delegated workflows from server authorities', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/auth/authorities', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(['STUDENT', 'PERM_ACCOMMODATION_MANAGE']),
    }));
    await page.goto('/operations.html');

    await expect(page.locator('#roleBadge')).toHaveText('STUDENT');
    await expect(page.locator('.endpoint').filter({ hasText: 'Create room' })).toHaveCount(1);
    await expect(page.locator('.endpoint').filter({ hasText: 'Create student fee' })).toHaveCount(0);
  });

  test('Operations sends institution filters as GET query parameters', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    await page.goto('/operations.html');
    await page.locator('#domainFilter').selectOption({ label: 'Institution directory' });
    await page.route('**/api/institution/faculties?*', async route => {
      expect(route.request().method()).toBe('GET');
      expect(route.request().url()).toContain('campusId=42');
      expect(route.request().postData()).toBeNull();
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.locator('.endpoint').filter({ hasText: 'Faculties' }).getByRole('button', { name: 'Load' }).click();
    await page.locator('#fieldGrid [data-query="campusId"]').fill('42');
    await page.locator('#sendButton').click();
    await expect(page.locator('#resultContent')).toContainText('[]');
  });

  test('Operations admission enrollment matches account-creation and candidate APIs', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    let enrollment;
    await page.route('**/api/admissions/applications/41/enroll-new', async route => {
      enrollment = route.request().postDataJSON();
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ username: 'test-applicant' }) });
    });
    await page.goto('/operations.html');
    await page.locator('#domainFilter').selectOption({ label: 'Admissions' });
    await expect(page.locator('.endpoint').filter({ hasText: 'Find matching student accounts' })).toHaveCount(1);
    await expect(page.locator('.endpoint').filter({ hasText: 'Create account and enroll accepted applicant' })).toHaveCount(1);

    await page.locator('.endpoint').filter({ hasText: 'Create account and enroll accepted applicant' }).getByRole('button', { name: 'Open' }).click();
    await expect(page.locator('#requestPath')).toHaveText('/admissions/applications/{id}/enroll-new');
    await expect(page.locator('#fieldGrid [data-field="phone"]')).not.toHaveAttribute('required', '');
    await page.locator('#fieldGrid [data-path-param="id"]').fill('41');
    await page.locator('#fieldGrid [data-field="username"]').fill('test-applicant');
    await page.locator('#fieldGrid [data-field="fullName"]').fill('Alex Example');
    await page.locator('#fieldGrid [data-field="email"]').fill('alex@example.test');
    await page.locator('#fieldGrid [data-field="phone"]').fill('+256700000001');
    await page.locator('#sendButton').click();
    await expect.poll(() => enrollment).toEqual({
      username: 'test-applicant',
      fullName: 'Alex Example',
      email: 'alex@example.test',
      phone: '+256700000001',
    });
  });

  test('research reviewers can load and download pending thesis files', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/auth/authorities') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(['ADMIN', 'PERM_RESEARCH_REVIEW']) });
      } else if (url.pathname === '/api/research/theses/pending') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ id: 31, researchProjectId: 11, notes: 'Ready for review', status: 'SUBMITTED' }]),
        });
      } else if (url.pathname === '/api/research/theses/31/download') {
        await route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.7 thesis' });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
    });
    await page.goto('/operations.html');
    await page.locator('#domainFilter').selectOption({ label: 'Research and theses' });
    await page.locator('.endpoint').filter({ hasText: 'Pending theses' }).getByRole('button', { name: 'Load' }).click();
    await expect(page.locator('#resultContent')).toContainText('Ready for review');
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download thesis PDF' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('thesis-submission-31.pdf');
  });

  test('admin can approve and reject teacher-submitted grades', async ({ page }) => {
    await login(page, 'admin', 'admin123', true);

    let grades = [
      { id: 101, studentId: 7, studentUsername: 'student1', studentName: 'Alice Student', courseId: 2, courseName: 'Computer Science', gradeValue: 'B+', gpaScore: 3.3, status: 'PENDING', submittedBy: 'teacher1', remarks: 'Good work' },
      { id: 102, studentId: 8, studentUsername: 'student2', studentName: 'Brian Student', courseId: 2, courseName: 'Computer Science', gradeValue: 'C', gpaScore: 2.0, status: 'PENDING', submittedBy: 'teacher1', remarks: 'Needs review' },
    ];
    let rejectionReason;
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/admin/grades' && route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(grades) });
      } else if (url.pathname === '/api/admin/grades/101/approve' && route.request().method() === 'POST') {
        grades = grades.map(grade => grade.id === 101 ? { ...grade, status: 'APPROVED', approvedBy: 'admin' } : grade);
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(grades[0]) });
      } else if (url.pathname === '/api/admin/grades/102/reject' && route.request().method() === 'POST') {
        rejectionReason = route.request().postDataJSON().reason;
        grades = grades.map(grade => grade.id === 102 ? { ...grade, status: 'REJECTED', rejectedBy: 'admin', rejectionReason } : grade);
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(grades[1]) });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
    });

    await page.goto('/admin-dashboard.html');
    await page.evaluate(() => switchModule('grades'));
    const gradeRows = page.locator('#gradesModule table tbody tr');
    await expect(gradeRows).toHaveCount(2);

    const approvalRow = gradeRows.filter({ hasText: 'Alice Student' });
    await approvalRow.getByRole('button', { name: 'Approve' }).click();
    await expect(approvalRow).toContainText('APPROVED');

    const rejectionRow = gradeRows.filter({ hasText: 'Brian Student' });
    page.once('dialog', dialog => dialog.accept('Missing assessment evidence'));
    await rejectionRow.getByRole('button', { name: 'Reject' }).click();
    await expect(rejectionRow).toContainText('REJECTED');
    await expect(rejectionRow).toContainText('Missing assessment evidence');
    expect(rejectionReason).toBe('Missing assessment evidence');
  });

  test('teacher sees leave workflow but not admin-only HR actions', async ({ page }) => {
    await login(page, 'teacher1', 'teacher123');
    await page.goto('/operations.html');
    await expect(page.locator('body')).toContainText('Request leave');
    await expect(page.locator('body')).not.toContainText('Create staff member');
    await expect(page.locator('body')).not.toContainText('Create reconciliation entry');
  });

  test('academics renders transcript and degree audit from the student profile ID', async ({ page }) => {
    await login(page, 'student1', 'student123');
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      const responses = {
        '/api/student/courses': [],
        '/api/student/grades': [],
        '/api/student/profile': { id: 44, fullName: 'Test Student', cgpa: 3.5 },
        '/api/portal/student/assignments': [],
        '/api/portal/student/exams': [],
        '/api/academic/student/timetable': [],
        '/api/academic/student/exam-results': [],
        '/api/student/exam-permits': [],
        '/api/student/attendance': [],
        '/api/academic-records/44/transcript': {
          studentId: 44,
          studentName: 'Test Student',
          cgpa: 3.5,
          courseCount: 1,
          grades: [{ courseCode: 'CS101', courseName: 'Intro to Computing', credits: 3, grade: 'A', gpaScore: 4, approvedAt: '2026-09-01' }],
        },
        '/api/academic-records/44/degree-audit': {
          programName: 'BSc Computing',
          completedRequiredCourses: 1,
          requiredCourses: 2,
          eligibleForGraduation: false,
          missingRequiredCourses: [{ courseCode: 'CS102', courseName: 'Data Structures', semesterNumber: 2 }],
        },
      };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(responses[url.pathname] ?? []) });
    });
    await page.goto('/academics.html');
    await page.getByRole('button', { name: /Academic Records/ }).click();
    await expect(page.locator('#transcriptSummary')).toContainText('Test Student');
    await expect(page.locator('#transcriptTable tbody')).toContainText('CS101');
    await expect(page.locator('#degreeAuditSummary')).toContainText('1/2 required courses complete');
    await expect(page.locator('#missingCoursesTable tbody')).toContainText('CS102');
    await expect(page.locator('#downloadTranscript')).toBeEnabled();
  });
});

test('static page links resolve successfully', async ({ request }) => {
  const pages = ['index.html', 'admin-login.html', 'privacy.html', 'support.html', 'terms.html', 'reset-password.html', 'verify-email.html', 'unauthorized.html'];
  for (const path of pages) {
    const response = await request.get(`/${path}`);
    expect(response.status(), path).toBe(200);
  }
});

test('password reset link secret is removed from history and posted in request bodies', async ({ page }) => {
  let resetRequest;
  await page.route('**/api/auth/validate-reset-token', async route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({ token: 'reset-secret-test' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: 'true' });
  });
  await page.route('**/api/auth/reset-password', async route => {
    resetRequest = route.request();
    await route.fulfill({ status: 200, body: 'Password reset successful.' });
  });

  await page.goto('/reset-password.html?token=reset-secret-test');
  await expect(page).toHaveURL(/\/reset-password\.html$/);
  await page.locator('#newPassword').fill('LongEnoughPass123');
  await page.locator('#confirmPassword').fill('LongEnoughPass123');
  await page.getByRole('button', { name: /RESET PASSWORD/ }).click();
  await expect.poll(() => resetRequest && resetRequest.method()).toBe('POST');
  expect(resetRequest.postDataJSON()).toEqual({
    token: 'reset-secret-test',
    newPassword: 'LongEnoughPass123',
  });
});

test('profile preferences load, save independently, and preserve other settings', async ({ page }) => {
  await login(page, 'student1', 'student123');
  let storedPreferences = {
    userId: 7,
    language: 'English',
    theme: 'dark',
    settingsJson: JSON.stringify({
      privacy: [false, true, false, false],
      notifications: [false, true, true, false],
      quickLinks: [false, true, false, true],
    }),
  };

  await page.on('dialog', dialog => dialog.accept());
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/student/profile') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        id: 7, username: 'student1', fullName: 'Test Student', updatedAt: '2026-10-01',
      }) });
    } else if (url.pathname === '/api/portal/preferences' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(storedPreferences) });
    } else if (url.pathname === '/api/portal/preferences' && request.method() === 'PUT') {
      storedPreferences = { ...storedPreferences, ...request.postDataJSON() };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(storedPreferences) });
    } else if (url.pathname === '/api/auth/mfa/status') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: 'false' });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/profile.html');
  const privacy = page.locator('#securityTab input[type="checkbox"]');
  const notifications = page.locator('#notificationPreferences input[type="checkbox"]');
  const quickLinks = page.locator('#quickLinkPreferences input[type="checkbox"]');
  await expect(privacy.nth(0)).not.toBeChecked();
  await expect(privacy.nth(1)).toBeChecked();
  await expect(notifications.nth(0)).not.toBeChecked();
  await expect(notifications.nth(2)).toBeChecked();
  await expect(quickLinks.nth(0)).not.toBeChecked();
  await expect(quickLinks.nth(3)).toBeChecked();
  await expect(page.locator('#preferencesTab input[name="theme"][value="dark"]')).toBeChecked();

  await page.getByRole('button', { name: /Security/ }).click();
  await privacy.nth(0).check();
  await page.getByRole('button', { name: /Save Settings/ }).click();
  await expect.poll(() => JSON.parse(storedPreferences.settingsJson).privacy[0]).toBe(true);
  expect(JSON.parse(storedPreferences.settingsJson).notifications).toEqual([false, true, true, false]);
  expect(JSON.parse(storedPreferences.settingsJson).quickLinks).toEqual([false, true, false, true]);

  await page.getByRole('button', { name: /Preferences/ }).click();
  await notifications.nth(0).check();
  await page.locator('#preferencesTab input[name="theme"][value="system"]').check();
  await page.getByRole('button', { name: /Save Preferences/ }).click();
  await expect.poll(() => JSON.parse(storedPreferences.settingsJson).notifications[0]).toBe(true);
  expect(JSON.parse(storedPreferences.settingsJson).privacy[0]).toBe(true);
  expect(storedPreferences.theme).toBe('system');

  await page.reload();
  await expect(privacy.nth(0)).toBeChecked();
  await expect(notifications.nth(0)).toBeChecked();
  await expect(page.locator('#preferencesTab input[name="theme"][value="system"]')).toBeChecked();
});

test('profile does not advertise MFA when the deployment has MFA disabled', async ({ page }) => {
  await login(page, 'student1', 'student123');
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/student/profile') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        id: 7, username: 'student1', fullName: 'Test Student',
      }) });
    } else if (url.pathname === '/api/portal/preferences') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        language: 'English', theme: 'light', settingsJson: '{}',
      }) });
    } else if (url.pathname === '/api/auth/mfa/status') {
      await route.fulfill({ status: 501, body: '' });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.goto('/profile.html');
  await expect(page.locator('#mfaSecuritySection')).toBeHidden();
});

test('dashboard renders selected quick links and supports deep links into modules', async ({ page }) => {
  await login(page, 'student1', 'student123');
  let settingsJson = JSON.stringify({ quickLinks: [false, true, true, true] });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/portal/preferences') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ settingsJson }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.goto('/dashboard.html');
  const links = page.locator('#personalQuickLinksList a');
  await expect(links).toHaveCount(3);
  await expect(links.nth(0)).toHaveAttribute('href', 'academics.html#assignments');
  await expect(links.nth(1)).toHaveAttribute('href', 'campus-support.html#events');
  await expect(links.nth(2)).toHaveAttribute('href', 'messages.html');
  await links.nth(0).click();
  await expect(page).toHaveURL(/academics\.html#assignments$/);
  await expect(page.locator('#assignmentsTab')).toHaveClass(/active/);

  settingsJson = JSON.stringify({ quickLinks: [false, false, false, false] });
  await page.goto('/dashboard.html');
  await expect(page.locator('#personalQuickLinksList')).toContainText('No quick links selected');
  await expect(page.locator('#personalQuickLinksList a')).toHaveAttribute('href', 'profile.html#preferences');
});

test('dashboard notification preferences filter the preview by explicit category', async ({ page }) => {
  await login(page, 'student1', 'student123');
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/portal/preferences') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          settingsJson: JSON.stringify({ notifications: [false, true, false, false] }),
        }),
      });
      return;
    }
    if (url.pathname === '/api/notifications') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 1, title: 'Assignment due', message: 'Assignment A is due soon', category: 'ASSIGNMENT', read: false },
          { id: 2, title: 'Grade posted', message: 'A new grade is available', category: 'GRADE', read: false },
          { id: 3, title: 'System update', message: 'Scheduled maintenance', category: 'SYSTEM', read: false },
          { id: 4, title: 'Payment due', message: 'A payment is due', category: 'PAYMENT', read: false },
        ]),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.goto('/dashboard.html');
  const list = page.locator('.notification-list');
  await expect(list.locator('.notification-item')).toHaveCount(1);
  await expect(list).toContainText('Grade posted');
  await expect(list).not.toContainText('Assignment due');
  await expect(list).not.toContainText('System update');
  await expect(list).not.toContainText('Payment due');
  await expect(page.locator('#notificationBadge')).toHaveText('1');
});

test('admissions explains missing programme/year setup and blocks submission', async ({ page }) => {
  await page.route('**/api/public/admissions/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(pathname.endsWith('/programs') ? [] : {}),
    });
  });

  await page.goto('/admissions.html');
  await expect(page.locator('#admissionStatus')).toContainText('configure active programmes and an active academic year');
  await expect(page.locator('#programId')).toBeDisabled();
  await expect(page.locator('#academicYearId')).toBeDisabled();
  await expect(page.locator('#submitAdmissionButton')).toBeDisabled();
});

test('public applicant receives the administrator approval and enrollment steps', async ({ page }) => {
  let submittedApplication;
  await page.route('**/api/public/admissions/**', async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname.endsWith('/programs')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 4, code: 'BSC-CS', name: 'Bachelor of Computer Science' }]),
      });
    } else if (pathname.endsWith('/academic-year')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 7, name: '2027 Academic Year' }),
      });
    } else if (pathname.endsWith('/applications') && request.method() === 'POST') {
      submittedApplication = request.postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ applicationNumber: 'APP-2027-0001', status: 'SUBMITTED' }),
      });
    } else {
      await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
    }
  });

  await page.goto('/admissions.html');
  await expect(page.locator('body')).toContainText('This does not create a portal login');
  await page.locator('#applicantName').fill('Test Applicant');
  await page.locator('#email').fill('applicant@example.test');
  await page.locator('#phone').fill('+256700000001');
  await page.locator('#programId').selectOption('4');
  await page.locator('#academicYearId').selectOption('7');
  await page.getByRole('button', { name: 'Submit application' }).click();

  await expect(page.locator('#admissionStatus')).toContainText('APP-2027-0001');
  await expect(page.locator('#admissionStatus')).toContainText('after acceptance and enrollment');
  expect(submittedApplication).toEqual({
    applicantName: 'Test Applicant',
    email: 'applicant@example.test',
    phone: '+256700000001',
    programId: 4,
    academicYearId: 7,
  });
});

test('public admissions status lookup requires the application email without exposing applicant name', async ({ page }) => {
  let statusRequest;
  await page.route('**/api/public/admissions/**', async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname.endsWith('/programs')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    } else if (pathname.endsWith('/academic-year')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    } else if (pathname.endsWith('/applications/status') && request.method() === 'POST') {
      statusRequest = request.postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          applicationNumber: 'APP-123456',
          program: 'Bachelor of Science',
          academicYear: '2027 Academic Year',
          status: 'UNDER_REVIEW',
        }),
      });
    } else {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Application number or email address is incorrect' }) });
    }
  });

  await page.goto('/admissions.html');
  await page.locator('#applicationNumber').fill('APP-123456');
  await page.locator('#statusEmail').fill('amina@example.test');
  await page.getByRole('button', { name: 'Check status' }).click();
  await expect(page.locator('#statusResult')).toContainText('Status: Under Review');
  await expect(page.locator('#statusResult')).toContainText('Your application is being reviewed.');
  await expect(page.locator('#statusResult')).toContainText('Programme: Bachelor of Science');
  await expect(page.locator('#statusResult')).not.toContainText('applicantName');
  expect(statusRequest).toEqual({
    applicationNumber: 'APP-123456',
    email: 'amina@example.test',
  });
});

test('admin report approval uses an in-page form instead of a browser prompt', async ({ page }) => {
  await login(page, 'admin', 'admin123', true);
  let approvedStudentId;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/reports/academic/7/approve' && request.method() === 'POST') {
      approvedStudentId = 7;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ approved: true, approvedBy: 'admin', approvedAt: '2026-10-07T12:00:00' }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.getByRole('link', { name: /Reports/ }).click();
  await page.locator('#reportsModule .report-card').filter({ hasText: 'Approve Student Report' }).click();
  await expect(page.getByRole('heading', { name: 'Approve student report', exact: true })).toBeVisible();
  await page.locator('#approveStudentReportId').fill('7');
  await page.getByRole('button', { name: 'Approve report' }).click();

  await expect(page.locator('#approveStudentReportMessage')).toContainText("Student 7's academic report was approved");
  expect(approvedStudentId).toBe(7);
});

test('admin user management reads the API isActive field accurately', async ({ page }) => {
  await login(page, 'admin', 'admin123', true);
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === '/api/admin/users') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 1, username: 'admin', role: 'ADMIN', isActive: true },
          { id: 2, username: 'active-user', role: 'STUDENT', isActive: true },
          { id: 3, username: 'inactive-user', role: 'STUDENT', isActive: false },
        ]),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.locator('.sidebar-nav .nav-item').nth(5).click();
  const rows = page.locator('#usersModule tbody tr');
  const currentUser = rows.filter({ has: page.getByText('admin', { exact: true }) });
  const activeUser = rows.filter({ has: page.getByText('active-user', { exact: true }) });
  const inactiveUser = rows.filter({ has: page.getByText('inactive-user', { exact: true }) });
  await expect(currentUser).toContainText('Current account');
  await expect(currentUser).not.toContainText('Deactivate');
  await expect(activeUser).toContainText('Active');
  await expect(activeUser).toContainText('Deactivate');
  await expect(activeUser).not.toContainText('Restore');
  await expect(inactiveUser).toContainText('Deactivated');
  await expect(inactiveUser).toContainText('Restore');
});

test('admin can configure institution directory and activate an academic year', async ({ page }) => {
  await login(page, 'admin', 'admin123', true);
  const directory = { campuses: [], faculties: [], departments: [], programs: [], years: [] };
  let nextId = 1;
  await page.route('**/api/admin/institution/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname.replace('/api/admin/institution', '');
    const method = request.method();
    if (method === 'GET' && pathname === '/campuses') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(directory.campuses) });
    } else if (method === 'POST' && pathname === '/campuses') {
      const campus = { id: nextId++, ...request.postDataJSON() };
      directory.campuses.push(campus);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(campus) });
    } else if (method === 'GET' && pathname === '/faculties') {
      const faculties = directory.faculties.filter(item => item.campusId === Number(url.searchParams.get('campusId')));
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(faculties) });
    } else if (method === 'POST' && pathname === '/faculties') {
      const faculty = { id: nextId++, ...request.postDataJSON() };
      directory.faculties.push(faculty);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(faculty) });
    } else if (method === 'GET' && pathname === '/departments') {
      const departments = directory.departments.filter(item => item.facultyId === Number(url.searchParams.get('facultyId')));
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(departments) });
    } else if (method === 'POST' && pathname === '/departments') {
      const department = { id: nextId++, ...request.postDataJSON() };
      directory.departments.push(department);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(department) });
    } else if (method === 'GET' && pathname === '/programs') {
      const programs = directory.programs.filter(item => item.departmentId === Number(url.searchParams.get('departmentId')));
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(programs) });
    } else if (method === 'POST' && pathname === '/programs') {
      const program = { id: nextId++, ...request.postDataJSON() };
      directory.programs.push(program);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(program) });
    } else if (method === 'GET' && pathname === '/academic-years') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(directory.years) });
    } else if (method === 'POST' && pathname === '/academic-years') {
      const year = { id: nextId++, ...request.postDataJSON() };
      if (year.activate) directory.years.forEach(existing => { existing.active = false; });
      year.active = year.activate;
      directory.years.push(year);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(year) });
    } else if (method === 'PATCH' && /^\/academic-years\/\d+\/activate$/.test(pathname)) {
      const yearId = Number(pathname.split('/')[2]);
      directory.years.forEach(year => { year.active = year.id === yearId; });
      const year = directory.years.find(item => item.id === yearId);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(year) });
    } else {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: `Unexpected setup request: ${method} ${pathname}` }) });
    }
  });

  await page.getByRole('link', { name: /Academic Operations/ }).click();
  await expect(page.locator('#academicYearList')).toContainText('No academic years configured yet.');
  await page.locator('#campusCode').fill('NORTH');
  await page.locator('#campusName').fill('North Campus');
  await page.getByRole('button', { name: 'Add Campus' }).click();
  await expect(page.locator('#setupCampusSelect')).toHaveValue('1');

  await page.locator('#facultyCode').fill('SCI');
  await page.locator('#facultyName').fill('Faculty of Science');
  await page.getByRole('button', { name: 'Add Faculty' }).click();
  await expect(page.locator('#setupFacultySelect')).toHaveValue('2');

  await page.locator('#departmentCode').fill('CS');
  await page.locator('#departmentName').fill('Computer Science');
  await page.getByRole('button', { name: 'Add Department' }).click();
  await expect(page.locator('#setupDepartmentSelect')).toHaveValue('3');

  await page.locator('#programCode').fill('BSC-CS');
  await page.locator('#programName').fill('Bachelor of Computer Science');
  await page.locator('#programAwardTitle').fill('Bachelor of Science');
  await page.locator('#programDurationYears').fill('3');
  await page.getByRole('button', { name: 'Add Programme' }).click();
  await expect(page.locator('#institutionProgramList')).toContainText('Bachelor of Computer Science');

  await page.locator('#academicYearCode').fill('AY-2027');
  await page.locator('#academicYearName').fill('2027 Academic Year');
  await page.locator('#academicYearStart').fill('2027-01-01');
  await page.locator('#academicYearEnd').fill('2027-12-31');
  await page.getByRole('button', { name: 'Add Academic Year' }).click();
  await expect(page.locator('#academicYearList')).toContainText('Active');
  await expect.poll(() => directory.programs).toHaveLength(1);
  expect(directory.programs[0]).toMatchObject({
    code: 'BSC-CS',
    name: 'Bachelor of Computer Science',
    awardTitle: 'Bachelor of Science',
    departmentId: 3,
    durationYears: 3,
  });
  expect(directory.years[0]).toMatchObject({ code: 'AY-2027', activate: true, active: true });
});

test('institution setup API is restricted to administrator accounts', async ({ page }) => {
  await login(page, 'admin', 'admin123', true);
  const adminResponse = await page.request.get('/api/admin/institution/campuses');
  expect(adminResponse.status()).toBe(200);
  const admissionsResponse = await page.request.get('/api/admissions/applications');
  expect(admissionsResponse.status()).toBe(200);
  const statusResponse = await page.request.get('/api/admin/system/status');
  expect(statusResponse.status()).toBe(200);
  const status = await statusResponse.json();
  expect(status.database.status).toBe('online');
  expect(['disabled', 'incomplete', 'configured', 'online']).toContain(status.email.status);
  expect(['not_configured', 'online', 'stale', 'unavailable']).toContain(status.backup.status);
  await expect(page.locator('#databaseHealthStatus')).toContainText('Connected');

  await login(page, 'student1', 'student123');
  const studentResponse = await page.request.get('/api/admin/institution/campuses');
  expect(studentResponse.status()).toBe(403);
  const studentStatusResponse = await page.request.get('/api/admin/system/status');
  expect(studentStatusResponse.status()).toBe(403);
  const studentAdmissionsResponse = await page.request.get('/api/admissions/applications');
  expect(studentAdmissionsResponse.status()).toBe(403);
});

test('admin dashboard pending actions link to decision queues and support admission decisions', async ({ page }) => {
  await login(page, 'admin', 'admin123', true);
  let applications = [
    {
      id: 41, applicationNumber: 'APP-41', applicantName: 'Amina Applicant',
      email: 'amina@example.com', phone: '0700000041', programName: 'Bachelor of Science',
      academicYearId: 2, academicYearName: '2027 Academic Year', status: 'SUBMITTED',
      reviewerNotes: '', submittedAt: '2026-10-01T10:00:00', enrolled: false,
    },
    {
      id: 42, applicationNumber: 'APP-42', applicantName: 'Ben Accepted',
      email: 'ben@example.com', phone: '', programName: 'Bachelor of Arts',
      academicYearId: 2, academicYearName: '2027 Academic Year', status: 'ACCEPTED',
      reviewerNotes: 'Already accepted', submittedAt: '2026-09-30T10:00:00',
    },
  ];
  let submittedDecision;
  let submittedEnrollment;
  let createdStudent;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/admin/grades/pending') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 51, status: 'PENDING', studentName: 'Test Student', courseName: 'Biology' },
      ]) });
    } else if (url.pathname === '/api/admissions/applications' && request.method() === 'GET') {
      const selected = url.searchParams.get('status');
      const filtered = selected ? applications.filter(application => application.status === selected) : applications;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(filtered) });
    } else if (url.pathname === '/api/admissions/applications/41/decision' && request.method() === 'PATCH') {
      submittedDecision = request.postDataJSON();
      applications = applications.map(application => application.id === 41
        ? { ...application, status: submittedDecision.status, reviewerNotes: submittedDecision.reviewerNotes }
        : application);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(applications[0]) });
    } else if (url.pathname === '/api/admissions/applications/41/enrollment-candidates' && request.method() === 'GET') {
      const candidates = createdStudent ? [{
        id: createdStudent.id,
        fullName: createdStudent.fullName,
        username: createdStudent.username,
        email: createdStudent.email,
        registrationNumber: 'MIT/2027/00041',
        enrolled: false,
      }] : [];
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(candidates) });
    } else if (url.pathname === '/api/admissions/applications/41/enroll-new' && request.method() === 'POST') {
      submittedEnrollment = request.postDataJSON();
      createdStudent = { id: 81, ...submittedEnrollment };
      applications = applications.map(application => application.id === 41
        ? { ...application, enrolled: true }
        : application);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        id: 91, studentId: 81, programId: 1, academicYearId: 2, status: 'ACTIVE',
        username: submittedEnrollment.username, temporaryPassword: 'GeneratedTempPass1234567890',
        emailSent: false,
        emailDeliveryMessage: 'Email is not configured; share the temporary password privately.',
      }) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
  });

  await page.reload();
  await expect(page.locator('#pendingTaskCount')).toHaveText('2');
  await expect(page.locator('#pendingActionsList')).toContainText('1 grade approval');
  await expect(page.locator('#pendingActionsList')).toContainText('1 admission application');
  await page.getByRole('button', { name: /1 admission application/ }).click();
  await expect(page.locator('#admissionsTable tbody')).toContainText('Amina Applicant');
  await expect(page.locator('#admissionsNavCount')).toHaveText('1');
  await expect(page.locator('#admissionsTable tbody')).toContainText('2027 Academic Year');

  await page.getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByRole('heading', { name: 'Accept application' })).toBeVisible();
  await page.locator('#adminInputValue').fill('Verified eligibility and documents.');
  await page.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.locator('#admissionQueueStatus')).toContainText('Application accepted.');
  await expect(page.locator('#admissionsTable tbody')).toContainText('ACCEPTED');
  await expect(page.locator('#pendingTaskCount')).toHaveText('1');
  expect(submittedDecision).toEqual({
    status: 'ACCEPTED',
    reviewerNotes: 'Verified eligibility and documents.',
  });

  await page.getByRole('row', { name: /APP-41 Amina Applicant/ }).getByRole('button', { name: 'Enroll student' }).click();
  await expect(page.getByRole('heading', { name: 'Admit New Student' })).toBeVisible();
  await expect(page.locator('#admitStudentForm input[name="email"]')).toHaveValue('amina@example.com');
  await expect(page.locator('#admitStudentForm input[name="fullName"]')).toHaveValue('Amina Applicant');
  await page.locator('#admitStudentForm input[name="username"]').fill('amina-applicant');
  await expect(page.locator('#admitStudentForm input[name="password"]')).toHaveCount(0);
  await expect(page.locator('#admitStudentForm')).toContainText('secure temporary password will be generated');
  await page.getByRole('button', { name: 'Create account and enroll' }).click();
  await expect(page.getByRole('heading', { name: 'Student account ready' })).toBeVisible();
  await expect(page.locator('#admissionUsername')).toHaveValue('amina-applicant');
  await expect(page.locator('#admissionTemporaryPassword')).toHaveValue('GeneratedTempPass1234567890');
  await expect(page.locator('#modalBox')).toContainText('share the temporary password privately');
  await expect(page.locator('#admissionLoginUrl')).toHaveValue(/\/index\.html$/);
  await expect(page.locator('#admissionQueueStatus')).toContainText('Student account created and applicant enrolled successfully.');
  expect(createdStudent).toMatchObject({
    fullName: 'Amina Applicant',
    email: 'amina@example.com',
    phone: '0700000041',
    username: 'amina-applicant',
  });
  expect(submittedEnrollment).toEqual({
    fullName: 'Amina Applicant',
    email: 'amina@example.com',
    phone: '0700000041',
    username: 'amina-applicant',
  });
  expect(submittedEnrollment).not.toHaveProperty('password');
  await expect(page.getByRole('row', { name: /APP-41 Amina Applicant/ })).toContainText('Enrolled');
  await expect(page.getByRole('row', { name: /APP-41 Amina Applicant/ }).getByRole('button', { name: 'Enroll student' })).toHaveCount(0);
});
