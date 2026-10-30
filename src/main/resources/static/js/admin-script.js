// API, apiGet(), apiFetch(), requireRole(), logout(), escapeHtml() all come from
// auth.js, loaded before this file.

let currentTeachers = []; // cached for the "assign teacher" dropdown on Create Course
let pendingAdminInputResolver = null;

// ===== INITIALIZATION =====
document.addEventListener('DOMContentLoaded', function() {
  if (!requireRole(['ADMIN'], 'admin-login.html')) return;
  document.getElementById('adminRole').textContent = 'Administrator';
  setupNavigation();
  setupAdminFilters();
  setupHeaderDropdowns();
  document.getElementById('hrStaffForm')?.addEventListener('submit', createHrStaffRecord);
  document.getElementById('adminLeaveForm')?.addEventListener('submit', submitAdminLeaveRequest);
  loadDashboard();
  loadAdminAttendance();
  loadAdminNotifications();
});

function setupHeaderDropdowns() {
  document.getElementById('notificationBtn')?.addEventListener('click', function() {
    toggleNotifications();
  });

  document.getElementById('themeBtn')?.addEventListener('click', function() {
    toggleDropdown('theme');
  });

  document.getElementById('profileBtn')?.addEventListener('click', function() {
    toggleProfile();
  });

  document.addEventListener('click', function(event) {
    if (!event.target.closest('.dropdown')) {
      document.querySelectorAll('.dropdown.active').forEach(dropdown => {
        dropdown.classList.remove('active');
      });
    }

    async function saveProgramAwardType(programId) {
      const awardType = document.getElementById(`program-award-type-${programId}`)?.value;
      if (!awardType) {
        setAdminStatus('programStatus', 'Select Bachelor or Diploma before saving the award type.', 'error');
        return;
      }
      try {
        const response = await apiFetch(`/admin/institution/programs/${programId}/award-type`, {
          method: 'PUT',
          body: JSON.stringify({ awardType }),
        });
        const program = await response.json();
        setAdminStatus('programStatus', `Award type saved for ${program.name}.`, 'success');
      } catch (err) {
        setAdminStatus('programStatus', err.message || 'Unable to save award type.', 'error');
      }
    }
  });
}

function toggleDropdown(type) {
  const button = document.getElementById(`${type}Btn`);
  const dropdown = button?.closest('.dropdown');
  if (!dropdown) return;

  dropdown.classList.toggle('active');
  document.querySelectorAll('.dropdown.active').forEach(other => {
    if (other !== dropdown) other.classList.remove('active');
  });
}

async function loadAdminNotifications() {
  try {
    const notifications = await apiGet('/notifications');
    const list = document.getElementById('adminNotificationList');
    if (list) list.innerHTML = notifications.length ? notifications.slice(0, 10).map(item => `<button type="button" class="admin-notification-item ${item.read ? '' : 'unread'}" onclick="markAdminNotificationRead(${item.id}, this)"><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(item.message)}</span></button>`).join('') : '<p>No notifications yet.</p>';
    const badge = document.getElementById('adminNotificationBadge');
    if (badge) {
      const unread = notifications.filter(item => !item.read).length;
      badge.textContent = unread;
      badge.hidden = unread === 0;
    }
  } catch (err) { console.error('Failed to load notifications', err); }
}

function toggleNotifications() {
  toggleDropdown('notification');
}

async function markAdminNotificationRead(id, element) {
  await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
  element.classList.remove('unread');
  loadAdminNotifications();
}

async function markAllAdminNotificationsRead() {
  await apiFetch('/notifications/read-all', { method: 'PATCH' });
  loadAdminNotifications();
}

// ===== LOGOUT =====
function adminLogout() {
  if (confirm('Are you sure you want to logout?')) {
    logout('admin-login.html');
  }
}

async function loadAdminAttendance() {
  const tbody = document.querySelector('#adminAttendanceTable tbody');
  if (!tbody) return;
  try {
    const records = await apiGet('/admin/attendance');
    const sortedRecords = records.slice().sort((left, right) =>
      String(right.date || '').localeCompare(String(left.date || ''))
      || Number(right.id || 0) - Number(left.id || 0));
    tbody.innerHTML = sortedRecords.length ? sortedRecords.map(record => {
      const startTime = record.classStartTime ? String(record.classStartTime).slice(0, 5) : null;
      const endTime = record.classEndTime ? String(record.classEndTime).slice(0, 5) : null;
      const time = startTime && endTime ? `${startTime} - ${endTime}` : startTime || endTime || '—';
      const status = record.late ? '⏱ Late' : record.present ? '✓ Present' : '✕ Absent';
      return `<tr><td>${escapeHtml(record.studentName || record.studentId)}</td><td>${escapeHtml(record.courseName)}</td><td>${escapeHtml(record.date)}</td><td>${escapeHtml(time)}</td><td><span class="badge ${record.present || record.late ? 'success' : 'danger'}">${status}</span></td><td>${escapeHtml(record.remarks || '—')}</td></tr>`;
    }).join('') : '<tr><td colspan="6">No approved attendance records yet.</td></tr>';
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6">${escapeHtml(err.message || 'Unable to load attendance records.')}</td></tr>`;
  }
}

function setupAdminFilters() {
  document.querySelectorAll('[data-admin-filter]').forEach(control => {
    if (control.dataset.adminFilter === 'student-search') {
      control.addEventListener('input', () => {
        clearTimeout(window._studentSearchTimer);
        window._studentSearchTimer = setTimeout(() => loadStudents(0), 250);
      });
    } else if (control.dataset.adminFilter === 'student-status') {
      control.addEventListener('change', () => loadStudents(0));
    } else {
      control.addEventListener(control.tagName === 'SELECT' ? 'change' : 'input', applyAdminFilters);
    }
  });
}

function applyAdminFilters() {
  const courseSearch = document.querySelector('[data-admin-filter="course-search"]')?.value.toLowerCase() || '';
  const category = document.querySelector('[data-admin-filter="course-category"]')?.value.toLowerCase() || '';
  document.querySelectorAll('#coursesModule .course-card').forEach(card => {
    const text = card.textContent.toLowerCase();
    card.hidden = Boolean(courseSearch && !text.includes(courseSearch))
      || Boolean(category && card.dataset.category !== category);
  });

  const gradeCourse = document.querySelector('[data-admin-filter="grade-course"]')?.value || '';
  const gradeSemester = document.querySelector('[data-admin-filter="grade-semester"]')?.value || '';
  document.querySelectorAll('#gradesModule tbody tr').forEach(row => {
    const text = row.textContent.toLowerCase();
    row.hidden = Boolean(gradeCourse && !gradeCourse.startsWith('Select') && !text.includes(gradeCourse.toLowerCase().split(' - ')[0]))
      || Boolean(gradeSemester && !gradeSemester.startsWith('All') && !text.includes(gradeSemester.toLowerCase()));
  });

  const financeStatus = document.querySelector('[data-admin-filter="finance-status"]')?.value || '';
  document.querySelectorAll('#financeModule tbody tr').forEach(row => {
    row.hidden = Boolean(financeStatus && !financeStatus.startsWith('All') && !row.textContent.toLowerCase().includes(financeStatus.toLowerCase()));
  });
}

// ===== NAVIGATION =====
function setupNavigation() {
  document.querySelectorAll('.nav-item').forEach(item => {
    if (item.getAttribute('href') && item.getAttribute('href') !== '#') return;
    item.addEventListener('click', function(e) {
      e.preventDefault();
    });
  });
}

function switchModule(moduleName, clickedElement = null) {
  const moduleEl = document.getElementById(moduleName + 'Module');
  if (!moduleEl) {
    console.error(`Admin dashboard module "${moduleName}" was not found.`);
    return;
  }

  document.querySelectorAll('.module').forEach(mod => mod.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
  moduleEl.classList.add('active');
  const activeNavigationItem = clickedElement?.closest('.nav-item')
    || Array.from(document.querySelectorAll('.sidebar-nav .nav-item[data-module]'))
      .find(item => item.dataset.module === moduleName);
  activeNavigationItem?.classList.add('active');

  if (moduleName === 'students') loadStudents();
  else if (moduleName === 'courses') loadCourses();
  else if (moduleName === 'course-categories') loadCourseCategories();
  else if (moduleName === 'staff') { loadStaff(); loadHrManagement(); }
  else if (moduleName === 'requests') loadRequests();
  else if (moduleName === 'admissions') loadAdmissionApplications();
  else if (moduleName === 'dashboard') loadDashboard();
  else if (moduleName === 'finance') loadAdminFinance();
  else if (moduleName === 'grades') loadAdminGrades();
  else if (moduleName === 'academic') setupAcademicForms();
  else if (moduleName === 'users') loadUsers();
}

let academicFormsReady = false;

function setupAcademicForms() {
  if (academicFormsReady) {
    loadInstitutionSetup();
    loadAcademicOperationOptions();
    return;
  }
  academicFormsReady = true;
  document.getElementById('campusForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await apiFetch('/admin/institution/campuses', { method: 'POST', body: JSON.stringify({
        code: document.getElementById('campusCode').value.trim(),
        name: document.getElementById('campusName').value.trim(),
      }) });
      const campus = await response.json();
      setAdminStatus('campusStatus', 'Campus added.', 'success');
      event.target.reset();
      await loadSetupCampuses(campus.id);
    } catch (err) { setAdminStatus('campusStatus', err.message, 'error'); }
  });
  document.getElementById('setupCampusSelect')?.addEventListener('change', event => {
    updateInstitutionSubmitButtons();
    loadSetupFaculties(event.target.value).catch(err => setAdminStatus('facultyStatus', err.message, 'error'));
  });
  document.getElementById('facultyForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await apiFetch('/admin/institution/faculties', { method: 'POST', body: JSON.stringify({
        campusId: Number(document.getElementById('setupCampusSelect').value),
        code: document.getElementById('facultyCode').value.trim(),
        name: document.getElementById('facultyName').value.trim(),
      }) });
      const faculty = await response.json();
      setAdminStatus('facultyStatus', 'Faculty added.', 'success');
      event.target.reset();
      document.getElementById('setupCampusSelect').value = String(faculty.campusId);
      updateInstitutionSubmitButtons();
      await loadSetupFaculties(faculty.campusId, faculty.id);
    } catch (err) { setAdminStatus('facultyStatus', err.message, 'error'); }
  });
  document.getElementById('setupFacultySelect')?.addEventListener('change', event => {
    updateInstitutionSubmitButtons();
    loadSetupDepartments(event.target.value).catch(err => setAdminStatus('departmentStatus', err.message, 'error'));
  });
  document.getElementById('departmentForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await apiFetch('/admin/institution/departments', { method: 'POST', body: JSON.stringify({
        facultyId: Number(document.getElementById('setupFacultySelect').value),
        code: document.getElementById('departmentCode').value.trim(),
        name: document.getElementById('departmentName').value.trim(),
      }) });
      const department = await response.json();
      setAdminStatus('departmentStatus', 'Department added.', 'success');
      event.target.reset();
      document.getElementById('setupFacultySelect').value = String(department.facultyId);
      updateInstitutionSubmitButtons();
      await loadSetupDepartments(department.facultyId, department.id);
    } catch (err) { setAdminStatus('departmentStatus', err.message, 'error'); }
  });
  document.getElementById('setupDepartmentSelect')?.addEventListener('change', event => {
    updateInstitutionSubmitButtons();
    loadSetupPrograms(event.target.value).catch(err => setAdminStatus('programStatus', err.message, 'error'));
  });
  document.getElementById('programForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await apiFetch('/admin/institution/programs', { method: 'POST', body: JSON.stringify({
        departmentId: Number(document.getElementById('setupDepartmentSelect').value),
        code: document.getElementById('programCode').value.trim(),
        name: document.getElementById('programName').value.trim(),
        awardTitle: document.getElementById('programAwardTitle').value.trim(),
        awardType: document.getElementById('programAwardType').value,
        durationYears: Number(document.getElementById('programDurationYears').value),
      }) });
      const program = await response.json();
      setAdminStatus('programStatus', 'Programme added and made available to admissions.', 'success');
      event.target.reset();
      document.getElementById('setupDepartmentSelect').value = String(program.departmentId);
      updateInstitutionSubmitButtons();
      await loadSetupPrograms(program.departmentId);
    } catch (err) { setAdminStatus('programStatus', err.message, 'error'); }
  });
  document.getElementById('academicYearForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const response = await apiFetch('/admin/institution/academic-years', { method: 'POST', body: JSON.stringify({
        code: document.getElementById('academicYearCode').value.trim(),
        name: document.getElementById('academicYearName').value.trim(),
        startDate: document.getElementById('academicYearStart').value,
        endDate: document.getElementById('academicYearEnd').value,
        activate: document.getElementById('activateAcademicYear').checked,
      }) });
      const year = await response.json();
      setAdminStatus('academicYearStatus', year.active ? 'Academic year created and activated.' : 'Academic year created.', 'success');
      event.target.reset();
      await loadAdminAcademicYears();
    } catch (err) { setAdminStatus('academicYearStatus', err.message, 'error'); }
  });
  document.getElementById('academicTermYearId')?.addEventListener('change', event => {
    loadAcademicTerms(event.target.value).catch(err => setAdminStatus('academicTermStatus', err.message, 'error'));
  });
  document.getElementById('academicTermForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const yearId = document.getElementById('academicTermYearId').value;
    try {
      const response = await apiFetch('/admin/institution/academic-terms', { method: 'POST', body: JSON.stringify({
        code: document.getElementById('academicTermCode').value.trim(),
        name: document.getElementById('academicTermName').value.trim(),
        academicYearId: Number(yearId),
        startDate: document.getElementById('academicTermStart').value,
        endDate: document.getElementById('academicTermEnd').value,
        registrationOpen: document.getElementById('academicTermRegistrationOpen').checked,
      }) });
      const term = await response.json();
      setAdminStatus('academicTermStatus', 'Academic term created.', 'success');
      event.target.reset();
      document.getElementById('academicTermYearId').value = String(term.academicYearId);
      await loadAcademicTerms(yearId, term.id);
    } catch (err) { setAdminStatus('academicTermStatus', err.message, 'error'); }
  });
  document.getElementById('sectionAcademicTermId')?.addEventListener('change', event => {
    updateCourseSectionButton();
    loadCourseSections(event.target.value).catch(err => setAdminStatus('courseSectionStatus', err.message, 'error'));
  });
  document.getElementById('sectionCourseId')?.addEventListener('change', updateCourseSectionButton);
  document.getElementById('courseSectionForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const termId = document.getElementById('sectionAcademicTermId').value;
    try {
      await apiFetch('/registration/sections', { method: 'POST', body: JSON.stringify({
        courseId: Number(document.getElementById('sectionCourseId').value),
        academicTermId: Number(termId),
        sectionCode: document.getElementById('sectionCode').value.trim(),
        capacity: Number(document.getElementById('sectionCapacity').value),
      }) });
      setAdminStatus('courseSectionStatus', 'Course section created.', 'success');
      const courseId = document.getElementById('sectionCourseId').value;
      event.target.reset();
      document.getElementById('sectionAcademicTermId').value = termId;
      document.getElementById('sectionCourseId').value = courseId;
      document.getElementById('sectionCapacity').value = '50';
      updateCourseSectionButton();
      await loadCourseSections(termId);
    } catch (err) { setAdminStatus('courseSectionStatus', err.message, 'error'); }
  });
  document.getElementById('timetableForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      await apiFetch('/academic/timetable', { method: 'POST', body: JSON.stringify({
        courseId: Number(document.getElementById('timetableCourseId').value),
        academicTermId: Number(document.getElementById('timetableAcademicTermId').value),
        dayOfWeek: document.getElementById('timetableDay').value,
        startsAt: document.getElementById('timetableStartsAt').value,
        endsAt: document.getElementById('timetableEndsAt').value,
        venue: document.getElementById('timetableVenue').value.trim(),
      }) });
      setAdminStatus('timetableStatus', 'Timetable entry published.', 'success');
      event.target.reset();
    } catch (err) { setAdminStatus('timetableStatus', err.message, 'error'); }
  });
  document.getElementById('examForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      await apiFetch('/portal/admin/exams', { method: 'POST', body: JSON.stringify({
        courseId: Number(document.getElementById('examCourseId').value),
        title: document.getElementById('examTitle').value.trim(),
        startsAt: document.getElementById('examStartsAt').value,
        venue: document.getElementById('examVenue').value.trim(),
      }) });
      setAdminStatus('examStatus', 'Examination published.', 'success');
      event.target.reset();
    } catch (err) { setAdminStatus('examStatus', err.message, 'error'); }
  });
  document.getElementById('examResultForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      await apiFetch('/academic/exam-results', { method: 'POST', body: JSON.stringify({
        examId: Number(document.getElementById('resultExamId').value),
        studentId: Number(document.getElementById('resultStudentId').value),
        score: Number(document.getElementById('resultScore').value),
        maxScore: Number(document.getElementById('resultMaxScore').value),
        remarks: document.getElementById('resultRemarks').value.trim(),
      }) });
      setAdminStatus('examResultStatus', 'Exam result saved.', 'success');
      event.target.reset();
      document.getElementById('resultMaxScore').value = '100';
    } catch (err) { setAdminStatus('examResultStatus', err.message, 'error'); }
  });
  loadInstitutionSetup();
  loadAcademicOperationOptions();
}

async function loadAcademicOperationOptions() {
  const statusId = 'academicReferenceStatus';
  try {
    const [courses, students, exams] = await Promise.all([
      fetchAllAdminPages('/admin/courses'),
      fetchAllAdminPages('/admin/students'),
      apiGet('/portal/admin/exams'),
    ]);
    populateAcademicOptions('timetableCourseId', courses, 'No courses configured',
      course => `${course.courseCode || 'Course'} · ${course.name} (#${course.id})`);
    populateAcademicOptions('sectionCourseId', courses, 'No courses configured',
      course => `${course.courseCode || 'Course'} · ${course.name} (#${course.id})`);
    updateCourseSectionButton();
    populateAcademicOptions('examCourseId', courses, 'No courses configured',
      course => `${course.courseCode || 'Course'} · ${course.name} (#${course.id})`);
    populateAcademicOptions('resultStudentId', students, 'No students configured',
      student => `${student.fullName} · ${student.registrationNumber || student.username} (#${student.id})`);
    populateAcademicOptions('resultExamId', exams, 'No examinations configured',
      exam => `${exam.title} · ${String(exam.startsAt || '').replace('T', ' ')} (#${exam.id})`);
    setAdminStatus(statusId, '', 'info');
  } catch (err) {
    setAdminStatus(statusId, err.message || 'Unable to load academic record choices.', 'error');
    ['timetableCourseId', 'sectionCourseId', 'examCourseId', 'resultStudentId', 'resultExamId'].forEach(id => {
      const select = document.getElementById(id);
      if (select) {
        select.replaceChildren(new Option('Unable to load choices; retry Academic Operations', ''));
        select.disabled = true;
      }
    });
  }
}

function populateAcademicOptions(selectId, items, emptyLabel, labelFor) {
  const select = document.getElementById(selectId);
  if (!select) return;
  select.replaceChildren(new Option(items.length ? 'Select a record' : emptyLabel, ''));
  items.forEach(item => select.add(new Option(labelFor(item), item.id)));
  select.disabled = items.length === 0;
}

async function loadInstitutionSetup() {
  await Promise.all([
    loadSetupCampuses().catch(err => setAdminStatus('campusStatus', err.message, 'error')),
    loadAdminAcademicYears(),
  ]);
}

function setupDirectorySelect(id, items, placeholder, selectedId = '') {
  const select = document.getElementById(id);
  if (!select) return;
  select.innerHTML = `<option value="">${escapeHtml(placeholder)}</option>${items.map(item =>
    `<option value="${item.id}">${escapeHtml(item.code)} · ${escapeHtml(item.name)}</option>`).join('')}`;
  select.disabled = items.length === 0;
  select.value = selectedId ? String(selectedId) : '';
  updateInstitutionSubmitButtons();
}

function updateInstitutionSubmitButtons() {
  const dependencies = [
    ['setupCampusSelect', 'createFacultyButton'],
    ['setupFacultySelect', 'createDepartmentButton'],
    ['setupDepartmentSelect', 'createProgramButton'],
  ];
  dependencies.forEach(([selectId, buttonId]) => {
    const select = document.getElementById(selectId);
    const button = document.getElementById(buttonId);
    if (select && button) button.disabled = !select.value;
  });
}

async function loadSetupCampuses(selectedId = '') {
  try {
    const campuses = await apiGet('/admin/institution/campuses');
    setupDirectorySelect('setupCampusSelect', campuses, campuses.length ? 'Select a campus' : 'No campuses configured', selectedId);
    if (!selectedId) {
      setupDirectorySelect('setupFacultySelect', [], 'Select a campus first');
      setupDirectorySelect('setupDepartmentSelect', [], 'Select a faculty first');
      document.getElementById('programStatus').textContent = '';
    } else {
      await loadSetupFaculties(selectedId);
    }
  } catch (err) {
    setAdminStatus('campusStatus', err.message || 'Unable to load campuses.', 'error');
    throw err;
  }
}

async function loadSetupFaculties(campusId, selectedId = '') {
  setupDirectorySelect('setupFacultySelect', [], 'Select a campus first');
  setupDirectorySelect('setupDepartmentSelect', [], 'Select a faculty first');
  if (!campusId) return;
  try {
    const faculties = await apiGet(`/admin/institution/faculties?campusId=${encodeURIComponent(campusId)}`);
    setupDirectorySelect('setupFacultySelect', faculties, faculties.length ? 'Select a faculty' : 'No faculties configured', selectedId);
    if (selectedId) await loadSetupDepartments(selectedId);
  } catch (err) {
    setAdminStatus('facultyStatus', err.message || 'Unable to load faculties.', 'error');
    throw err;
  }
}

async function loadSetupDepartments(facultyId, selectedId = '') {
  setupDirectorySelect('setupDepartmentSelect', [], 'Select a faculty first');
  if (!facultyId) return;
  try {
    const departments = await apiGet(`/admin/institution/departments?facultyId=${encodeURIComponent(facultyId)}`);
    setupDirectorySelect('setupDepartmentSelect', departments, departments.length ? 'Select a department' : 'No departments configured', selectedId);
    if (selectedId) await loadSetupPrograms(selectedId);
  } catch (err) {
    setAdminStatus('departmentStatus', err.message || 'Unable to load departments.', 'error');
    throw err;
  }
}

async function loadSetupPrograms(departmentId) {
  const list = document.getElementById('institutionProgramList');
  if (!list) return;
  if (!departmentId) {
    list.innerHTML = '<p>Select a department to view its active programmes.</p>';
    return;
  }
  try {
    const programs = await apiGet(`/admin/institution/programs?departmentId=${encodeURIComponent(departmentId)}`);
    list.innerHTML = programs.length
      ? `<ul>${programs.map(program => `<li>${escapeHtml(program.code)} · ${escapeHtml(program.name)} (${escapeHtml(program.awardTitle)}) <label>Award type <select id="program-award-type-${program.id}"><option value="">Unclassified</option><option value="BACHELOR" ${program.awardType === 'BACHELOR' ? 'selected' : ''}>Bachelor's Degree</option><option value="DIPLOMA" ${program.awardType === 'DIPLOMA' ? 'selected' : ''}>Diploma</option></select></label> <button type="button" class="btn-small" onclick="saveProgramAwardType(${program.id})">Save type</button></li>`).join('')}</ul>`
      : '<p>No active programmes in this department.</p>';
  } catch (err) {
    list.textContent = err.message || 'Unable to load programmes.';
  }
}

async function loadAdminAcademicYears() {
  const list = document.getElementById('academicYearList');
  if (!list) return;
  try {
    const years = await apiGet('/admin/institution/academic-years');
    list.innerHTML = years.length
      ? `<ul>${years.map(year => `<li><strong>${escapeHtml(year.name)} (${escapeHtml(year.code)})</strong> · ${escapeHtml(year.startDate)} to ${escapeHtml(year.endDate)} · ${year.active ? '<span class="badge success">Active</span>' : `<button class="btn-small" type="button" onclick="activateAcademicYear(${year.id})">Make active</button>`}</li>`).join('')}</ul>`
      : '<p>No academic years configured yet.</p>';
    const yearSelect = document.getElementById('academicTermYearId');
    if (yearSelect) {
      const previousId = yearSelect.value;
      const selectedYear = years.find(year => String(year.id) === previousId) || years.find(year => year.active);
      yearSelect.innerHTML = `<option value="">${years.length ? 'Select an academic year' : 'No academic years configured'}</option>${years.map(year =>
        `<option value="${year.id}">${escapeHtml(year.code)} · ${escapeHtml(year.name)}${year.active ? ' (Active)' : ''}</option>`).join('')}`;
      yearSelect.disabled = years.length === 0;
      yearSelect.value = selectedYear ? String(selectedYear.id) : '';
      document.getElementById('createAcademicTermButton').disabled = !yearSelect.value;
      if (yearSelect.value) await loadAcademicTerms(yearSelect.value);
    }
  } catch (err) {
    list.textContent = err.message || 'Unable to load academic years.';
    setAdminStatus('academicTermStatus', err.message || 'Unable to load academic years.', 'error');
  }
}

async function loadAcademicTerms(academicYearId, selectedTermId = '') {
  const list = document.getElementById('academicTermList');
  const termSelect = document.getElementById('sectionAcademicTermId');
  if (!list || !termSelect) return;
  if (!academicYearId) {
    list.innerHTML = '<p>Select an academic year to view its terms.</p>';
    termSelect.replaceChildren(new Option('Select an academic year first', ''));
    termSelect.disabled = true;
    document.getElementById('createAcademicTermButton').disabled = true;
    updateCourseSectionButton();
    return;
  }
  list.textContent = 'Loading academic terms…';
  try {
    const terms = await apiGet(`/admin/institution/academic-terms?academicYearId=${encodeURIComponent(academicYearId)}`);
    list.innerHTML = terms.length
      ? `<ul>${terms.map(term => `<li><strong>${escapeHtml(term.name)} (${escapeHtml(term.code)})</strong> · ${escapeHtml(term.startDate)} to ${escapeHtml(term.endDate)} · Registration ${term.registrationOpen ? '<span class="badge success">Open</span><button class="btn-small" type="button" onclick="setAcademicTermRegistration(${term.id}, false)">Close window</button>' : `<span class="badge">Closed</span><button class="btn-small" type="button" onclick="setAcademicTermRegistration(${term.id}, true)">Open window</button>`}</li>`).join('')}</ul>`
      : '<p>No academic terms configured for this year.</p>';
    termSelect.innerHTML = `<option value="">${terms.length ? 'Select an academic term' : 'No terms configured'}</option>${terms.map(term =>
      `<option value="${term.id}">${escapeHtml(term.code)} · ${escapeHtml(term.name)}</option>`).join('')}`;
    termSelect.disabled = terms.length === 0;
    termSelect.value = selectedTermId ? String(selectedTermId) : '';
    document.getElementById('createAcademicTermButton').disabled = false;
    const timetableTermSelect = document.getElementById('timetableAcademicTermId');
    if (timetableTermSelect) {
      const previousTimetableTermId = timetableTermSelect.value;
      timetableTermSelect.innerHTML = `<option value="">${terms.length ? 'Select an academic term' : 'No terms configured'}</option>${terms.map(term =>
        `<option value="${term.id}">${escapeHtml(term.code)} · ${escapeHtml(term.name)}</option>`).join('')}`;
      timetableTermSelect.disabled = terms.length === 0;
      timetableTermSelect.value = selectedTermId
        ? String(selectedTermId)
        : (terms.some(term => String(term.id) === previousTimetableTermId) ? previousTimetableTermId : '');
    }
    updateCourseSectionButton();
    if (termSelect.value) await loadCourseSections(termSelect.value);
    else {
      document.getElementById('courseSectionList').innerHTML = '<p>Select an academic term to view its sections.</p>';
    }
  } catch (err) {
    list.textContent = err.message || 'Unable to load academic terms.';
    termSelect.replaceChildren(new Option('Unable to load terms', ''));
    termSelect.disabled = true;
    const timetableTermSelect = document.getElementById('timetableAcademicTermId');
    if (timetableTermSelect) {
      timetableTermSelect.replaceChildren(new Option('Unable to load terms', ''));
      timetableTermSelect.disabled = true;
    }
    setAdminStatus('academicTermStatus', err.message || 'Unable to load academic terms.', 'error');
  }
}

async function setAcademicTermRegistration(termId, registrationOpen) {
  const yearId = document.getElementById('academicTermYearId')?.value;
  if (!yearId) return;
  const action = registrationOpen ? 'open' : 'close';
  if (!confirm(`Are you sure you want to ${action} registration for this academic term?`)) return;
  try {
    await apiFetch(`/admin/institution/academic-terms/${termId}/registration-window`, {
      method: 'PATCH',
      body: JSON.stringify({ registrationOpen }),
    });
    setAdminStatus('academicTermStatus', `Registration window ${registrationOpen ? 'opened' : 'closed'}.`, 'success');
    await loadAcademicTerms(yearId, termId);
  } catch (err) {
    setAdminStatus('academicTermStatus', err.message || 'Unable to update registration window.', 'error');
  }
}

async function loadCourseSections(termId) {
  const list = document.getElementById('courseSectionList');
  if (!list) return;
  if (!termId) {
    list.innerHTML = '<p>Select an academic term to view its sections.</p>';
    return;
  }
  list.textContent = 'Loading course sections…';
  try {
    const sections = await apiGet(`/registration/sections?termId=${encodeURIComponent(termId)}`);
    list.innerHTML = sections.length
      ? `<div class="table-scroll"><table class="table"><thead><tr><th>Course</th><th>Section</th><th>Capacity</th><th>Registered</th><th>Seats available</th></tr></thead><tbody>${sections.map(section =>
        `<tr><td>${escapeHtml(section.courseCode)} · ${escapeHtml(section.courseName)}</td><td>${escapeHtml(section.sectionCode)}</td><td>${escapeHtml(section.capacity)}</td><td>${escapeHtml(section.registeredCount)}</td><td>${escapeHtml(section.seatsAvailable)}</td></tr>`).join('')}</tbody></table></div>`
      : '<p>No active course sections are configured for this term.</p>';
  } catch (err) {
    list.textContent = err.message || 'Unable to load course sections.';
    throw err;
  }
}

function updateCourseSectionButton() {
  const button = document.getElementById('createCourseSectionButton');
  const term = document.getElementById('sectionAcademicTermId');
  const course = document.getElementById('sectionCourseId');
  if (button && term && course) button.disabled = !term.value || !course.value;
}

async function activateAcademicYear(id) {
  try {
    await apiFetch(`/admin/institution/academic-years/${id}/activate`, { method: 'PATCH' });
    setAdminStatus('academicYearStatus', 'Academic year activated for admissions.', 'success');
    await loadAdminAcademicYears();
  } catch (err) {
    setAdminStatus('academicYearStatus', err.message, 'error');
  }
}

function setAdminStatus(id, message, kind) {
  const element = document.getElementById(id);
  if (element) { element.textContent = message; element.className = `teacher-status ${kind}`; }
}

async function loadUsers() {
  const tbody = document.querySelector('#usersModule table tbody');
  if (!tbody) return;
  try {
    const users = await apiGet('/admin/users');
    window._usersCache = users;
    tbody.innerHTML = users.length ? users.map(user => {
      const active = user.isActive ?? user.active;
      const isCurrentUser = user.username === localStorage.getItem('mitUsername');
      const statusAction = active
        ? (isCurrentUser ? '<span class="muted">Current account</span>' : `<button class="btn-small delete" onclick="deactivateUser(${user.id})">Deactivate</button>`)
        : `<button class="btn-small" onclick="restoreUser(${user.id})">Restore</button>`;
      return `<tr><td>#${user.id}</td><td>${escapeHtml(user.username)}</td><td>${escapeHtml(user.role)}</td><td>${escapeHtml(user.email || '—')}</td><td>${active ? 'Active' : 'Deactivated'}</td><td><button class="btn-small" onclick="editUser(${user.id})">Edit</button>${statusAction}</td></tr>`;
    }).join('') : '<tr><td colspan="6">No users found.</td></tr>';
  } catch (err) { tbody.innerHTML = `<tr><td colspan="6">${escapeHtml(err.message)}</td></tr>`; }
}

function openUserForm(user = null) {
  const roleOptions = user
    ? ['STUDENT', 'TEACHER', 'ADMIN'].map(role =>
        `<option value="${role}" ${user.role === role ? 'selected' : ''}>${role}</option>`).join('')
    : '<option value="ADMIN">ADMIN</option>';
  openModal(`<span class="modal-close" onclick="closeModal()">&times;</span><h2>${user ? 'Edit' : 'Add Administrator'}</h2><form id="userForm"><div class="modal-field"><label>Username</label><input name="username" value="${escapeHtml(user?.username || '')}" required ${user ? 'readonly' : ''}></div><div class="modal-field"><label>Password</label><input name="password" type="password" ${user ? '' : 'required'}></div><div class="modal-field"><label>Role</label><select name="role">${roleOptions}</select></div><div class="modal-field"><label>Email</label><input name="email" type="email" value="${escapeHtml(user?.email || '')}"></div><div class="modal-field"><label>Phone</label><input name="phone" value="${escapeHtml(user?.phone || '')}"></div><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button class="btn-primary">Save</button></div></form>`);
  document.getElementById('userForm').addEventListener('submit', async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.target));
    try { await apiFetch(user ? `/admin/users/${user.id}` : '/admin/users', { method: user ? 'PUT' : 'POST', body: JSON.stringify(data) }); closeModal(); loadUsers(); }
    catch (err) { alert(err.message); }
  });
}

function editUser(id) { const user = (window._usersCache || []).find(item => item.id === id); if (user) openUserForm(user); }
async function deactivateUser(id) { if (!confirm('Deactivate this user?')) return; try { await apiFetch(`/admin/users/${id}`, { method: 'DELETE' }); loadUsers(); } catch (err) { alert(err.message); } }
async function restoreUser(id) { try { await apiFetch(`/admin/users/${id}/restore`, { method: 'POST' }); loadUsers(); } catch (err) { alert(err.message); } }

async function publishAnnouncement() {
  const title = prompt('Announcement title:');
  if (!title) return;
  const content = prompt('Announcement content:');
  if (!content) return;
  try {
    await apiFetch('/announcements', { method: 'POST', body: JSON.stringify({ title, content }) });
    alert('Announcement published.');
  } catch (err) { alert(err.message || 'Announcement could not be published.'); }
}

async function loadAdminGrades() {
  const tbody = document.querySelector('#gradesModule table tbody');
  if (!tbody) return;
  try {
    const grades = await apiGet('/admin/grades');
    window._adminGrades = grades;
    window._adminReviewedAssessmentIds = new Set();
    tbody.innerHTML = grades.length ? grades.map(grade => {
      const finalized = grade.status === 'APPROVED' || grade.status === 'REJECTED';
      const actions = finalized
        ? `<span class="badge ${grade.status === 'APPROVED' ? 'success' : 'danger'}">${escapeHtml(grade.status)}</span>${grade.status === 'APPROVED' ? `${grade.retakeRequired ? `<button class="btn-small" onclick="createGradeRetake(${grade.id})">Create retake</button>` : ''}<button class="btn-small" onclick="authorizeGradeCorrection(${grade.id})">Authorize correction</button>` : ''}`
        : `<div class="row-actions"><button class="btn-small" onclick="reviewGradeAssessments(${grade.id})">Review marks</button><button class="btn-small" data-approve-grade="${grade.id}" onclick="approveGrade(${grade.id})" disabled>Approve</button><button class="btn-small danger" onclick="rejectGrade(${grade.id})">Reject</button></div>`;
      return `<tr>
      <td>${escapeHtml(grade.studentUsername)}</td>
      <td>${escapeHtml(grade.studentName)}</td>
      <td>${escapeHtml(grade.courseName)}</td>
      <td><span class="grade">${escapeHtml(grade.gradeValue)}</span>${grade.retakeRequired ? ' <span class="badge danger">Retake required</span>' : ''}</td>
      <td>${grade.gpaScore == null ? '—' : Number(grade.gpaScore).toFixed(2)}</td>
      <td><span class="badge ${grade.status === 'APPROVED' ? 'success' : grade.status === 'REJECTED' ? 'danger' : 'pending'}">${escapeHtml(grade.status || 'PENDING')}</span></td>
      <td>${escapeHtml(grade.submittedBy || '—')}</td>
      <td>${escapeHtml(grade.approvedBy || '—')}</td>
      <td>${escapeHtml(grade.rejectedBy || '—')}</td>
      <td>${escapeHtml(grade.remarks || '—')}</td>
      <td>${escapeHtml(grade.rejectionReason || '—')}</td>
      <td>${actions}</td>
    </tr>`;
    }).join('') : '<tr><td colspan="12">No grades found.</td></tr>';
  } catch (err) { tbody.innerHTML = `<tr><td colspan="12">${escapeHtml(err.message)}</td></tr>`; }
}

async function reviewGradeAssessments(id) {
  try {
    const marks = await apiGet(`/teacher/grades/${id}/assessment-marks`);
    const grade = (window._adminGrades || []).find(item => Number(item.id) === Number(id));
    const missing = marks.filter(mark => mark.score == null);
    const rows = marks.length ? marks.map(mark => `<tr>
      <td>${escapeHtml(mark.displayName)} (${escapeHtml(mark.componentType)})</td>
      <td>${mark.score == null ? '<span class="badge pending">MISSING</span>' : `${escapeHtml(mark.score)} / ${escapeHtml(mark.maxScore)}`}</td>
      <td>${escapeHtml(mark.weightPercentage)}%</td>
    </tr>`).join('') : '<tr><td colspan="3">No assessment structure is configured for this course.</td></tr>';
    openModal(`<button type="button" class="modal-close" onclick="closeModal()" aria-label="Close">&times;</button>
      <h2>Assessment review · ${escapeHtml(grade?.courseName || `Grade ${id}`)}</h2>
      <p>Calculated result: ${escapeHtml(grade?.totalMark ?? 'NOT FINALIZED')}% · ${escapeHtml(grade?.gradeValue || '—')} · ${grade?.gpaScore == null ? '—' : `${Number(grade.gpaScore).toFixed(2)} grade points`}</p>
      <div class="roster-wrap"><table class="admin-table"><thead><tr><th>Assessment component</th><th>Mark</th><th>Weight</th></tr></thead><tbody>${rows}</tbody></table></div>
      <p class="teacher-status ${missing.length ? 'error' : 'success'}" role="status">${missing.length ? `${missing.length} assessment mark(s) are missing. Approval will be rejected until all required marks are entered.` : 'All configured assessment components have marks. The server will recompute and validate the weighted result during approval.'}</p>
      <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button><button type="button" class="btn-primary" ${missing.length || !marks.length ? 'disabled' : ''} onclick="acknowledgeGradeReview(${id}, ${missing.length === 0 && marks.length > 0})">Mark reviewed</button></div>`);
  } catch (error) {
    alert(error.message || 'Unable to load assessment details.');
  }
}

async function acknowledgeGradeReview(id, complete) {
  if (!complete) return;
  try {
    await apiFetch(`/admin/grades/${id}/review`, { method: 'POST' });
    closeModal();
    window._adminReviewedAssessmentIds = window._adminReviewedAssessmentIds || new Set();
    window._adminReviewedAssessmentIds.add(Number(id));
    const approve = document.querySelector(`[data-approve-grade="${Number(id)}"]`);
    if (approve) approve.disabled = false;
  } catch (error) {
    alert(error.message || 'Unable to record the assessment review.');
  }
}

async function approveGrade(id) {
  if (!window._adminReviewedAssessmentIds?.has(Number(id))) {
    return alert('Review the assessment marks and validation status before approving this grade.');
  }
  try {
    await apiFetch(`/admin/grades/${id}/approve`, { method: 'POST' });
    await loadAdminGrades();
  } catch (err) { alert(err.message); }
}

async function createGradeRetake(id) {
  if (!confirm('Create a separate retake attempt? The original grade and marks will remain unchanged.')) return;
  try {
    await apiFetch(`/admin/grades/${id}/retakes`, { method: 'POST' });
    await loadAdminGrades();
  } catch (err) { alert(err.message || 'Unable to create the retake attempt.'); }
}

async function rejectGrade(id) {
  const reason = prompt('Rejection reason:');
  if (!reason) return;
  if (reason.length > 1000) { alert('Rejection reasons must be 1,000 characters or fewer.'); return; }
  try {
    const grade = await apiFetch(`/admin/grades/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) });
    await loadAdminGrades();
  } catch (err) { alert(err.message); }
}

async function authorizeGradeCorrection(id) {
  const reason = prompt('Required reason for reopening this approved result (max 2,000 characters):');
  if (!reason || !reason.trim()) return;
  if (reason.length > 2000) { alert('Correction reasons must be 2,000 characters or fewer.'); return; }
  if (!confirm('The assigned teacher will be able to correct and resubmit this result. Student access will be locked as soon as a change begins, and the corrected result will require approval and publication again. Continue?')) return;
  try {
    await apiFetch(`/admin/grades/${id}/corrections`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason.trim() })
    });
    alert('Correction authorized and previous result snapshot preserved.');
    await loadAdminGrades();
  } catch (err) { alert(err.message || 'Grade correction could not be authorized.'); }
}

async function importGrades() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.csv,.xls,.xlsx';
  input.onchange = async () => {
    let file = input.files?.[0];
    if (!file) return;
    try {
      file = validateUploadFile(file, ['csv', 'xls', 'xlsx']);
      const formData = new FormData();
      formData.append('file', file);
      const response = await apiFetch('/admin/grades/import-file', { method: 'POST', body: formData });
      const imported = await response.json();
      await loadAdminGrades();
      alert(`${imported.length} grade record(s) imported. Imported grades remain pending until approved.`);
    } catch (err) { alert(err.message || 'Grade import failed.'); }
  };
  input.click();
}

async function loadDeletedUsers() {
  const tbody = document.querySelector('#usersModule table tbody');
  if (!tbody) return;
  try {
    const users = await apiGet('/admin/deleted-users');
    window._usersCache = users;
    tbody.innerHTML = users.length ? users.map(user => `<tr><td>#${user.id}</td><td>${escapeHtml(user.username)}</td><td>${escapeHtml(user.role)}</td><td>${escapeHtml(user.email || '—')}</td><td>Deactivated</td><td><button class="btn-small" onclick="restoreUser(${user.id})">Restore</button></td></tr>`).join('') : '<tr><td colspan="6">No deleted users found.</td></tr>';
  } catch (err) { tbody.innerHTML = `<tr><td colspan="6">${escapeHtml(err.message)}</td></tr>`; }
}

async function loadAdminFinance() {
  try {
    const [fees, attempts] = await Promise.all([
      apiGet('/admin/fees'), apiGet('/finance/payments/attempts/pending'),
    ]);
    const total = fees.reduce((sum, fee) => sum + Number(fee.amount || 0), 0);
    const paid = fees.reduce((sum, fee) => sum + Number(fee.amountPaid || (fee.paid ? fee.amount : 0)), 0);
    const pending = fees.reduce((sum, fee) => sum + Number(fee.balance ?? (fee.paid ? 0 : fee.amount)), 0);
    const cards = document.querySelectorAll('#financeModule .finance-card .value');
    if (cards[0]) cards[0].textContent = `UGX ${paid.toLocaleString()}`;
    if (cards[1]) cards[1].textContent = `UGX ${pending.toLocaleString()}`;
    if (cards[2]) cards[2].textContent = `UGX ${fees.filter(fee => Number(fee.balance ?? (fee.paid ? 0 : fee.amount)) > 0 && fee.dueDate && fee.dueDate < new Date().toISOString().slice(0, 10)).reduce((sum, fee) => sum + Number(fee.balance ?? (fee.paid ? 0 : fee.amount)), 0).toLocaleString()}`;
    if (cards[3]) cards[3].textContent = total ? `${Math.round((paid / total) * 100)}%` : '0%';
    const tbody = document.querySelector('#financeModule table tbody');
    if (tbody) tbody.innerHTML = fees.length ? fees.map(fee => {
      const amountPaid = Number(fee.amountPaid ?? (fee.paid ? fee.amount : 0));
      const balance = Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
      const settled = balance <= 0;
      return `<tr><td>${escapeHtml(fee.studentName)} (${escapeHtml(fee.studentUsername)})</td><td>UGX ${Number(fee.amount).toLocaleString()}</td><td>UGX ${amountPaid.toLocaleString()}</td><td>UGX ${balance.toLocaleString()}</td><td>${escapeHtml(fee.dueDate || 'Not set')}</td><td><span class="badge ${settled ? 'success' : 'pending'}">${settled ? 'Settled' : amountPaid > 0 ? 'Partially paid' : 'Pending'}</span></td></tr>`;
    }).join('') : '<tr><td colspan="6">No fee records found.</td></tr>';
    renderAdminPaymentAttempts(attempts);
    window._adminFees = fees;
    await loadAdminRefunds();
  } catch (err) { console.error('Failed to load finance data', err); }
}

async function loadAdminPaymentAttempts() {
  try {
    renderAdminPaymentAttempts(await apiGet('/finance/payments/attempts/pending'));
  } catch (err) {
    alert(err.message || 'Unable to load pending payment attempts.');
  }
}

function renderAdminPaymentAttempts(attempts) {
  const tbody = document.querySelector('#paymentAttemptTable tbody');
  if (!tbody) return;
  tbody.innerHTML = attempts.length ? attempts.map(attempt => {
    const isFlutterwave = attempt.paymentChannel === 'FLUTTERWAVE';
    const checkoutExpired = attempt.checkoutExpiresAt
      && new Date(attempt.checkoutExpiresAt).getTime() <= Date.now();
    const reviewActions = isFlutterwave
      ? checkoutExpired
        ? `<span>Checkout expired</span><button class="btn-small danger" onclick="rejectAdminPaymentAttempt(${Number(attempt.id)})">Close attempt</button>`
        : '<span>Awaiting verified Flutterwave result</span>'
      : `<button class="btn-small" onclick="confirmAdminPaymentAttempt(${Number(attempt.id)})">Confirm verified</button>
          <button class="btn-small danger" onclick="rejectAdminPaymentAttempt(${Number(attempt.id)})">Reject</button>`;
    return `
    <tr>
      <td>#${Number(attempt.id)}</td>
      <td>Student #${Number(attempt.studentId)}</td>
      <td>Fee #${Number(attempt.feeId)}</td>
      <td>${escapeHtml(attempt.paymentChannel || 'BANK_TRANSFER').replaceAll('_', ' ')}</td>
      <td>UGX ${Number(attempt.amount).toLocaleString()}</td>
      <td>${escapeHtml(attempt.providerReference)}</td>
      <td>${escapeHtml((attempt.createdAt || '').replace('T', ' ').slice(0, 16))}</td>
      <td>${reviewActions}</td>
    </tr>`;
  }).join('') : '<tr><td colspan="8">No pending payment attempts.</td></tr>';
}

async function confirmAdminPaymentAttempt(id) {
  if (!confirm('Confirm only after matching this reference against the provider transaction or settlement statement.')) return;
  try {
    await apiFetch(`/finance/payments/attempts/${id}/confirm`, { method: 'POST' });
    await loadAdminFinance();
  } catch (err) { alert(err.message || 'Unable to confirm the payment attempt.'); }
}

async function rejectAdminPaymentAttempt(id) {
  const reason = prompt('Why is this payment reference being rejected?');
  if (!reason?.trim()) return;
  try {
    await apiFetch(`/finance/payments/attempts/${id}/reject`, {
      method: 'POST', body: JSON.stringify({ reason: reason.trim() }),
    });
    await loadAdminFinance();
  } catch (err) { alert(err.message || 'Unable to reject the payment attempt.'); }
}

async function loadAdminRefunds() {
  const tbody = document.querySelector('#refundReviewTable tbody');
  if (!tbody) return;
  try {
    const refunds = await apiGet('/finance/refunds');
    tbody.innerHTML = refunds.length ? refunds.map(refund => `
      <tr>
        <td>#${Number(refund.id)}</td>
        <td>#${Number(refund.paymentAttemptId)}</td>
        <td>#${Number(refund.feeId)}</td>
        <td>UGX ${Number(refund.amount).toLocaleString()}</td>
        <td>${escapeHtml(refund.processedBy || '—')}</td>
        <td>${escapeHtml(refund.reason || '—')}</td>
        <td>${escapeHtml(refund.refundReference || '—')}</td>
        <td><button class="btn-small" onclick="completeAdminRefund(${Number(refund.id)})">Confirm external refund</button>
            <button class="btn-small danger" onclick="rejectAdminRefund(${Number(refund.id)})">Reject</button></td>
      </tr>`).join('') : '<tr><td colspan="8">No pending refund requests.</td></tr>';
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8">${escapeHtml(err.message || 'Unable to load refund requests.')}</td></tr>`;
  }
}

async function completeAdminRefund(id) {
  const refundReference = prompt('Enter the verified bank or provider refund reference:');
  if (!refundReference?.trim()) return;
  try {
    await apiFetch(`/finance/refunds/${id}/complete`, {
      method: 'POST', body: JSON.stringify({ refundReference: refundReference.trim() }),
    });
    await loadAdminFinance();
  } catch (err) { alert(err.message || 'Unable to complete the refund review.'); }
}

async function rejectAdminRefund(id) {
  const reason = prompt('Why is this refund request being rejected?');
  if (!reason?.trim()) return;
  try {
    await apiFetch(`/finance/refunds/${id}/reject`, {
      method: 'POST', body: JSON.stringify({ reason: reason.trim() }),
    });
    await loadAdminRefunds();
  } catch (err) { alert(err.message || 'Unable to reject the refund request.'); }
}

async function createAdminFee() {
  const studentId = Number(prompt('Student ID:'));
  const description = prompt('Fee description:');
  const amount = prompt('Amount:');
  const dueDate = prompt('Due date (YYYY-MM-DD):');
  if (!studentId || !description || !amount || !dueDate) return;
  try { await apiFetch('/fees', { method: 'POST', body: JSON.stringify({ studentId, description, amount: Number(amount), dueDate }) }); loadAdminFinance(); }
  catch (err) { alert(err.message || 'Fee creation failed.'); }
}

async function loadRequests() {
  const tbody = document.querySelector('#requestsModule table tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">Loading requests…</td></tr>';
  try {
    const requests = await apiGet('/student/requests/admin');
    const status = document.getElementById('requestStatusFilter')?.value || '';
    const filtered = status ? requests.filter(request => request.status === status) : requests;
    const openCount = requests.filter(request => request.status === 'OPEN').length;
    const navCount = document.getElementById('requestNavCount');
    if (navCount) navCount.textContent = openCount;
    tbody.innerHTML = filtered.length ? filtered.map(request => `
      <tr>
        <td>#REQ-${request.id}<br><strong>${escapeHtml(request.subject)}</strong></td>
        <td>${escapeHtml(request.studentName || '—')}<br>${escapeHtml(request.studentUsername || '')}</td>
        <td>${escapeHtml(request.type)}</td>
        <td>${escapeHtml((request.createdAt || '').slice(0, 10))}</td>
        <td><select onchange="updateRequestStatus(${request.id}, this.value)">
          ${['OPEN','IN_PROGRESS','APPROVED','REJECTED','COMPLETED'].map(option => `<option value="${option}" ${option === request.status ? 'selected' : ''}>${option.replace('_', ' ')}</option>`).join('')}
        </select></td>
        <td><button class="btn-small" onclick="viewRequest(${request.id})">View</button></td>
      </tr>`).join('') : '<tr><td colspan="6" style="text-align:center;">No requests found.</td></tr>';
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--danger);">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function updateRequestStatus(id, status) {
  try {
    await apiFetch(`/student/requests/admin/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
    loadRequests();
  } catch (err) {
    alert(err.message);
    loadRequests();
  }
}

async function viewRequest(id) {
  try {
    const request = await apiGet(`/student/requests/admin/${id}`);
    openModal(`<span class="modal-close" onclick="closeModal()">&times;</span>
      <h2>Request #${request.id}</h2>
      <p><strong>Student:</strong> ${escapeHtml(request.studentName)} (${escapeHtml(request.studentUsername)})</p>
      <p><strong>Type:</strong> ${escapeHtml(request.type)}</p>
      <p><strong>Subject:</strong> ${escapeHtml(request.subject)}</p>
      <p><strong>Details:</strong><br>${escapeHtml(request.details || 'No details provided.')}</p>
      <p><strong>Status:</strong> ${escapeHtml(request.status)}</p>`);
  } catch (err) { alert(err.message); }
}

function closeModal() {
  document.getElementById('modalOverlay').classList.remove('show');
  document.getElementById('modalBox').innerHTML = '';
}

function openModal(html) {
  document.getElementById('modalBox').innerHTML = html;
  document.getElementById('modalOverlay').classList.add('show');
}

function requestAdminInput(title, label, type = 'text') {
  if (pendingAdminInputResolver) return Promise.resolve(null);
  return new Promise(resolve => {
    pendingAdminInputResolver = resolve;
    openModal(`
      <button type="button" class="modal-close" onclick="cancelAdminInput()" aria-label="Close">&times;</button>
      <h2 id="adminInputTitle">${escapeHtml(title)}</h2>
      <form onsubmit="submitAdminInput(event)" aria-labelledby="adminInputTitle">
        <div class="modal-field">
          <label for="adminInputValue">${escapeHtml(label)}</label>
          <input id="adminInputValue" name="value" type="${type}" ${type === 'number' ? 'min="1" step="1"' : ''} required>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" onclick="cancelAdminInput()">Cancel</button>
          <button type="submit" class="btn-primary">Continue</button>
        </div>
      </form>
    `);
    document.getElementById('adminInputValue').focus();
  });
}

function submitAdminInput(event) {
  event.preventDefault();
  if (!pendingAdminInputResolver) return;
  const value = new FormData(event.currentTarget).get('value');
  const resolve = pendingAdminInputResolver;
  pendingAdminInputResolver = null;
  closeModal();
  resolve(value);
}

function cancelAdminInput() {
  if (!pendingAdminInputResolver) return;
  const resolve = pendingAdminInputResolver;
  pendingAdminInputResolver = null;
  closeModal();
  resolve(null);
}

// ===== DASHBOARD =====
async function loadDashboard() {
  try {
    const [statsResult, healthResult, overviewResult, systemStatusResult] = await Promise.allSettled([
      apiGet('/admin/statistics'),
      apiGet('/v1/health'),
      apiGet('/admin/dashboard'),
      apiGet('/admin/system/status'),
    ]);

    const stats = statsResult.status === 'fulfilled' ? statsResult.value : {};
    const health = healthResult.status === 'fulfilled' ? healthResult.value : { status: 'unknown' };
    const overview = overviewResult.status === 'fulfilled' ? overviewResult.value : {};
    const systemStatus = systemStatusResult.status === 'fulfilled' ? systemStatusResult.value : {};

    const healthStatus = document.getElementById('apiHealthStatus');
    if (healthStatus) {
      healthStatus.textContent = health.status === 'ok' ? `● Online (${health.timestamp})` : '● Unavailable';
      healthStatus.classList.toggle('online', health.status === 'ok');
    }
    renderSystemStatus('databaseHealthStatus', systemStatus.database);
    renderSystemStatus('emailHealthStatus', systemStatus.email);
    renderSystemStatus('backupHealthStatus', systemStatus.backup);
    renderSystemStatus('uploadBackupHealthStatus', systemStatus.uploadBackup);
    await loadPendingActions();

    const statValues = document.querySelectorAll('#dashboardModule .stat-value');
    const statChanges = document.querySelectorAll('#dashboardModule .stat-change');
    const totalStudents = overview.totalStudents ?? stats.totalStudents ?? 0;
    const totalTeachers = overview.totalTeachers ?? stats.totalTeachers ?? 0;
    let courseCount = overview.totalCourses ?? 0;
    let revenue = Number(overview.totalRevenue ?? 0);

    if (overviewResult.status !== 'fulfilled' || overview.totalCourses == null || overview.totalRevenue == null) {
      const courses = await apiGet('/admin/courses?size=1');
      const fees = await apiGet('/admin/fees');
      courseCount = Number(courses.totalElements ?? courseCount);
      revenue = fees.filter(fee => fee.paid).reduce((sum, fee) => sum + Number(fee.amount || 0), 0);
    }

    if (statValues[0]) statValues[0].textContent = totalStudents;
    const studentNavCount = document.getElementById('studentNavCount');
    const staffNavCount = document.getElementById('staffNavCount');
    if (studentNavCount) studentNavCount.textContent = totalStudents;
    if (staffNavCount) staffNavCount.textContent = totalTeachers;
    if (statValues[1]) statValues[1].textContent = courseCount;
    if (statValues[2]) statValues[2].textContent = totalTeachers;
    if (statValues[3]) statValues[3].textContent = `UGX ${revenue.toLocaleString()}`;

    const courseNavCount = document.getElementById('courseNavCount');
    if (courseNavCount) courseNavCount.textContent = courseCount;

    statChanges.forEach(el => { el.textContent = ''; });
    renderRecentStudents();
  } catch (err) {
    console.error('Failed to load dashboard', err);
  }
}

async function loadPendingActions() {
  const list = document.getElementById('pendingActionsList');
  const count = document.getElementById('pendingTaskCount');
  if (!list || !count) return;

  const [gradesResult, admissionsResult] = await Promise.allSettled([
    apiGet('/admin/grades/pending'),
    Promise.all([
      apiGet('/admissions/applications?status=SUBMITTED'),
      apiGet('/admissions/applications?status=UNDER_REVIEW'),
    ]).then(([submitted, underReview]) => submitted.concat(underReview)),
  ]);
  const pendingGrades = gradesResult.status === 'fulfilled'
    ? gradesResult.value
    : [];
  const pendingAdmissions = admissionsResult.status === 'fulfilled'
    ? admissionsResult.value
    : [];
  const failedQueues = [
    gradesResult.status === 'rejected' ? 'grade approvals' : null,
    admissionsResult.status === 'rejected' ? 'admissions' : null,
  ].filter(Boolean);
  const taskCount = pendingGrades.length + pendingAdmissions.length;
  count.textContent = failedQueues.length ? '—' : String(taskCount);

  const tasks = [];
  if (pendingGrades.length) {
    tasks.push(`<button type="button" class="task-item task-link" onclick="switchModule('grades')"><span class="task-info"><span class="task-title">${pendingGrades.length} grade approval${pendingGrades.length === 1 ? '' : 's'}</span><span>Review teacher-submitted grades.</span></span><span aria-hidden="true">→</span></button>`);
  }
  if (pendingAdmissions.length) {
    tasks.push(`<button type="button" class="task-item task-link" onclick="switchModule('admissions')"><span class="task-info"><span class="task-title">${pendingAdmissions.length} admission application${pendingAdmissions.length === 1 ? '' : 's'}</span><span>Review submitted applications.</span></span><span aria-hidden="true">→</span></button>`);
  }
  if (failedQueues.length) {
    tasks.push(`<p class="teacher-status error" role="alert">Unable to load ${escapeHtml(failedQueues.join(' and '))} queue${failedQueues.length === 1 ? '' : 's'}.</p>`);
  } else if (!taskCount) {
    tasks.push('<p>No pending actions.</p>');
  }
  list.innerHTML = tasks.join('');
  const admissionsBadge = document.getElementById('admissionsNavCount');
  if (admissionsBadge) admissionsBadge.textContent = admissionsResult.status === 'fulfilled' ? pendingAdmissions.length : '—';
}

async function loadAdmissionApplications() {
  const tbody = document.querySelector('#admissionsTable tbody');
  const status = document.getElementById('admissionQueueStatus');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="9">Loading applications…</td></tr>';
  setAdminStatus('admissionQueueStatus', 'Loading applications…', 'info');
  const selectedStatus = document.getElementById('admissionStatusFilter')?.value || '';
  const query = selectedStatus ? `?status=${encodeURIComponent(selectedStatus)}` : '';
  try {
    const applications = await apiGet(`/admissions/applications${query}`);
    const reviewable = applications.filter(application =>
      application.status === 'SUBMITTED' || application.status === 'UNDER_REVIEW');
    const admissionsBadge = document.getElementById('admissionsNavCount');
    if (admissionsBadge && !selectedStatus) admissionsBadge.textContent = reviewable.length;
    tbody.innerHTML = applications.length ? applications.map(application => {
      const canDecide = application.status === 'SUBMITTED' || application.status === 'UNDER_REVIEW';
      const actions = canDecide
        ? `<div class="row-actions"><button class="btn-small" type="button" onclick="decideAdmission(${application.id}, 'ACCEPTED')">Accept</button><button class="btn-small danger" type="button" onclick="decideAdmission(${application.id}, 'REJECTED')">Reject</button></div>`
        : application.status === 'ACCEPTED' && application.enrolled
          ? '<span class="badge success">Enrolled</span>'
          : application.status === 'ACCEPTED'
            ? `<button class="btn-small" type="button" onclick="enrollAcceptedApplication(${application.id})">Enroll student</button>`
          : `<span class="muted">${escapeHtml(application.status)}</span>`;
      return `<tr><td>${escapeHtml(application.applicationNumber)}</td><td>${escapeHtml(application.applicantName)}</td><td>${escapeHtml(application.email)}${application.phone ? `<br>${escapeHtml(application.phone)}` : ''}</td><td>${escapeHtml(application.programName)}</td><td>${escapeHtml(application.academicYearName || application.academicYearId)}</td><td>${escapeHtml(String(application.submittedAt || '').slice(0, 10) || '—')}</td><td><span class="badge ${application.status === 'ACCEPTED' ? 'success' : application.status === 'REJECTED' ? 'danger' : 'pending'}">${escapeHtml(application.status)}</span></td><td>${escapeHtml(application.reviewerNotes || '—')}</td><td>${actions}</td></tr>`;
    }).join('') : '<tr><td colspan="9">No applications found for this status.</td></tr>';
    setAdminStatus('admissionQueueStatus', `${applications.length} application${applications.length === 1 ? '' : 's'} loaded; ${reviewable.length} awaiting decision.`, 'success');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="9">${escapeHtml(err.message || 'Unable to load applications.')}</td></tr>`;
    if (status) setAdminStatus('admissionQueueStatus', err.message || 'Unable to load applications.', 'error');
    const admissionsBadge = document.getElementById('admissionsNavCount');
    if (admissionsBadge) admissionsBadge.textContent = '!';
  }
}

async function decideAdmission(id, decision) {
  const decisionLabel = decision === 'ACCEPTED' ? 'Accept application' : 'Reject application';
  const reviewerNotes = await requestAdminNotes(decisionLabel, 'Reviewer notes (optional)');
  if (reviewerNotes === null) return;
  try {
    await apiFetch(`/admissions/applications/${id}/decision`, {
      method: 'PATCH',
      body: JSON.stringify({ status: decision, reviewerNotes }),
    });
    await Promise.all([loadAdmissionApplications(), loadPendingActions()]);
    setAdminStatus('admissionQueueStatus', `Application ${decision.toLowerCase()}.`, 'success');
  } catch (err) {
    setAdminStatus('admissionQueueStatus', err.message || 'Unable to decide this application.', 'error');
  }
}

function requestAdminNotes(title, label) {
  if (pendingAdminInputResolver) return Promise.resolve(null);
  return new Promise(resolve => {
    pendingAdminInputResolver = resolve;
    openModal(`
      <span class="modal-close" onclick="cancelAdminInput()">&times;</span>
      <h2>${escapeHtml(title)}</h2>
      <form id="adminInputForm" onsubmit="submitAdminInput(event)">
        <div class="modal-field">
          <label for="adminInputValue">${escapeHtml(label)}</label>
          <textarea id="adminInputValue" name="value" maxlength="2000"></textarea>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" onclick="cancelAdminInput()">Cancel</button>
          <button type="submit" class="btn-primary">Confirm</button>
        </div>
      </form>
    `);
    document.getElementById('adminInputValue').focus();
  });
}

async function enrollAcceptedApplication(applicationId) {
  try {
    const candidates = await apiGet(`/admissions/applications/${applicationId}/enrollment-candidates`);
    if (!candidates.length) {
      const application = (await apiGet('/admissions/applications')).find(item => item.id === applicationId);
      if (!application) throw new Error('Admission application was not found.');
      openStudentForm({
        fullName: application.applicantName,
        email: application.email,
        phone: application.phone,
        className: application.programName,
      }, async studentDetails => {
        const response = await apiFetch(`/admissions/applications/${applicationId}/enroll-new`, {
          method: 'POST',
          body: JSON.stringify(studentDetails),
        });
        const credentials = await response.json();
        await Promise.all([loadAdmissionApplications(), loadPendingActions()]);
        setAdminStatus('admissionQueueStatus', 'Student account created and applicant enrolled successfully.', 'success');
        return credentials;
      });
      return;
    }
    openAdmissionEnrollmentModal(applicationId, candidates);
  } catch (err) {
    setAdminStatus('admissionQueueStatus', err.message || 'Unable to load eligible student accounts.', 'error');
  }
}

function openAdmissionEnrollmentModal(applicationId, candidates) {
  const options = candidates.map(student => {
    const details = `${student.fullName} · ${student.username}${student.registrationNumber ? ` · ${student.registrationNumber}` : ''}${student.enrolled ? ' · Already enrolled' : ''}`;
    return `<option value="${student.id}" ${student.enrolled ? 'disabled' : ''}>${escapeHtml(details)}</option>`;
  }).join('');
  const available = candidates.some(student => !student.enrolled);
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Enroll accepted applicant</h2>
    <p>Only active student accounts with an email matching the application are shown.</p>
    <form id="admissionEnrollmentForm">
      <div class="modal-field">
        <label for="admissionStudentId">Student account</label>
        <select id="admissionStudentId" name="studentId" required ${available ? '' : 'disabled'}>
          <option value="">${available ? 'Select the matching student account' : 'Matching student is already enrolled'}</option>
          ${options}
        </select>
      </div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary" ${available ? '' : 'disabled'}>Confirm enrollment</button>
      </div>
    </form>
  `);
  document.getElementById('admissionEnrollmentForm').addEventListener('submit', async event => {
    event.preventDefault();
    const studentId = Number(new FormData(event.currentTarget).get('studentId'));
    if (!studentId) return;
    const button = event.currentTarget.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      await submitAdmissionEnrollment(applicationId, studentId);
      closeModal();
    } catch (err) {
      button.disabled = false;
      const message = document.createElement('p');
      message.className = 'teacher-status error';
      message.setAttribute('role', 'alert');
      message.textContent = err.message || 'Unable to enroll this student.';
      event.currentTarget.prepend(message);
    }
  });
}

async function submitAdmissionEnrollment(applicationId, studentId) {
  await apiFetch(`/admissions/applications/${applicationId}/enroll`, {
    method: 'POST',
    body: JSON.stringify({ studentId }),
  });
  await Promise.all([loadAdmissionApplications(), loadPendingActions()]);
  setAdminStatus('admissionQueueStatus', 'Accepted applicant enrolled successfully.', 'success');
}

function renderSystemStatus(id, component) {
  const element = document.getElementById(id);
  if (!element) return;
  const status = component?.status || 'unknown';
  element.textContent = component?.label || 'Status unavailable';
  element.title = component?.detail || 'System status could not be retrieved.';
  element.classList.remove('online', 'warning', 'error');
  if (status === 'online' || status === 'configured') element.classList.add('online');
  else if (status === 'disabled' || status === 'incomplete' || status === 'not_configured') {
    element.classList.add('warning');
  } else element.classList.add('error');
}

async function renderRecentStudents() {
  const tbody = document.querySelector('#dashboardModule .card .table tbody');
  if (!tbody) return;
  try {
    const page = await apiGet('/admin/students?size=5&sort=id,desc');
    const students = page.content || [];
    if (!students.length) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-secondary);">No students yet.</td></tr>`;
      return;
    }
    tbody.innerHTML = students.map(s => `
      <tr>
        <td>${escapeHtml(s.fullName)}</td>
        <td>${escapeHtml(s.registrationNumber || `#${s.id}`)}</td>
        <td>${escapeHtml(s.className || '—')}</td>
        <td><span class="badge ${s.active ? 'active' : 'inactive'}">${s.active ? 'Active' : 'Deactivated'}</span></td>
      </tr>`).join('');
  } catch (err) {
    console.error('Failed to load recent students', err);
  }
}

// ===== STUDENTS =====
async function loadStudents(page = 0) {
  const tbody = document.querySelector('#studentsModule table tbody');
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;">Loading…</td></tr>`;

  try {
    const query = document.querySelector('[data-admin-filter="student-search"]')?.value.trim() || '';
    const active = document.querySelector('[data-admin-filter="student-status"]')?.value || '';
    const params = new URLSearchParams({ page: String(page), size: '20', sort: 'id' });
    if (query) params.set('q', query);
    if (active) params.set('active', active);
    const data = await apiGet(`/admin/students?${params.toString()}`);
    const students = data.content || [];

    tbody.innerHTML = students.length ? students.map(s => `
      <tr>
        <td>${escapeHtml(s.registrationNumber || '—')}</td>
        <td>${escapeHtml(s.fullName)}</td>
        <td>${escapeHtml(s.className || '—')}</td>
        <td>${escapeHtml(s.cgpa != null ? Number(s.cgpa).toFixed(2) : '—')}</td>
        <td>${escapeHtml(s.email || '—')}</td>
        <td><span class="badge ${s.active ? 'active' : 'inactive'}">${s.active ? 'Active' : 'Deactivated'}</span></td>
        <td>
          <button class="btn-small edit" onclick="openEditStudentModal(${s.id})">Edit</button>
          <button class="btn-small" onclick="viewStudentCgpa(${s.id})">CGPA</button>
          <button class="btn-small" onclick="openStudentGraduationAudit(${s.id})">Grad audit</button>
          <button class="btn-small" onclick="openStudentLifecycleModal(${s.id})">Lifecycle</button>
          <button class="btn-small" onclick="openStudentTransferModal(${s.id})">Transfer</button>
          <button class="btn-small" onclick="recalculateStudentCgpa(${s.id})">Recalculate</button>
          ${s.active ? `<button class="btn-small delete" onclick="confirmDeactivateStudent(${s.id}, '${escapeHtml(s.fullName)}')">Deactivate</button>` : `<button class="btn-small" onclick="restoreStudent(${s.id})">Restore</button>`}
        </td>
      </tr>`).join('') : `<tr><td colspan="7" style="text-align:center;color:var(--text-secondary);">No students admitted yet.</td></tr>`;

    renderPagination('studentsModule', data, loadStudents);
    window._studentsCache = students;
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;color:var(--danger);">${escapeHtml(err.message)}</td></tr>`;
  }
}

function renderPagination(moduleId, page, reloadFn) {
  const moduleEl = document.getElementById(moduleId);
  let container = moduleEl.querySelector('.pagination-controls');
  if (!container) {
    container = document.createElement('div');
    container.className = 'pagination-controls';
    moduleEl.appendChild(container);
  }
  const current = page.number ?? 0;
  const total = page.totalPages ?? 1;
  container.innerHTML = `
    <button ${current <= 0 ? 'disabled' : ''} id="pgPrev">← Prev</button>
    <span>Page ${current + 1} of ${Math.max(total, 1)} (${page.totalElements ?? 0} total)</span>
    <button ${current + 1 >= total ? 'disabled' : ''} id="pgNext">Next →</button>
  `;
  container.querySelector('#pgPrev')?.addEventListener('click', () => reloadFn(current - 1));
  container.querySelector('#pgNext')?.addEventListener('click', () => reloadFn(current + 1));
}

function openStudentForm(prefill = {}, onCreated = null) {
  const admissionEnrollment = Boolean(onCreated);
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Admit New Student</h2>
    <form id="admitStudentForm">
      <div class="modal-field"><label>Full Name *</label><input name="fullName" value="${escapeHtml(prefill.fullName || '')}" required></div>
      <div class="modal-field"><label>Username *</label><input name="username" required minlength="3"></div>
      ${admissionEnrollment
        ? '<p class="muted">A secure temporary password will be generated. If email delivery is configured, it will be sent to the student; otherwise, it will be shown once for private handoff. The student must change it before accessing student services.</p>'
        : '<div class="modal-field"><label>Password *</label><input name="password" type="password" required minlength="6"></div>'}
      <div class="modal-row">
        <div class="modal-field"><label>Email *</label><input name="email" type="email" value="${escapeHtml(prefill.email || '')}" required></div>
        <div class="modal-field"><label>Phone</label><input name="phone" value="${escapeHtml(prefill.phone || '')}"></div>
      </div>
      <div class="modal-field"><label>Class / Program</label><input name="className" value="${escapeHtml(prefill.className || '')}" placeholder="e.g. B.IT Year 2"></div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary">${onCreated ? 'Create account and enroll' : 'Admit Student'}</button>
      </div>
    </form>
  `);
  document.getElementById('admitStudentForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      const studentDetails = {
        fullName: fd.get('fullName'), username: fd.get('username'),
        ...(admissionEnrollment ? {} : { password: fd.get('password') }),
        ...(admissionEnrollment ? {} : { className: fd.get('className') }),
        email: fd.get('email'), phone: fd.get('phone'),
      };
      if (onCreated) {
        const credentials = await onCreated(studentDetails);
        closeModal();
        await loadStudents();
        showAdmissionCredentials(credentials);
        return;
      }
      await apiFetch('/admin/students/admit', {
        method: 'POST',
        body: JSON.stringify(studentDetails),
      });
      closeModal();
      await loadStudents();
    } catch (err) { alert(err.message); }
  });
}

function showAdmissionCredentials(credentials) {
  const loginUrl = `${window.location.origin}/index.html`;
  const emailSent = credentials.emailSent === true;
  const deliveryMessage = credentials.emailDeliveryMessage
    || (emailSent
      ? 'One-time sign-in details were emailed to the applicant.'
      : 'Email is unavailable. Share the temporary password privately with the student.');
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Student account ready</h2>
    <p>${escapeHtml(deliveryMessage)}</p>
    <div class="modal-field"><label for="admissionLoginUrl">Student sign-in page</label><input id="admissionLoginUrl" value="${escapeHtml(loginUrl)}" readonly></div>
    <div class="modal-field"><label for="admissionUsername">Username</label><input id="admissionUsername" value="${escapeHtml(credentials.username)}" readonly></div>
    ${emailSent
      ? '<p>The student must sign in with the emailed temporary password and choose a new password before accessing student services.</p>'
      : `<div class="modal-field"><label for="admissionTemporaryPassword">Temporary password (one-time display)</label><input id="admissionTemporaryPassword" value="${escapeHtml(credentials.temporaryPassword || '')}" readonly></div>`}
    <p id="admissionCredentialCopyStatus" role="status" aria-live="polite"></p>
    <div class="modal-actions">
      ${emailSent ? '' : '<button type="button" class="btn-secondary" id="copyAdmissionCredentials">Copy sign-in details</button>'}
      <button type="button" class="btn-primary" onclick="closeModal()">Done</button>
    </div>
  `);
  const copyButton = document.getElementById('copyAdmissionCredentials');
  if (copyButton) copyButton.addEventListener('click', async () => {
    const details = `Student sign-in: ${loginUrl}\nUsername: ${credentials.username}\nTemporary password: ${credentials.temporaryPassword}`;
    const status = document.getElementById('admissionCredentialCopyStatus');
    try {
      await navigator.clipboard.writeText(details);
      status.textContent = 'Sign-in details copied. Share them privately and do not store the temporary password.';
    } catch {
      status.textContent = 'Could not access clipboard. Copy the fields individually and share them privately.';
    }
  });
}

function openEditStudentModal(id) {
  const student = (window._studentsCache || []).find(s => s.id === id);
  if (!student) return;
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Edit Student</h2>
    <form id="editStudentForm">
      <div class="modal-field"><label>Full Name *</label><input name="fullName" value="${escapeHtml(student.fullName)}" required></div>
      <div class="modal-field"><label>Class / Program</label><input name="className" value="${escapeHtml(student.className || '')}"></div>
      <div class="modal-row">
        <div class="modal-field"><label>Email</label><input name="email" type="email" value="${escapeHtml(student.email || '')}"></div>
        <div class="modal-field"><label>Phone</label><input name="phone" value="${escapeHtml(student.phone || '')}"></div>
      </div>
      <div class="modal-field"><label>Address</label><textarea name="address" rows="2">${escapeHtml(student.address || '')}</textarea></div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary">Save Changes</button>
      </div>
    </form>
  `);
  document.getElementById('editStudentForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await apiFetch(`/admin/students/${id}`, {
        method: 'PUT',
        body: JSON.stringify({
          fullName: fd.get('fullName'), className: fd.get('className'),
          email: fd.get('email'), phone: fd.get('phone'), address: fd.get('address'),
        }),
      });
      closeModal();
      loadStudents();
    } catch (err) { alert(err.message); }
  });
}

const studentLifecycleTransitions = {
  ACTIVE: ['DEFERRED', 'SUSPENDED', 'WITHDRAWN', 'GRADUATED'],
  DEFERRED: ['ACTIVE', 'SUSPENDED', 'WITHDRAWN'],
  SUSPENDED: ['ACTIVE', 'WITHDRAWN'],
  GRADUATED: ['ALUMNI'],
  WITHDRAWN: [],
  ALUMNI: [],
};

async function openStudentGraduationAudit(studentId) {
  try {
    const enrollments = (await apiGet(`/admin/students/${studentId}/programs`))
      .filter(enrollment => enrollment.status === 'ACTIVE');
    if (!enrollments.length) {
      alert('A graduation audit requires an active programme enrollment.');
      return;
    }
    const audits = await Promise.all(enrollments.map(async enrollment => ({
      enrollment,
      audit: await apiGet(`/academic-records/${studentId}/degree-audit?programId=${encodeURIComponent(enrollment.programId)}&academicYearId=${encodeURIComponent(enrollment.academicYearId)}`),
    })));
    const renderAudit = ({ enrollment, audit }, index) => {
      const blockers = [];
      if (!audit.academicRequirementsMet) blockers.push(`${audit.completedRequiredCourses}/${audit.requiredCourses} required courses complete`);
      if (!audit.directoryActive) blockers.push('programme directory is inactive');
      if (audit.activeFinancialHold) blockers.push('active financial hold');
      if (audit.outstandingFees) blockers.push('outstanding fees');
      if (!audit.requiredCourses) blockers.push('programme curriculum has no required courses');
      const eligible = audit.eligibleForGraduation === true;
      return `
        <section class="lifecycle-audit-card">
          <h3>${escapeHtml(enrollment.programName)} · ${escapeHtml(enrollment.academicYearName)}</h3>
          <p><strong>Academic requirements:</strong> ${audit.academicRequirementsMet ? 'Complete' : 'Incomplete'}</p>
          <p><strong>Financial clearance:</strong> ${audit.financialClearanceGranted ? 'Clear' : 'Not clear'}</p>
          <p><strong>Directory:</strong> ${audit.directoryActive ? 'Active' : 'Inactive'}</p>
          ${blockers.length ? `<p><strong>Blockers:</strong> ${escapeHtml(blockers.join('; '))}</p>` : ''}
          ${audit.missingRequiredCourses.length
            ? `<ul>${audit.missingRequiredCourses.map(course => `<li>${escapeHtml(course.courseCode)} — ${escapeHtml(course.courseName)}</li>`).join('')}</ul>`
            : ''}
          <button type="button" class="btn-primary" data-graduate-enrollment="${index}" ${eligible ? '' : 'disabled'}>
            ${eligible ? 'Approve graduation' : 'Not eligible'}
          </button>
        </section>`;
    };

    openModal(`
      <span class="modal-close" onclick="closeModal()">&times;</span>
      <h2>Graduation Clearance</h2>
      <p>Graduation is available only when all required approved courses are complete, the programme directory is active, and financial clearance is granted.</p>
      ${audits.map(renderAudit).join('')}
    `);
    document.querySelectorAll('[data-graduate-enrollment]').forEach(button => {
      button.addEventListener('click', async () => {
        const index = Number(button.dataset.graduateEnrollment);
        const { enrollment } = audits[index];
        if (!confirm(`Approve graduation for ${enrollment.programName} (${enrollment.academicYearName})?`)) return;
        try {
          await apiFetch(`/admin/students/${studentId}/status`, {
            method: 'POST',
            body: JSON.stringify({
              programId: enrollment.programId,
              academicYearId: enrollment.academicYearId,
              status: 'GRADUATED',
              reason: 'Graduation approved after successful academic and financial clearance audit.',
            }),
          });
          closeModal();
          await loadStudents();
        } catch (err) {
          alert(err.message || 'Unable to approve graduation.');
        }
      });
    });
  } catch (err) {
    alert(err.message || 'Unable to load graduation clearance.');
  }
}

async function openStudentLifecycleModal(studentId) {
  try {
    const [enrollments, history] = await Promise.all([
      apiGet(`/admin/students/${studentId}/programs`),
      apiGet(`/admin/students/${studentId}/status-history`),
    ]);
    if (!enrollments.length) {
      openModal(`
        <span class="modal-close" onclick="closeModal()">&times;</span>
        <h2>Student Status History</h2>
        <p>This student has no programme enrollments available for a status change.</p>
        ${renderStudentStatusHistory(history)}
      `);
      return;
    }

    openModal(`
      <span class="modal-close" onclick="closeModal()">&times;</span>
      <h2>Student Programme Status</h2>
      <form id="studentLifecycleForm">
        <div class="modal-field">
          <label for="lifecycleEnrollment">Programme enrollment *</label>
          <select id="lifecycleEnrollment" required>
            ${enrollments.map((enrollment, index) => `
              <option value="${index}">
                ${escapeHtml(enrollment.programName)} — ${escapeHtml(enrollment.academicYearName)}
                (${escapeHtml(enrollment.status)})
              </option>`).join('')}
          </select>
        </div>
        <div class="modal-field">
          <label for="lifecycleStatus">New status *</label>
          <select id="lifecycleStatus" required></select>
        </div>
        <div class="modal-field">
          <label for="lifecycleReason">Reason for decision *</label>
          <textarea id="lifecycleReason" rows="3" maxlength="2000" required></textarea>
        </div>
        <p id="lifecycleMessage" class="muted" role="status"></p>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="submit" class="btn-primary">Update Status</button>
        </div>
      </form>
      ${renderStudentStatusHistory(history)}
    `);

    const enrollmentSelect = document.getElementById('lifecycleEnrollment');
    const statusSelect = document.getElementById('lifecycleStatus');
    const message = document.getElementById('lifecycleMessage');
    const updateAvailableStatuses = () => {
      const selected = enrollments[Number(enrollmentSelect.value)];
      const allowed = studentLifecycleTransitions[selected.status] || [];
      statusSelect.innerHTML = allowed.length
        ? allowed.map(status => `<option value="${status}">${status}</option>`).join('')
        : '<option value="">No status changes allowed</option>';
      statusSelect.disabled = allowed.length === 0;
      message.textContent = allowed.length
        ? `Current status: ${selected.status}`
        : `${selected.status} is a final status; no further status changes are available.`;
      document.querySelector('#studentLifecycleForm button[type="submit"]').disabled = allowed.length === 0;
    };
    enrollmentSelect.addEventListener('change', updateAvailableStatuses);
    updateAvailableStatuses();

    document.getElementById('studentLifecycleForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const selected = enrollments[Number(enrollmentSelect.value)];
      try {
        await apiFetch(`/admin/students/${studentId}/status`, {
          method: 'POST',
          body: JSON.stringify({
            programId: selected.programId,
            academicYearId: selected.academicYearId,
            status: statusSelect.value,
            reason: document.getElementById('lifecycleReason').value,
          }),
        });
        closeModal();
        await loadStudents();
      } catch (err) {
        alert(err.message || 'Unable to update student status.');
      }
    });
  } catch (err) {
    alert(err.message || 'Unable to load student programme enrollments.');
  }
}

function renderStudentStatusHistory(history) {
  return `
    <h3>Status history</h3>
    <div class="roster-wrap">
      <table class="admin-table">
        <thead><tr><th>Changed</th><th>Status</th><th>Programme / Year</th><th>Changed by</th><th>Reason</th></tr></thead>
        <tbody>
          ${history.length ? history.map(item => `
            <tr>
              <td>${escapeHtml(item.changedAt || '—')}</td>
              <td>${escapeHtml(item.status)}</td>
              <td>${item.programName
                ? `${escapeHtml(item.programName)} / ${escapeHtml(item.academicYearName || '—')}`
                : 'Student-wide / legacy record'}</td>
              <td>${escapeHtml(item.changedBy || '—')}</td>
              <td>${escapeHtml(item.reason || '—')}</td>
            </tr>`).join('')
            : '<tr><td colspan="5">No status history is recorded.</td></tr>'}
        </tbody>
      </table>
    </div>
    <p class="muted">Showing the latest ${history.length} status changes (up to 100).</p>`;
}

async function openStudentTransferModal(studentId) {
  try {
    const [enrollments, options] = await Promise.all([
      apiGet(`/admin/students/${studentId}/programs`),
      apiGet(`/admin/students/${studentId}/transfer-options`),
    ]);
    const activeEnrollments = enrollments.filter(enrollment => enrollment.status === 'ACTIVE');
    if (!activeEnrollments.length) {
      alert('Only a student with an active programme enrollment can be transferred.');
      return;
    }
    if (!options.programs.length || !options.academicYears.length) {
      alert('Configure an active destination programme and academic year before transferring a student.');
      return;
    }

    openModal(`
      <span class="modal-close" onclick="closeModal()">&times;</span>
      <h2>Transfer Student Programme</h2>
      <form id="studentTransferForm">
        <div class="modal-field">
          <label for="transferSourceEnrollment">Current enrollment *</label>
          <select id="transferSourceEnrollment" required>
            ${activeEnrollments.map((enrollment, index) => `
              <option value="${index}">
                ${escapeHtml(enrollment.programName)} — ${escapeHtml(enrollment.academicYearName)}
              </option>`).join('')}
          </select>
        </div>
        <div class="modal-field">
          <label for="transferDestinationProgram">Destination programme *</label>
          <select id="transferDestinationProgram" required></select>
        </div>
        <div class="modal-field">
          <label for="transferDestinationYear">Destination academic year *</label>
          <select id="transferDestinationYear" required>
            ${options.academicYears.map(year => `
              <option value="${year.id}">
                ${escapeHtml(year.name)} (${escapeHtml(year.startDate)} – ${escapeHtml(year.endDate)})
              </option>`).join('')}
          </select>
        </div>
        <div class="modal-field">
          <label for="transferReason">Transfer reason *</label>
          <textarea id="transferReason" rows="3" maxlength="1800" required></textarea>
        </div>
        <p class="muted">This closes the source enrollment as TRANSFERRED and creates an ACTIVE enrollment in the selected destination.</p>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="submit" class="btn-primary">Complete Transfer</button>
        </div>
      </form>
    `);

    const sourceSelect = document.getElementById('transferSourceEnrollment');
    const destinationSelect = document.getElementById('transferDestinationProgram');
    const updateDestinationPrograms = () => {
      const source = activeEnrollments[Number(sourceSelect.value)];
      const destinations = options.programs.filter(program => program.id !== source.programId);
      destinationSelect.innerHTML = destinations.length
        ? destinations.map(program => `
          <option value="${program.id}">
            ${escapeHtml(program.code)} · ${escapeHtml(program.name)} — ${escapeHtml(program.departmentName)}
          </option>`).join('')
        : '<option value="">No other active programmes available</option>';
      destinationSelect.disabled = destinations.length === 0;
      document.querySelector('#studentTransferForm button[type="submit"]').disabled = destinations.length === 0;
    };
    sourceSelect.addEventListener('change', updateDestinationPrograms);
    updateDestinationPrograms();

    document.getElementById('studentTransferForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const source = activeEnrollments[Number(sourceSelect.value)];
      try {
        await apiFetch(`/admin/students/${studentId}/transfer`, {
          method: 'POST',
          body: JSON.stringify({
            sourceProgramId: source.programId,
            sourceAcademicYearId: source.academicYearId,
            destinationProgramId: Number(destinationSelect.value),
            destinationAcademicYearId: Number(document.getElementById('transferDestinationYear').value),
            reason: document.getElementById('transferReason').value.trim(),
          }),
        });
        closeModal();
        await loadStudents();
      } catch (err) {
        alert(err.message || 'Unable to transfer student programme.');
      }
    });
  } catch (err) {
    alert(err.message || 'Unable to load student transfer options.');
  }
}

function confirmDeactivateStudent(id, name) {
  if (!confirm(`Deactivate ${name}? Their account will no longer be able to sign in.`)) return;
  apiFetch(`/admin/students/${id}`, { method: 'DELETE' })
    .then(() => loadStudents())
    .catch(err => alert(err.message));
}

async function restoreStudent(id) {
  try { await apiFetch(`/admin/students/${id}/restore`, { method: 'POST' }); await loadStudents(); }
  catch (err) { alert(err.message || 'Unable to restore student.'); }
}

async function viewStudentCgpa(id) {
  try {
    const result = await apiGet(`/admin/students/${id}/cgpa`);
    alert(`${result.fullName}\nCGPA: ${result.cgpa == null ? 'Not calculated' : Number(result.cgpa).toFixed(2)}`);
  } catch (err) { alert(err.message || 'Unable to load CGPA.'); }
}

async function recalculateStudentCgpa(id) {
  if (!confirm(`Recalculate CGPA for student #${id}?`)) return;
  try {
    await apiFetch(`/admin/students/${id}/calculate-cgpa`, { method: 'POST' });
    await viewStudentCgpa(id);
    loadStudents();
  } catch (err) { alert(err.message || 'Unable to recalculate CGPA.'); }
}

// ===== COURSES =====
async function loadCourses(page = 0) {
  const grid = document.querySelector('#coursesModule .courses-grid');
  if (!grid) return;
  grid.innerHTML = `<p style="color:var(--text-secondary);">Loading…</p>`;

  try {
    const [coursesPage, teachersPage, categories, courseUnits] = await Promise.all([
      apiGet(`/admin/courses?page=${page}&size=12&sort=id`),
      apiGet('/admin/teachers?size=200'),
      apiGet('/admin/course-categories'),
      apiGet('/admin/course-units'),
    ]);
    currentTeachers = teachersPage.content || [];
    window._courseCategories = categories.filter(category => category.active);
    const categoryFilter = document.querySelector('[data-admin-filter="course-category"]');
    if (categoryFilter) {
      const selectedCategory = categoryFilter.value;
      categoryFilter.innerHTML = '<option value="">All Categories</option>'
        + window._courseCategories.map(category =>
          `<option value="${escapeHtml(category.name.toLowerCase())}">${escapeHtml(category.name)}</option>`
        ).join('');
      categoryFilter.value = selectedCategory;
    }
    const courses = coursesPage.content || [];

    grid.innerHTML = courses.length ? courses.map(c => `
      <div class="course-card" data-category="${escapeHtml((c.category || 'OTHER').toLowerCase())}">
        <div class="course-header">
          <h3>${escapeHtml(c.courseCode || '—')} · ${escapeHtml(c.name)}</h3>
        </div>
        <div class="course-body">
          <p><strong>Category:</strong> ${escapeHtml(c.category || 'OTHER')}</p>
          <p><strong>Instructor:</strong> ${escapeHtml(c.teacherFullName || 'Unassigned')}</p>
          <p><strong>Course Units:</strong> ${c.credits ?? '—'}</p>
          <p><strong>Requirement:</strong> ${c.core ? 'Core course' : 'Elective / not designated core'}</p>
          <p><strong>Status:</strong> <span class="badge active">Active</span></p>
        </div>
        <div class="course-actions"><button class="btn-small edit" onclick="editCourse(${c.id})">Edit</button><button class="btn-small" onclick="viewCourseGrades(${c.id})">Grades</button><button class="btn-small" onclick="configureCourseAssessments(${c.id})">Assessment structure</button></div>
      </div>`).join('') : `<p style="color:var(--text-secondary);">No courses created yet.</p>`;
    window._coursesCache = courses;
    window._courseUnitsCache = courseUnits;
    renderCourseUnits(courseUnits);
    // Credits and enrolled-student-count from the original static cards are omitted -
    // neither is tracked/exposed anywhere in the backend for course listings.

    renderPagination('coursesModule', coursesPage, loadCourses);
  } catch (err) {
    grid.innerHTML = `<p style="color:var(--danger);">${escapeHtml(err.message)}</p>`;
  }
}

function assessmentComponentRow(component = {}) {
  const types = ['COURSEWORK', 'FINAL_PAPER', 'OTHER'];
  return `<div class="assessment-component-row" style="display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:.5rem;margin:.75rem 0;">
    <label>Code<input name="componentCode" maxlength="40" value="${escapeHtml(component.componentCode || '')}" required></label>
    <label>Name<input name="displayName" maxlength="120" value="${escapeHtml(component.displayName || '')}" required></label>
    <label>Type<select name="componentType">${types.map(type => `<option value="${type}" ${component.componentType === type ? 'selected' : ''}>${type.replace('_', ' ')}</option>`).join('')}</select></label>
    <label>Weight (%)<input name="weightPercentage" type="number" min="0.01" max="100" step="0.01" value="${escapeHtml(component.weightPercentage ?? '')}" required></label>
    <label>Maximum score<input name="maxScore" type="number" min="0.01" step="0.01" value="${escapeHtml(component.maxScore ?? '100')}" required></label>
    <button class="btn-secondary" type="button" data-remove-assessment-component>Remove</button>
  </div>`;
}

async function configureCourseAssessments(courseId) {
  let components;
  try {
    components = await apiGet(`/teacher/courses/${courseId}/assessment-components`);
  } catch (error) {
    alert(error.message || 'Unable to load the course assessment structure.');
    return;
  }
  openModal(`<button type="button" class="modal-close" onclick="closeModal()" aria-label="Close">&times;</button>
    <h2>Course assessment structure</h2>
    <p>Define course-specific components. Weights must total exactly 100%. Once any marks are submitted, the structure is locked to preserve result history.</p>
    <form id="courseAssessmentStructureForm">
      <div id="assessmentComponentRows">${(components.length ? components : [{}]).map(assessmentComponentRow).join('')}</div>
      <button id="addAssessmentComponentRow" type="button" class="btn-secondary">Add component</button>
      <p id="assessmentStructureStatus" role="status" aria-live="polite"></p>
      <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button type="submit" class="btn-primary">Save assessment structure</button></div>
    </form>`);
  const form = document.getElementById('courseAssessmentStructureForm');
  document.getElementById('addAssessmentComponentRow').addEventListener('click', () => {
    document.getElementById('assessmentComponentRows').insertAdjacentHTML('beforeend', assessmentComponentRow());
  });
  form.addEventListener('click', event => {
    if (event.target.matches('[data-remove-assessment-component]')) {
      const rows = form.querySelectorAll('.assessment-component-row');
      if (rows.length === 1) return;
      event.target.closest('.assessment-component-row').remove();
    }
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const status = document.getElementById('assessmentStructureStatus');
    const rows = [...form.querySelectorAll('.assessment-component-row')];
    const payload = rows.map(row => ({
      componentCode: row.querySelector('[name="componentCode"]').value.trim(),
      displayName: row.querySelector('[name="displayName"]').value.trim(),
      componentType: row.querySelector('[name="componentType"]').value,
      weightPercentage: Number(row.querySelector('[name="weightPercentage"]').value),
      maxScore: Number(row.querySelector('[name="maxScore"]').value),
    }));
    const weightTotal = payload.reduce((sum, component) => sum + component.weightPercentage, 0);
    if (Math.abs(weightTotal - 100) > 0.000001) {
      status.textContent = `Assessment component weights total ${weightTotal}%. They must total exactly 100%.`;
      status.className = 'teacher-status error';
      return;
    }
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    try {
      await apiFetch(`/admin/courses/${courseId}/assessment-components`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      });
      status.textContent = 'Assessment structure saved.';
      status.className = 'teacher-status success';
      await new Promise(resolve => setTimeout(resolve, 500));
      closeModal();
    } catch (error) {
      status.textContent = error.message || 'Unable to save the assessment structure.';
      status.className = 'teacher-status error';
      button.disabled = false;
    }
  });
}

function renderCourseUnits(units) {
  const tbody = document.querySelector('#courseUnitsTable tbody');
  if (!tbody) return;
  tbody.innerHTML = units.length ? units.map(unit => `<tr>
    <td>${escapeHtml(unit.unitCode)} · ${escapeHtml(unit.name)}</td>
    <td>${escapeHtml(unit.courseCode)} · ${escapeHtml(unit.courseName)}</td>
    <td>${escapeHtml(unit.category)}</td>
    <td>${escapeHtml(unit.teacherName)}</td>
  </tr>`).join('') : '<tr><td colspan="4">No course units configured.</td></tr>';
}

async function loadCourseManagementOptions() {
  const [courses, teachers] = await Promise.all([
    fetchAllAdminPages('/admin/courses'),
    fetchAllAdminPages('/admin/teachers'),
  ]);
  currentTeachers = teachers;
  window._coursesCache = courses;
  return { courses, teachers };
}

async function loadActiveCourseCategories() {
  const categories = await apiGet('/admin/course-categories');
  window._courseCategories = categories.filter(category => category.active);
  return window._courseCategories;
}

async function openCourseUnitForm() {
  openModal('<h2>Add Course Unit</h2><p role="status">Loading courses and instructors…</p>');
  let courses;
  let teachers;
  try {
    ({ courses, teachers } = await loadCourseManagementOptions());
  } catch (error) {
    openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>Course units unavailable</h2><p role="alert">${escapeHtml(error.message || 'Could not load courses and instructors. Please try again.')}</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button><button type="button" class="btn-primary" onclick="openCourseUnitForm()">Try again</button></div>`);
    return;
  }

  if (!courses.length) {
    openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>No courses to assign</h2><p>Create a course before adding its units. Once created, it will be available to all portal users in the course catalogue.</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button type="button" class="btn-primary" id="createCourseBeforeUnit">Create a course</button></div>`);
    document.getElementById('createCourseBeforeUnit').addEventListener('click', () => {
      openCourseForm();
    });
    return;
  }

  if (!teachers.length) {
    openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>No instructors available</h2><p>Add an instructor in Staff before creating a course unit.</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button type="button" class="btn-primary" id="openStaffFromCourseUnit">Open Staff</button></div>`);
    document.getElementById('openStaffFromCourseUnit').addEventListener('click', () => {
      closeModal();
      switchModule('staff');
    });
    return;
  }

  openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>Add Course Unit</h2>
    <form id="courseUnitForm">
      <div class="modal-field"><label for="courseUnitCode">Unit code *</label><input id="courseUnitCode" name="unitCode" maxlength="30" required></div>
      <div class="modal-field"><label for="courseUnitName">Unit name *</label><input id="courseUnitName" name="name" required></div>
      <div class="modal-field"><label for="courseUnitCourse">Course *</label><select id="courseUnitCourse" name="courseId" required><option value="">Choose a course…</option>${courses.map(course => `<option value="${course.id}">${escapeHtml(course.courseCode || '—')} · ${escapeHtml(course.name)}</option>`).join('')}</select></div>
      <div class="modal-field"><label for="courseUnitTeacher">Instructor *</label><select id="courseUnitTeacher" name="teacherId" required><option value="">Choose an instructor…</option>${teachers.map(teacher => `<option value="${teacher.id}">${escapeHtml(teacher.fullName)}</option>`).join('')}</select></div>
      <p id="courseUnitFormMessage" role="alert" hidden></p>
      <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button class="btn-primary" type="submit">Create Unit</button></div>
    </form>`);
  document.getElementById('courseUnitForm').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    const message = document.getElementById('courseUnitFormMessage');
    const submitButton = form.querySelector('[type="submit"]');
    if (!data.courseId || !data.teacherId) {
      message.textContent = 'Choose both a course and an instructor.';
      message.hidden = false;
      return;
    }
    submitButton.disabled = true;
    try {
      await apiFetch('/admin/course-units', { method: 'POST', body: JSON.stringify({
        unitCode: data.unitCode, name: data.name, courseId: Number(data.courseId), teacherId: Number(data.teacherId),
      }) });
      closeModal();
      await loadCourses();
    } catch (err) {
      message.textContent = err.message || 'Unable to create course unit. Please check the details and try again.';
      message.hidden = false;
      submitButton.disabled = false;
    }
  });
}

async function openCourseForm() {
  openModal('<h2>Add New Course</h2><p role="status">Loading instructors and categories…</p>');
  try {
    await Promise.all([loadCourseManagementOptions(), loadActiveCourseCategories()]);
  } catch (error) {
    openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>Course setup unavailable</h2><p role="alert">${escapeHtml(error.message || 'Could not load instructors and categories. Please try again.')}</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button><button type="button" class="btn-primary" onclick="openCourseForm()">Try again</button></div>`);
    return;
  }

  if (!currentTeachers.length || !window._courseCategories.length) {
    const missing = [
      !currentTeachers.length ? 'instructor' : '',
      !window._courseCategories.length ? 'active course category' : '',
    ].filter(Boolean);
    const setupActions = [
      !currentTeachers.length ? '<button type="button" class="btn-primary" id="openCourseStaff">Open Staff</button>' : '',
      !window._courseCategories.length ? '<button type="button" class="btn-primary" id="openCourseCategories">Open Course Categories</button>' : '',
    ].filter(Boolean).join('');
    openModal(`<span class="modal-close" onclick="closeModal()" aria-label="Close">&times;</span><h2>Course setup needs more information</h2><p>Add an ${escapeHtml(missing.join(' and '))} before creating a course.</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>${setupActions}</div>`);
    document.getElementById('openCourseStaff')?.addEventListener('click', () => {
      closeModal();
      switchModule('staff');
    });
    document.getElementById('openCourseCategories')?.addEventListener('click', () => {
      closeModal();
      switchModule('course-categories');
    });
    return;
  }

  const teacherOptions = currentTeachers.map(t => `<option value="${t.id}">${escapeHtml(t.fullName)}</option>`).join('');
  const categoryOptions = (window._courseCategories || []).map(category => `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join('');
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Add New Course</h2>
    <form id="createCourseForm">
      <div class="modal-field"><label>Course Name *</label><input name="name" required></div>
      <div class="modal-row"><div class="modal-field"><label>Course Code *</label><input name="courseCode" placeholder="CSC201" required></div><div class="modal-field"><label>Category *</label><select name="categoryId" required><option value="">Choose a category…</option>${categoryOptions}</select></div></div>
      <div class="modal-row"><div class="modal-field"><label>Semester</label><input name="semester"></div><div class="modal-field"><label>Course Units</label><input name="credits" type="number" min="1" max="30" value="3" required></div></div>
      <div class="modal-field"><label><input name="core" type="checkbox" value="true"> Core course unit</label></div>
      <div class="modal-field">
        <label>Instructor *</label>
        <select name="teacherId" required>
          <option value="">Choose a teacher…</option>
          ${teacherOptions}
        </select>
      </div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary">Create Course</button>
      </div>
    </form>
  `);
  document.getElementById('createCourseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    if (!fd.get('teacherId') || !fd.get('categoryId')) { alert('Choose both an instructor and a course category.'); return; }
    try {
      await apiFetch('/admin/courses', {
        method: 'POST',
        body: JSON.stringify({ name: fd.get('name'), courseCode: fd.get('courseCode'), categoryId: Number(fd.get('categoryId')), teacherId: Number(fd.get('teacherId')), semester: fd.get('semester'), credits: Number(fd.get('credits') || 3), core: fd.get('core') === 'true' }),
      });
      closeModal();
      loadCourses();
    } catch (err) { alert(err.message); }
  });
}

async function loadCourseCategories() {
  const tbody = document.querySelector('#course-categoriesModule table tbody');
  if (!tbody) return;
  try {
    const categories = await apiGet('/admin/course-categories');
    window._allCourseCategories = categories;
    window._courseCategories = categories.filter(category => category.active);
    tbody.innerHTML = categories.length ? categories.map(category => `<tr>
      <td>${escapeHtml(category.code)}</td><td>${escapeHtml(category.name)}</td>
      <td><span class="badge ${category.active ? 'active' : 'inactive'}">${category.active ? 'Active' : 'Inactive'}</span></td>
      <td><button class="btn-small edit" onclick="openEditCourseCategoryForm(${category.id})">Edit</button><button class="btn-small" onclick="toggleCourseCategory(${category.id}, ${!category.active})">${category.active ? 'Deactivate' : 'Activate'}</button><button class="btn-small delete" onclick="deleteCourseCategory(${category.id})">Delete</button></td>
    </tr>`).join('') : '<tr><td colspan="4">No categories configured.</td></tr>';
  } catch (err) { tbody.innerHTML = `<tr><td colspan="4">${escapeHtml(err.message)}</td></tr>`; }
}

function openCourseCategoryForm() {
  openModal(`<span class="modal-close" onclick="closeModal()">&times;</span><h2>Add Course Category</h2>
    <form id="courseCategoryForm"><div class="modal-field"><label>Code *</label><input name="code" placeholder="SCIENCES" required maxlength="30"></div>
    <div class="modal-field"><label>Name *</label><input name="name" placeholder="Sciences" required maxlength="100"></div>
    <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button class="btn-primary">Create Category</button></div></form>`);
  document.getElementById('courseCategoryForm').addEventListener('submit', async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.target));
    try { await apiFetch('/admin/course-categories', { method: 'POST', body: JSON.stringify(data) }); closeModal(); await loadCourseCategories(); }
    catch (err) { alert(err.message); }
  });
}

function openEditCourseCategoryForm(id) {
  const category = (window._allCourseCategories || []).find(item => item.id === id);
  if (!category) return;
  openModal(`<span class="modal-close" onclick="closeModal()">&times;</span><h2>Edit Course Category</h2>
    <form id="editCourseCategoryForm"><div class="modal-field"><label>Code *</label><input name="code" value="${escapeHtml(category.code)}" required maxlength="30"></div>
    <div class="modal-field"><label>Name *</label><input name="name" value="${escapeHtml(category.name)}" required maxlength="100"></div>
    <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button class="btn-primary">Save Changes</button></div></form>`);
  document.getElementById('editCourseCategoryForm').addEventListener('submit', async event => {
    event.preventDefault();
    try { await apiFetch(`/admin/course-categories/${id}`, { method: 'PUT', body: JSON.stringify(Object.fromEntries(new FormData(event.target))) }); closeModal(); await loadCourseCategories(); }
    catch (err) { alert(err.message); }
  });
}

async function deleteCourseCategory(id) {
  if (!confirm('Delete this course category?')) return;
  try { await apiFetch(`/admin/course-categories/${id}`, { method: 'DELETE' }); await loadCourseCategories(); }
  catch (err) { alert(err.message || 'Category cannot be deleted.'); }
}

async function toggleCourseCategory(id, active) {
  try { await apiFetch(`/admin/course-categories/${id}/active?active=${active}`, { method: 'POST' }); await loadCourseCategories(); }
  catch (err) { alert(err.message); }
}

async function editCourse(courseId) {
  const course = (window._coursesCache || []).find(item => item.id === courseId);
  if (!course) return;
  const teacherOptions = currentTeachers.map(t => `<option value="${t.id}" ${t.fullName === course.teacherFullName ? 'selected' : ''}>${escapeHtml(t.fullName)}</option>`).join('');
  const categoryOptions = (window._courseCategories || []).map(category => `<option value="${category.id}" ${category.name === course.category ? 'selected' : ''}>${escapeHtml(category.name)}</option>`).join('');
  openModal(`<span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Edit Course</h2>
    <form id="editCourseForm">
      <div class="modal-field"><label>Course Name *</label><input name="name" value="${escapeHtml(course.name)}" required></div>
      <div class="modal-row"><div class="modal-field"><label>Course Code *</label><input name="courseCode" value="${escapeHtml(course.courseCode || '')}" required></div><div class="modal-field"><label>Category *</label><select name="categoryId" required>${categoryOptions}</select></div></div>
      <div class="modal-row"><div class="modal-field"><label>Semester</label><input name="semester" value="${escapeHtml(course.semester || '')}"></div><div class="modal-field"><label>Course Units</label><input name="credits" type="number" min="1" max="30" value="${course.credits ?? 3}" required></div></div>
      <div class="modal-field"><label><input name="core" type="checkbox" value="true" ${course.core ? 'checked' : ''}> Core course unit</label></div>
      <div class="modal-field"><label>Instructor *</label><select name="teacherId" required>${teacherOptions}</select></div>
      <div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button><button type="submit" class="btn-primary">Save Changes</button></div>
    </form>`);
  document.getElementById('editCourseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await apiFetch(`/admin/courses/${courseId}`, { method: 'PUT', body: JSON.stringify({ name: fd.get('name'), courseCode: fd.get('courseCode'), categoryId: Number(fd.get('categoryId')), teacherId: Number(fd.get('teacherId')), semester: fd.get('semester'), credits: Number(fd.get('credits') || 3), core: fd.get('core') === 'true' }) });
      closeModal();
      loadCourses();
    } catch (err) { alert(err.message); }
  });
}

async function viewCourseGrades(courseName) {
  try {
    const grades = await apiGet('/admin/grades');
    const matching = grades.filter(grade => grade.courseName === courseName || grade.courseId === Number(courseName));
    downloadCsv(`grades-${courseName}.csv`, matching);
  } catch (err) { alert(err.message || 'Unable to load course grades.'); }
}

// ===== STAFF (TEACHERS) =====
async function loadStaff(page = 0) {
  const tbody = document.querySelector('#staffModule table tbody');
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;">Loading…</td></tr>`;

  try {
    const data = await apiGet(`/admin/teachers?page=${page}&size=20&sort=id`);
    const teachers = data.content || [];
    window._teachersCache = teachers;

    tbody.innerHTML = teachers.length ? teachers.map(t => `
      <tr>
        <td>#${t.id}</td>
        <td>${escapeHtml(t.fullName)}</td>
        <td>${escapeHtml(t.qualification || '—')}</td>
        <td>${escapeHtml(t.departmentName || (t.department ? `${t.department} · needs assignment` : 'Needs assignment'))}</td>
        <td>${escapeHtml(t.email || '—')}</td>
        <td><span class="badge ${t.active ? 'active' : 'inactive'}">${t.active ? 'Active' : 'Deactivated'}</span></td>
        <td>
          <button class="btn-small edit" onclick="openEditStaffModal(${t.id})">Edit</button>
          ${t.active ? `<button class="btn-small delete" onclick="confirmDeactivateStaff(${t.id}, '${escapeHtml(t.fullName)}')">Deactivate</button>` : `<button class="btn-small" onclick="restoreTeacher(${t.id})">Restore</button>`}
        </td>
      </tr>`).join('') : `<tr><td colspan="7" style="text-align:center;color:var(--text-secondary);">No staff hired yet.</td></tr>`;

    renderPagination('staffModule', data, loadStaff);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;color:var(--danger);">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function loadHrManagement() {
  const staffBody = document.querySelector('#hrStaffTable tbody');
  const leaveBody = document.querySelector('#hrPendingLeaveTable tbody');
  if (!staffBody || !leaveBody) return;
  staffBody.innerHTML = '<tr><td colspan="7">Loading HR staff records…</td></tr>';
  leaveBody.innerHTML = '<tr><td colspan="4">Loading pending leave requests…</td></tr>';

  try {
    const [staff, pendingLeaves, users] = await Promise.all([
      apiGet('/hr/staff'),
      apiGet('/hr/leave/pending'),
      apiGet('/admin/users'),
    ]);
    const assignedUserIds = new Set(staff.map(item => Number(item.user?.id)).filter(Number.isFinite));
    const eligibleUsers = users.filter(user => {
      const active = user.isActive ?? user.active;
      return active && ['ADMIN', 'TEACHER'].includes(String(user.role).toUpperCase())
        && !assignedUserIds.has(Number(user.id));
    });
    const accountSelect = document.getElementById('hrStaffUser');
    if (accountSelect) {
      accountSelect.innerHTML = '<option value="">Select an active admin/teacher account</option>'
        + eligibleUsers.map(user => `<option value="${Number(user.id)}">${escapeHtml(user.username)} · ${escapeHtml(user.role)} · ${escapeHtml(user.email || 'no email')}</option>`).join('');
    }

    staffBody.innerHTML = staff.length ? staff.map(item => `
      <tr><td>${escapeHtml(item.employeeNumber)}</td><td>${escapeHtml(item.fullName)}</td>
      <td>${escapeHtml(item.title)}</td><td>${escapeHtml(item.user?.username || '—')}</td>
      <td>${escapeHtml(item.departmentId ?? '—')}</td><td>${escapeHtml(item.hireDate || '—')}</td>
      <td>${item.active ? 'Active' : 'Inactive'}</td></tr>`).join('')
      : '<tr><td colspan="7">No HR staff records are configured.</td></tr>';

    leaveBody.innerHTML = pendingLeaves.length ? pendingLeaves.map(item => `
      <tr><td>${escapeHtml(item.staff?.fullName || item.staff?.user?.username || '—')}</td>
      <td>${escapeHtml(item.startDate)} – ${escapeHtml(item.endDate)}</td>
      <td>${escapeHtml(item.reason)}</td><td>
      <button class="btn-small edit" type="button" onclick="reviewHrLeave(${Number(item.id)}, 'APPROVED')">Approve</button>
      <button class="btn-small delete" type="button" onclick="reviewHrLeave(${Number(item.id)}, 'REJECTED')">Reject</button>
      </td></tr>`).join('')
      : '<tr><td colspan="4">No pending leave requests.</td></tr>';
    setAdminStatus('hrStaffStatus', '', '');
    setAdminStatus('hrLeaveStatus', '', '');
    await loadAdminLeaveRequests();
  } catch (err) {
    staffBody.innerHTML = `<tr><td colspan="7">${escapeHtml(err.message || 'Unable to load HR staff records.')}</td></tr>`;
    leaveBody.innerHTML = `<tr><td colspan="4">${escapeHtml(err.message || 'Unable to load pending leave requests.')}</td></tr>`;
    setAdminStatus('hrStaffStatus', err.message || 'Unable to load HR management data.', 'error');
  }
}

async function createHrStaffRecord(event) {
  event.preventDefault();
  const departmentValue = document.getElementById('hrStaffDepartment').value.trim();
  try {
    await apiFetch('/hr/staff', { method: 'POST', body: JSON.stringify({
      userId: Number(document.getElementById('hrStaffUser').value),
      employeeNumber: document.getElementById('hrStaffNumber').value.trim(),
      fullName: document.getElementById('hrStaffName').value.trim(),
      title: document.getElementById('hrStaffTitle').value.trim(),
      departmentId: departmentValue ? Number(departmentValue) : null,
      hireDate: document.getElementById('hrStaffHireDate').value,
    }) });
    event.target.reset();
    setAdminStatus('hrStaffStatus', 'HR staff profile created. The employee can now use staff leave services.', 'success');
    await loadHrManagement();
  } catch (err) {
    setAdminStatus('hrStaffStatus', err.message || 'Unable to create the HR staff profile.', 'error');
  }
}

async function loadAdminLeaveRequests() {
  const list = document.getElementById('adminLeaveList');
  if (!list) return;
  try {
    const requests = await apiGet('/hr/leave/mine');
    list.innerHTML = requests.length ? requests.map(item => `
      <div class="teacher-request-item"><strong>${escapeHtml(item.startDate)} – ${escapeHtml(item.endDate)}</strong>
      <span>${escapeHtml(item.status)} · ${escapeHtml(item.reason)}</span></div>`).join('')
      : '<p>No leave requests submitted yet.</p>';
  } catch (err) {
    list.textContent = err.message || 'Unable to load your leave requests.';
  }
}

async function submitAdminLeaveRequest(event) {
  event.preventDefault();
  try {
    await apiFetch('/hr/leave', { method: 'POST', body: JSON.stringify({
      startDate: document.getElementById('adminLeaveStart').value,
      endDate: document.getElementById('adminLeaveEnd').value,
      reason: document.getElementById('adminLeaveReason').value.trim(),
    }) });
    event.target.reset();
    setAdminStatus('adminLeaveStatus', 'Leave request submitted.', 'success');
    await Promise.all([loadAdminLeaveRequests(), loadHrManagement()]);
  } catch (err) {
    setAdminStatus('adminLeaveStatus', err.message || 'Unable to submit your leave request.', 'error');
  }
}

async function reviewHrLeave(id, status) {
  try {
    await apiFetch(`/hr/leave/${id}/review`, { method: 'POST', body: JSON.stringify({ status }) });
    setAdminStatus('hrLeaveStatus', `Leave request ${status.toLowerCase()}.`, 'success');
    await loadHrManagement();
  } catch (err) {
    setAdminStatus('hrLeaveStatus', err.message || 'Unable to review leave request.', 'error');
  }
}

function teacherDepartmentOptions(departments, selectedId = '') {
  return `<option value="">Select a home department</option>${departments.map(department => {
    const location = [department.campusName, department.facultyName, department.name].filter(Boolean).join(' · ');
    const label = department.inactive ? `${location} (inactive; choose an active department)` : location;
    return `<option value="${department.id}" ${String(department.id) === String(selectedId) ? 'selected' : ''}>${escapeHtml(label)}</option>`;
  }).join('')}`;
}

async function openStaffForm() {
  openModal('<h2>Add Staff Member</h2><p role="status">Loading active departments…</p>');
  let departments;
  try {
    departments = await apiGet('/admin/institution/departments/active');
  } catch (err) {
    openModal(`<h2>Departments unavailable</h2><p role="alert">${escapeHtml(err.message || 'Unable to load active departments.')}</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button></div>`);
    return;
  }
  if (!departments.length) {
    openModal('<h2>No active departments</h2><p>Configure an active campus, faculty, and department in Academic Operations before hiring a teacher.</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button></div>');
    return;
  }
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Add Staff Member</h2>
    <form id="hireStaffForm">
      <div class="modal-field"><label>Full Name *</label><input name="fullName" required></div>
      <div class="modal-row">
        <div class="modal-field"><label>Username *</label><input name="username" required minlength="3"></div>
        <div class="modal-field"><label>Password *</label><input name="password" type="password" required minlength="6"></div>
      </div>
      <div class="modal-row">
        <div class="modal-field"><label>Home Department *</label><select name="departmentId" required>${teacherDepartmentOptions(departments)}</select></div>
        <div class="modal-field"><label>Years of Experience</label><input name="yearsOfExperience" type="number" min="0" value="0"></div>
      </div>
      <div class="modal-field"><label>Subject Specialization</label><input name="department" maxlength="255"></div>
      <div class="modal-field"><label>Qualification</label><textarea name="qualification" rows="2"></textarea></div>
      <div class="modal-row">
        <div class="modal-field"><label>Email</label><input name="email" type="email"></div>
        <div class="modal-field"><label>Phone</label><input name="phone"></div>
      </div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary">Hire Staff</button>
      </div>
    </form>
  `);
  document.getElementById('hireStaffForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await apiFetch('/admin/teachers/hire', {
        method: 'POST',
        body: JSON.stringify({
          fullName: fd.get('fullName'), username: fd.get('username'), password: fd.get('password'),
          departmentId: Number(fd.get('departmentId')), department: fd.get('department'),
          qualification: fd.get('qualification'),
          yearsOfExperience: Number(fd.get('yearsOfExperience') || 0),
          email: fd.get('email'), phone: fd.get('phone'),
        }),
      });
      closeModal();
      loadStaff();
    } catch (err) { alert(err.message); }
  });
}

async function openEditStaffModal(id) {
  const teacher = (window._teachersCache || []).find(t => t.id === id);
  if (!teacher) return;
  openModal('<h2>Edit Staff Member</h2><p role="status">Loading active departments…</p>');
  let departments;
  try {
    departments = await apiGet('/admin/institution/departments/active');
  } catch (err) {
    openModal(`<h2>Departments unavailable</h2><p role="alert">${escapeHtml(err.message || 'Unable to load active departments.')}</p><div class="modal-actions"><button type="button" class="btn-secondary" onclick="closeModal()">Close</button></div>`);
    return;
  }
  if (teacher.departmentId && !departments.some(department => String(department.id) === String(teacher.departmentId))) {
    departments.push({
      id: teacher.departmentId,
      name: teacher.departmentName || 'Current inactive department',
      facultyName: '',
      campusName: '',
      inactive: true,
    });
  }
  openModal(`
    <span class="modal-close" onclick="closeModal()">&times;</span>
    <h2>Edit Staff Member</h2>
    <form id="editStaffForm">
      <div class="modal-field"><label>Full Name *</label><input name="fullName" value="${escapeHtml(teacher.fullName)}" required></div>
      <div class="modal-row">
        <div class="modal-field"><label>Home Department *</label><select name="departmentId" required>${teacherDepartmentOptions(departments, teacher.departmentId)}</select></div>
        <div class="modal-field"><label>Years of Experience</label><input name="yearsOfExperience" type="number" min="0" value="${teacher.yearsOfExperience ?? 0}"></div>
      </div>
      <div class="modal-field"><label>Subject Specialization</label><input name="department" maxlength="255" value="${escapeHtml(teacher.department || '')}"></div>
      <div class="modal-field"><label>Qualification</label><textarea name="qualification" rows="2">${escapeHtml(teacher.qualification || '')}</textarea></div>
      <div class="modal-row">
        <div class="modal-field"><label>Email</label><input name="email" type="email" value="${escapeHtml(teacher.email || '')}"></div>
        <div class="modal-field"><label>Phone</label><input name="phone" value="${escapeHtml(teacher.phone || '')}"></div>
      </div>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button type="submit" class="btn-primary">Save Changes</button>
      </div>
    </form>
  `);
  document.getElementById('editStaffForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await apiFetch(`/admin/teachers/${id}`, {
        method: 'PUT',
        body: JSON.stringify({
          fullName: fd.get('fullName'), departmentId: Number(fd.get('departmentId')),
          department: fd.get('department'),
          qualification: fd.get('qualification'), yearsOfExperience: Number(fd.get('yearsOfExperience') || 0),
          email: fd.get('email'), phone: fd.get('phone'),
        }),
      });
      closeModal();
      loadStaff();
    } catch (err) { alert(err.message); }
  });
}

function confirmDeactivateStaff(id, name) {
  if (!confirm(`Deactivate ${name}? Their account will no longer be able to sign in.`)) return;
  apiFetch(`/admin/teachers/${id}`, { method: 'DELETE' })
    .then(() => loadStaff())
    .catch(err => alert(err.message));
}

async function restoreTeacher(id) {
  try { await apiFetch(`/admin/teachers/${id}/restore`, { method: 'POST' }); await loadStaff(); }
  catch (err) { alert(err.message || 'Unable to restore teacher.'); }
}

// ===== GRADES, REPORTS, FINANCE, AND SETTINGS =====
function exportGrades() {
  downloadCsv('grades-export.csv', window._adminGrades || []);
}

function downloadGradeImportTemplate() {
  downloadCsv('grade-import-template.csv', [{
    studentId: '',
    courseId: '',
    gradeValue: '',
    gpaScore: '',
    remarks: '',
  }]);
}

async function generateReport(type) {
  try {
    let report;
    if (type === 'enrollment') {
      report = await fetchAllAdminPages('/admin/students');
    } else if (type === 'academic') {
      const page = await apiGet('/admin/students?size=1');
      if (!page.content?.length) throw new Error('No students are available for an academic report.');
      await downloadPdf(`/reports/academic/${page.content[0].id}/download`, `academic-report-${page.content[0].id}.pdf`, 'Academic report is not available.');
      return;
    } else if (type === 'attendance') {
      const studentId = Number(await requestAdminInput('Attendance report', 'Student ID', 'number'));
      if (!studentId) return;
      report = await apiGet(`/attendance/student/${studentId}`);
    } else if (type === 'finance') {
      report = await apiGet('/admin/fees');
    } else if (type === 'faculty') {
      const teacherId = Number(await requestAdminInput('Faculty report', 'Teacher ID', 'number'));
      if (!teacherId) return;
      report = await apiGet(`/reports/teacher/${teacherId}`);
    } else if (type === 'audit') {
      report = await apiGet('/portal/admin/audit');
    } else if (type === 'class') {
      const className = await requestAdminInput('Class statistics', 'Class or programme name');
      if (!className) return;
      report = await apiGet(`/reports/class/${encodeURIComponent(className)}`);
    } else if (type === 'top-students') {
      const className = await requestAdminInput('Top students', 'Class or programme name');
      if (!className) return;
      report = await apiGet(`/reports/top-students?className=${encodeURIComponent(className)}&limit=10`);
    } else if (type === 'compare') {
      const first = Number(await requestAdminInput('Compare students', 'First student ID', 'number'));
      if (!first) return;
      const second = Number(await requestAdminInput('Compare students', 'Second student ID', 'number'));
      if (!first || !second) return;
      report = await apiGet(`/reports/compare?studentId1=${first}&studentId2=${second}`);
    } else {
      report = await apiGet('/admin/statistics');
    }
    downloadReportData(`admin-${type}-report`, report);
  } catch (err) { alert(err.message || 'Report generation failed.'); }
}

async function generateStudentReport() {
  const studentId = Number(await requestAdminInput('Student academic report', 'Student ID', 'number'));
  if (!studentId) return;
  try {
    const report = await apiGet(`/reports/academic/${studentId}`);
    if (report?.published) {
      await downloadPdf(`/reports/academic/${studentId}/official/download`, `official-academic-report-${studentId}.pdf`, 'Official academic report is not available.');
    } else {
      alert(`Results are ${report?.publicationStatus || 'not finalized'}. The official PDF is available only after an administrator publishes the approved version.`);
    }
  } catch (err) { alert(err.message || 'Academic report failed.'); }
}

async function refreshStudentReportApprovalStatus(studentId) {
  const badge = document.getElementById('studentReportApprovalStatus');
  if (!badge || !studentId) return;
  try {
    const report = await apiGet(`/reports/academic/${studentId}/status`);
    badge.style.display = 'inline-block';
    badge.className = report?.published ? 'status-badge success'
      : report?.approved ? 'status-badge warning' : 'status-badge muted';
    badge.textContent = report?.published
      ? `Published: Student ${studentId} · version ${report.resultsVersion} · ${report.publishedBy || 'ADMIN'} (${report.publishedAt || 'recent'})`
      : report?.approved
        ? `Approved, not published: Student ${studentId} · version ${report.resultsVersion}`
        : report?.publicationStatus === 'CHANGES_REQUIRED'
          ? `Changes require reapproval for Student ${studentId}`
          : `Pending approval for Student ${studentId}`;
  } catch (err) {
    badge.style.display = 'inline-block';
    badge.className = 'status-badge muted';
    badge.textContent = `Approval status unavailable for Student ${studentId}`;
  }
}

async function approveStudentReport() {
  openModal(`
    <button type="button" class="modal-close" onclick="closeModal()" aria-label="Close">&times;</button>
    <h2 id="approveStudentReportTitle">Approve student report</h2>
    <form id="approveStudentReportForm" onsubmit="submitStudentReportApproval(event)" aria-labelledby="approveStudentReportTitle">
      <div class="modal-field">
        <label for="approveStudentReportId">Student ID</label>
        <input id="approveStudentReportId" name="studentId" type="number" min="1" step="1" required>
      </div>
      <p id="approveStudentReportMessage" class="status-badge muted" role="status" aria-live="polite"></p>
      <div class="modal-actions">
        <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
        <button id="approveStudentReportSubmit" type="submit" class="btn-primary">Approve current version</button>
        <button id="publishStudentReportButton" type="button" class="btn-primary" onclick="publishStudentReport()" hidden>Publish approved version</button>
      </div>
    </form>
  `);
  document.getElementById('approveStudentReportId').focus();
}

async function submitStudentReportApproval(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const studentId = Number(new FormData(form).get('studentId'));
  if (!Number.isSafeInteger(studentId) || studentId <= 0) return;
  const submitButton = form.querySelector('button[type="submit"]');
  const message = document.getElementById('approveStudentReportMessage');
  submitButton.disabled = true;
  try {
    const report = await apiFetch(`/reports/academic/${studentId}/approve`, { method: 'POST' });
    const approved = await report.json();
    const badge = document.getElementById('studentReportApprovalStatus');
    if (badge) {
      badge.style.display = 'inline-block';
      badge.className = 'status-badge warning';
      badge.textContent = `Approved, not published: Student ${studentId} · version ${approved.resultsVersion}`;
    }
    form.dataset.resultsVersion = approved.resultsVersion;
    message.className = 'status-badge warning';
    message.textContent = `Version ${approved.resultsVersion} approved. Students cannot see it until you publish this exact version.`;
    submitButton.hidden = true;
    document.getElementById('publishStudentReportButton').hidden = false;
  } catch (err) {
    message.className = 'status-badge error';
    message.textContent = err.message || 'Unable to approve academic report.';
    submitButton.disabled = false;
  }
}

async function publishStudentReport() {
  const form = document.getElementById('approveStudentReportForm');
  const studentId = Number(new FormData(form).get('studentId'));
  const resultsVersion = form.dataset.resultsVersion;
  const button = document.getElementById('publishStudentReportButton');
  const message = document.getElementById('approveStudentReportMessage');
  if (!Number.isSafeInteger(studentId) || studentId <= 0 || !resultsVersion) return;
  if (!confirm(`Publish result version ${resultsVersion} for student ${studentId}?`)) return;
  button.disabled = true;
  try {
    const response = await apiFetch(`/reports/academic/${studentId}/publish`, {
      method: 'POST',
      body: JSON.stringify({ resultsVersion }),
    });
    const published = await response.json();
    const badge = document.getElementById('studentReportApprovalStatus');
    if (badge) {
      badge.style.display = 'inline-block';
      badge.className = 'status-badge success';
      badge.textContent = `Published: Student ${studentId} · version ${published.resultsVersion} · ${published.publishedAt || 'now'}`;
    }
    message.className = 'status-badge success';
    message.textContent = `Version ${published.resultsVersion} is now published to the student.`;
    button.hidden = true;
  } catch (err) {
    message.className = 'status-badge error';
    message.textContent = err.message || 'Unable to publish academic results.';
    button.disabled = false;
  }
}

async function generateFinanceReport() {
  try { downloadReportData('finance-report', await apiGet('/admin/fees')); }
  catch (err) { alert(err.message || 'Finance report failed.'); }
}

function downloadReportData(filename, report) {
  if (typeof report === 'string') {
    downloadCsv(`${filename}.csv`, [{ report }]);
    return;
  }
  if (Array.isArray(report)) {
    downloadCsv(`${filename}.csv`, report);
    return;
  }
  if (report && Array.isArray(report.content)) {
    downloadCsv(`${filename}.csv`, report.content);
    return;
  }
  if (report && typeof report === 'object') {
    downloadCsv(`${filename}.csv`, Object.entries(report).map(([metric, value]) => ({ metric, value })));
    return;
  }
  throw new Error('The report returned no exportable data.');
}

async function fetchAllAdminPages(path, pageSize = 250) {
  const first = await apiGet(`${path}?page=0&size=${pageSize}&sort=id`);
  if (!Array.isArray(first.content)) throw new Error('The server returned an invalid paginated report.');
  const content = [...first.content];
  const totalPages = Number(first.totalPages || 1);
  for (let page = 1; page < totalPages; page += 1) {
    const next = await apiGet(`${path}?page=${page}&size=${pageSize}&sort=id`);
    if (!Array.isArray(next.content)) throw new Error('The server returned an invalid paginated report.');
    content.push(...next.content);
  }
  return content;
}

function downloadAdminCsv(dataset) {
  const data = window._adminExportData?.[dataset];
  if (dataset === 'statistics' && data && typeof data === 'object' && !Array.isArray(data)) {
    downloadCsv('portal-statistics.csv', Object.entries(data).map(([metric, value]) => ({ metric, value })));
    return;
  }
  const records = Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : null;
  if (!records) throw new Error(`The ${dataset} export did not contain a record list.`);
  downloadCsv(`portal-${dataset}.csv`, records);
}

async function downloadPdf(path, filename, fallbackMessage) {
  try {
    const response = await apiFetch(path);
    await downloadResponse(response, filename);
  } catch (err) {
    alert(err.message || fallbackMessage);
  }
}
async function exportAdminData() {
  try {
    const [statistics, users, students, teachers, courses, grades, fees, audit] = await Promise.all([
      apiGet('/admin/statistics'), apiGet('/admin/users'), fetchAllAdminPages('/admin/students'),
      fetchAllAdminPages('/admin/teachers'), fetchAllAdminPages('/admin/courses'),
      apiGet('/admin/grades'), apiGet('/admin/fees'), apiGet('/portal/admin/audit'),
    ]);
    window._adminExportData = { statistics, users, students, teachers, courses, grades, fees, audit };
    downloadCsv('portal-admin-export-summary.csv', [{
      exportedAt: new Date().toISOString(),
      statistics: Object.keys(window._adminExportData.statistics || {}).length,
      users: window._adminExportData.users.length,
      students: window._adminExportData.students.length,
      teachers: window._adminExportData.teachers.length,
      courses: window._adminExportData.courses.length,
      grades: window._adminExportData.grades.length,
      fees: window._adminExportData.fees.length,
      audit: window._adminExportData.audit.length,
    }]);
    document.getElementById('adminCsvExports').hidden = false;
  }
  catch (err) { alert(err.message || 'Admin data export failed.'); }
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-admin-csv]').forEach(button => {
    button.addEventListener('click', () => {
      try { downloadAdminCsv(button.dataset.adminCsv); }
      catch (err) { alert(err.message || 'CSV export failed.'); }
    });
  });
});
async function testEmail() {
  const recipient = prompt('Send a test email to:');
  if (!recipient) return;
  try {
    await apiFetch('/admin/email/test', { method: 'POST', body: JSON.stringify({ recipient }) });
    alert('The email provider accepted the test message. Check the recipient inbox and spam folder to confirm delivery.');
  } catch (err) { alert(err.message || 'Test email failed.'); }
}
function viewTask() { switchModule('requests'); }

function toggleProfile() {
  toggleDropdown('profile');
}

function setTheme(theme) {
  if (window.setSharedTheme) {
    window.setSharedTheme(theme);
    return;
  }

  const resolved = theme === 'system' ? (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : theme;
  document.documentElement.dataset.theme = resolved;
  document.body.classList.toggle('dark-mode', resolved === 'dark');
  const button = document.querySelector('[data-theme-toggle]');
  if (button) {
    button.textContent = resolved === 'dark' ? '☀️' : '🌙';
  }
  if (theme !== 'system') localStorage.setItem('theme', theme);
}
