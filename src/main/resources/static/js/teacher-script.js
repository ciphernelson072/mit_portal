document.addEventListener('DOMContentLoaded', async () => {
  if (!requireRole(['TEACHER', 'ADMIN'], 'admin-login.html')) return;
  setupTeacherWorkspaceNavigation();
  setupTeacherHeaderControls();
  const today = new Date();
  document.getElementById('attendanceDate').value = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-');
  document.getElementById('rosterCourseSelect').addEventListener('change', loadRoster);
  document.getElementById('attendanceCourseSelect').addEventListener('change', loadAttendanceRoster);
  document.getElementById('attendanceDate').addEventListener('change', loadAttendanceRoster);
  document.getElementById('gradeForm').addEventListener('submit', submitGrade);
  document.getElementById('materialForm').addEventListener('submit', submitMaterial);
  document.getElementById('assignmentForm').addEventListener('submit', submitAssignment);
  document.getElementById('teacherRequestForm').addEventListener('submit', submitTeacherRequest);
  await Promise.all([loadTeacherProfile(), loadTeacherDashboard(), loadCourses()]);
  await loadTeacherMaterials();
  await loadTeacherAssignments();
  await loadTeacherNotifications();
  await loadTeacherRequests();
});

async function loadTeacherDashboard() {
  const fields = [
    'assignedCourseCount',
    'enrolledStudentCount',
    'pendingGradeCount',
    'upcomingAssignmentCount',
    'pendingAttendanceCount',
  ];
  const status = document.getElementById('teacherDashboardStatus');
  try {
    const overview = await apiGet('/teacher/dashboard');
    fields.forEach(field => {
      const value = document.querySelector(`[data-teacher-stat="${field}"]`);
      if (value) value.textContent = String(overview[field] ?? 0);
    });
    if (status) {
      status.textContent = '';
      status.className = 'teacher-status';
    }
  } catch (err) {
    fields.forEach(field => {
      const value = document.querySelector(`[data-teacher-stat="${field}"]`);
      if (value) value.textContent = '—';
    });
    setStatus('teacherDashboardStatus', err.message || 'Unable to load teaching summary.', 'error');
  }
}

function getTeacherRequestRole() {
  return normalizeRole(getRole()) === 'ADMIN' ? 'ADMIN' : 'TEACHER';
}

async function submitTeacherRequest(event) {
  event.preventDefault();
  try {
    const payload = {
      type: document.getElementById('teacherRequestType').value,
      subject: document.getElementById('teacherRequestSubject').value.trim(),
      details: document.getElementById('teacherRequestDetails').value.trim(),
    };
    await apiFetch('/student/requests', { method: 'POST', body: JSON.stringify(payload) });
    setStatus('teacherRequestStatus', 'Request submitted successfully.', 'success');
    event.target.reset();
    await loadTeacherRequests();
  } catch (err) { setStatus('teacherRequestStatus', err.message, 'error'); }
}

async function loadTeacherRequests() {
  try {
    const requests = await apiGet('/student/requests');
    const list = document.getElementById('teacherRequestList');
    if (list) {
      list.innerHTML = requests.length
        ? requests.map(request => {
            const requesterRole = normalizeRole(request.requesterRole || request.role || getTeacherRequestRole());
            const label = requesterRole === 'ADMIN' ? 'Administration' : 'Faculty';
            return `<div class="teacher-request-item"><strong>#REQ-${request.id} ${escapeHtml(request.subject)}</strong><span>${escapeHtml(label)} · ${escapeHtml(request.status || 'OPEN')} · ${escapeHtml((request.createdAt || '').slice(0, 10))}</span></div>`;
          }).join('')
        : '<p>No requests submitted yet.</p>';
    }
  } catch (err) { setStatus('teacherRequestStatus', err.message, 'error'); }
}

async function loadTeacherNotifications() {
  try {
    const notifications = await apiGet('/notifications');
    const list = document.getElementById('teacherNotificationList');
    const badge = document.getElementById('teacherNotificationBadge');
    if (list) list.innerHTML = notifications.length ? notifications.slice(0, 8).map(item => `<button type="button" class="teacher-notification-item ${item.read ? '' : 'unread'}" onclick="markTeacherNotificationRead(${item.id}, this)"><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(item.message)}</span></button>`).join('') : '<p>No notifications yet.</p>';
    const unread = notifications.filter(item => !item.read).length;
    if (badge) { badge.textContent = unread; badge.hidden = unread === 0; }
  } catch (err) { setStatus('teacherWelcome', err.message, 'error'); }
}

function toggleTeacherNotifications() {
  const list = document.getElementById('teacherNotificationList');
  if (list) list.hidden = !list.hidden;
}

async function markTeacherNotificationRead(id, element) {
  await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
  element.classList.remove('unread');
  loadTeacherNotifications();
}

async function loadTeacherProfile() {
  try {
    const profile = await apiGet('/teacher/me');
    document.getElementById('teacherWelcome').textContent = `Welcome, ${profile.fullName}. Manage your assigned classes here.`;
    updateTeacherPhoto(profile.profileImagePath, profile.fullName);
  } catch (err) {
    setStatus('teacherWelcome', err.message, 'error');
  }
}

function updateTeacherPhoto(profileImagePath, fullName = 'Teacher') {
  const image = document.getElementById('teacherProfileImage');
  if (!image || !profileImagePath) return;
  image.src = `${API}/files/download?filePath=${encodeURIComponent(profileImagePath)}`;
  image.dataset.photoCaption = `${fullName} profile photo`;
  image.alt = `${fullName} profile photo; activate to view full screen`;
}

function setupTeacherWorkspaceNavigation() {
  const links = Array.from(document.querySelectorAll('.teacher-dashboard .sidebar-nav a[href^="#"]'));
  links.forEach(link => link.addEventListener('click', () => {
    links.forEach(item => item.classList.toggle('active', item === link));
  }));
}

function setupTeacherHeaderControls() {
  document.getElementById('teacherNotificationBtn')?.addEventListener('click', event => {
    const button = event.currentTarget;
    const dropdown = button.closest('.dropdown');
    const opening = !dropdown.classList.contains('active');
    document.querySelectorAll('.header-actions .dropdown.active').forEach(item => {
      item.classList.remove('active');
      item.querySelector('button')?.setAttribute('aria-expanded', 'false');
    });
    dropdown.classList.toggle('active', opening);
    document.getElementById('teacherNotificationList').hidden = !opening;
    button.setAttribute('aria-expanded', String(opening));
  });
  document.addEventListener('click', event => {
    if (event.target.closest('.header-actions .dropdown')) return;
    document.querySelectorAll('.header-actions .dropdown.active').forEach(dropdown => {
      dropdown.classList.remove('active');
      dropdown.querySelector('button')?.setAttribute('aria-expanded', 'false');
    });
  });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    document.querySelectorAll('.header-actions .dropdown.active').forEach(dropdown => {
      dropdown.classList.remove('active');
      dropdown.querySelector('button')?.setAttribute('aria-expanded', 'false');
    });
  });
}

async function uploadTeacherPhoto() {
  const input = document.getElementById('teacherPhotoInput');
  let file = input?.files[0];
  if (!file) return;
  try {
    file = validateUploadFile(file, ['jpg', 'jpeg', 'png', 'gif']);
    const profile = await apiGet('/teacher/me');
    const form = new FormData();
    form.append('file', file);
    await apiFetch(`/files/teacher/${profile.id}/profile-image`, { method: 'POST', body: form });
    const updatedProfile = await apiGet('/teacher/me');
    updateTeacherPhoto(updatedProfile.profileImagePath, updatedProfile.fullName);
    setStatus('teacherWelcome', 'Profile photo updated.', 'success');
  } catch (err) { setStatus('teacherWelcome', err.message, 'error'); }
  input.value = '';
}

async function loadCourses() {
  try {
    const courses = await apiGet('/teacher/my-courses');
    window._teacherCourses = courses;
    const courseOptions = courses.length
      ? courses.map(course => `<option value="${course.id}">${escapeHtml(course.courseCode || '—')} · ${escapeHtml(course.name)}</option>`).join('')
      : '<option value="">No assigned courses</option>';
    document.getElementById('rosterCourseSelect').innerHTML = courseOptions;
    document.getElementById('attendanceCourseSelect').innerHTML = courseOptions;
    if (courses.length) {
      await loadRoster();
    }
    if (courses.length) await loadAttendanceRoster();
  } catch (err) {
    document.getElementById('rosterCourseSelect').innerHTML = '<option value="">Unable to load courses</option>';
    document.getElementById('attendanceCourseSelect').innerHTML = '<option value="">Unable to load courses</option>';
    setStatus('rosterStatus', err.message, 'error');
    setStatus('attendanceStatus', err.message, 'error');
  }
}

function selectedTeacherCourse(selectId = 'rosterCourseSelect') {
  const id = Number(document.getElementById(selectId)?.value);
  return (window._teacherCourses || []).find(course => course.id === id);
}

async function loadRoster() {
  const courseId = document.getElementById('rosterCourseSelect').value;
  const table = document.getElementById('rosterTable');
  const status = document.getElementById('rosterStatus');
  window._teacherRoster = [];
  document.getElementById('exportTeacherRoster').disabled = true;
  if (!courseId) return;
  try {
    const students = await apiGet(`/teacher/courses/${courseId}/students`);
    window._teacherRoster = students;
    document.getElementById('exportTeacherRoster').disabled = students.length === 0;
    const options = students.map(student => `<option value="${student.id}">${escapeHtml(student.fullName)} (${escapeHtml(student.registrationNumber || '—')})</option>`).join('');
    document.getElementById('gradeStudent').innerHTML = options || '<option value="">No enrolled students</option>';
    table.hidden = !students.length;
    table.querySelector('tbody').innerHTML = students.map(student => {
      const photo = student.profileImagePath
        ? `<img class="teacher-roster-photo" data-photo-viewer data-photo-caption="${escapeHtml(student.fullName)} profile photo" tabindex="0" role="button" src="${escapeHtml(`${API}/files/download?filePath=${encodeURIComponent(student.profileImagePath)}`)}" alt="${escapeHtml(student.fullName)} profile photo; activate to view full screen">`
        : '<span aria-label="No profile photo">—</span>';
      return `<tr><td>${photo}</td><td>${escapeHtml(student.fullName)}</td><td>${escapeHtml(student.registrationNumber || '—')}</td><td>${student.cgpa == null ? '—' : Number(student.cgpa).toFixed(2)}</td><td><button type="button" class="btn-secondary small" onclick="viewStudentProfile(${student.id})">Profile</button></td></tr>`;
    }).join('');
    status.textContent = `${students.length} enrolled student${students.length === 1 ? '' : 's'}.`;
    status.className = 'teacher-status success';
    await loadTeacherGrades();
  } catch (err) {
    setStatus('rosterStatus', err.message, 'error');
  }
}

function exportTeacherRoster() {
  const course = selectedTeacherCourse();
  if (!course || !window._teacherRoster?.length) {
    return setStatus('rosterStatus', 'Load a course roster before exporting.', 'error');
  }
  downloadCsv(`teacher-roster-${course.courseCode || course.id}.csv`, window._teacherRoster.map(student => ({
    studentId: student.id,
    fullName: student.fullName,
    registrationNumber: student.registrationNumber,
    className: student.className,
    cgpa: student.cgpa,
  })));
}

function exportTeacherGrades() {
  const course = selectedTeacherCourse();
  if (!course || !window._teacherGrades?.length) {
    return setStatus('gradeStatus', 'Load course grades before exporting.', 'error');
  }
  downloadCsv(`teacher-grades-${course.courseCode || course.id}.csv`, window._teacherGrades.map(grade => ({
    gradeId: grade.id,
    studentId: grade.studentId,
    studentName: grade.studentName,
    courseId: course.id,
    gradeValue: grade.gradeValue,
    status: grade.status,
    remarks: grade.remarks,
    rejectionReason: grade.rejectionReason,
  })));
}

async function viewStudentProfile(studentId) {
  try {
    const profile = await apiGet(`/teacher/students/${studentId}/profile`);
    alert(`${profile.fullName}\nUsername: ${profile.username}\nClass: ${profile.className || '—'}\nCGPA: ${profile.cgpa == null ? '—' : Number(profile.cgpa).toFixed(2)}`);
  } catch (err) { alert(err.message || 'Unable to load student profile.'); }
}

async function submitGrade(event) {
  event.preventDefault();
  const status = document.getElementById('gradeStatus');
  try {
    await apiFetch('/teacher/grades', { method: 'POST', body: JSON.stringify({
      studentId: Number(document.getElementById('gradeStudent').value),
      courseId: Number(selectedTeacherCourse()?.id),
      gradeValue: document.getElementById('gradeValue').value.trim(),
      remarks: document.getElementById('gradeRemarks').value.trim(),
    }) });
    setStatus('gradeStatus', 'Grade saved successfully.', 'success');
    event.target.reset();
    await loadTeacherGrades();
  } catch (err) {
    setStatus('gradeStatus', err.message, 'error');
  }
}

async function updateGradeRecord() {
  const id = Number(document.getElementById('gradeRecordId').value);
  if (!id) return setStatus('gradeStatus', 'Enter an existing grade ID.', 'error');
  try {
    await apiFetch(`/teacher/grades/${id}`, { method: 'PUT', body: JSON.stringify({
      studentId: Number(document.getElementById('gradeStudent').value),
      courseId: Number(selectedTeacherCourse()?.id),
      gradeValue: document.getElementById('gradeValue').value.trim(),
      remarks: document.getElementById('gradeRemarks').value.trim(),
    }) });
    setStatus('gradeStatus', 'Grade updated successfully.', 'success');
    await loadTeacherGrades();
  } catch (err) { setStatus('gradeStatus', err.message, 'error'); }
}

async function deleteGradeRecord(gradeId = null) {
  const id = Number(gradeId || document.getElementById('gradeRecordId').value);
  if (!id || !confirm(`Delete grade #${id}?`)) return;
  try {
    await apiFetch(`/teacher/grades/${id}`, { method: 'DELETE' });
    setStatus('gradeStatus', 'Grade deleted.', 'success');
    await loadTeacherGrades();
  } catch (err) { setStatus('gradeStatus', err.message, 'error'); }
}

async function submitGradeForApproval(gradeId = null) {
  const id = Number(gradeId || document.getElementById('gradeRecordId').value);
  if (!id) return setStatus('gradeStatus', 'Enter an existing grade ID.', 'error');
  try {
    await apiFetch(`/teacher/grades/${id}/submit`, { method: 'POST' });
    setStatus('gradeStatus', 'Grade submitted for administrator approval.', 'success');
    await loadTeacherGrades();
  } catch (err) { setStatus('gradeStatus', err.message, 'error'); }
}

function editTeacherGrade(gradeId, studentId) {
  const grade = (window._teacherGrades || []).find(item => item.id === Number(gradeId));
  if (!grade) return;
  document.getElementById('gradeRecordId').value = grade.id;
  document.getElementById('gradeStudent').value = studentId;
  document.getElementById('gradeValue').value = grade.gradeValue || '';
  document.getElementById('gradeRemarks').value = grade.remarks || '';
  setStatus('gradeStatus', `Editing grade for ${grade.studentName}.`, 'success');
}

async function loadTeacherGrades() {
  const list = document.getElementById('teacherGradeList');
  const courseId = Number(selectedTeacherCourse()?.id);
  if (!list) return;
  if (!courseId) {
    list.innerHTML = '<p>Select a course to load grades.</p>';
    return;
  }
  try {
    const grades = await apiGet(`/teacher/courses/${courseId}/grades`);
    window._teacherGrades = grades;
    document.getElementById('exportTeacherGrades').disabled = grades.length === 0;
    list.innerHTML = grades.length ? `
      <table class="roster-table">
        <thead><tr><th>Student</th><th>Grade</th><th>Status</th><th>Remarks</th><th>Actions</th></tr></thead>
        <tbody>${grades.map(grade => `
          <tr>
            <td>${escapeHtml(grade.studentName)}</td>
            <td>${escapeHtml(grade.gradeValue)}</td>
            <td>${escapeHtml(grade.status)}</td>
            <td>${escapeHtml(grade.remarks || '—')}${grade.rejectionReason ? `<br>${escapeHtml(grade.rejectionReason)}` : ''}</td>
            <td class="teacher-actions">
              <button class="btn-secondary small" type="button" data-edit-grade="${escapeHtml(grade.id)}" data-student-id="${escapeHtml(grade.studentId)}">Edit</button>
              ${grade.status !== 'APPROVED' ? `<button class="btn-secondary small" type="button" data-submit-grade="${escapeHtml(grade.id)}">Submit for approval</button>` : ''}
              <button class="btn-small delete" type="button" data-delete-grade="${escapeHtml(grade.id)}">Delete</button>
            </td>
          </tr>`).join('')}</tbody>
      </table>` : '<p>No grades recorded for this course yet.</p>';
    list.querySelectorAll('[data-edit-grade]').forEach(button => button.addEventListener('click', () => editTeacherGrade(button.dataset.editGrade, button.dataset.studentId)));
    list.querySelectorAll('[data-submit-grade]').forEach(button => button.addEventListener('click', () => submitGradeForApproval(button.dataset.submitGrade)));
    list.querySelectorAll('[data-delete-grade]').forEach(button => button.addEventListener('click', () => deleteGradeRecord(button.dataset.deleteGrade)));
  } catch (err) {
    list.textContent = 'Unable to load course grades.';
    setStatus('gradeStatus', err.message, 'error');
  }
}

async function submitMaterial(event) {
  event.preventDefault();
  const courseId = Number(selectedTeacherCourse()?.id);
  if (!courseId) return setStatus('materialStatus', 'Select an assigned course first.', 'error');
  let file = document.getElementById('materialFile').files[0];
  try {
    file = validateUploadFile(file, ['pdf', 'jpg', 'jpeg', 'png', 'gif']);
  } catch (err) {
    return setStatus('materialStatus', err.message, 'error');
  }
  try {
    const form = new FormData();
    form.append('courseId', courseId);
    form.append('title', document.getElementById('materialTitle').value.trim());
    form.append('description', document.getElementById('materialDescription').value.trim());
    form.append('file', file);
    await apiFetch('/materials', { method: 'POST', body: form });
    setStatus('materialStatus', 'Material published successfully.', 'success');
    event.target.reset();
    await loadTeacherMaterials();
  } catch (err) { setStatus('materialStatus', err.message, 'error'); }
}

async function loadTeacherMaterials() {
  const list = document.getElementById('teacherMaterialList');
  if (!list) return;
  const courses = window._teacherCourses || [];
  if (!courses.length) {
    list.innerHTML = '<p>No assigned courses available.</p>';
    return;
  }
  try {
    const results = await Promise.all(courses.map(async course => ({
      course,
      materials: await apiGet(`/materials/course/${course.id}`),
    })));
    const materials = results.flatMap(({ course, materials: courseMaterials }) => courseMaterials.map(material => ({
      ...material,
      courseCode: course.courseCode,
      courseName: course.name,
    })));
    list.innerHTML = materials.length ? materials.map(material => `
      <article class="teacher-request-item">
        <strong>${escapeHtml(material.title)}</strong>
        <span>${escapeHtml(material.courseCode || material.courseName || 'Assigned course')} · Uploaded ${escapeHtml((material.uploadedAt || '').replace('T', ' ') || 'date unavailable')}</span>
        ${material.description ? `<p>${escapeHtml(material.description)}</p>` : ''}
        <a class="btn-secondary" href="${API}/materials/${encodeURIComponent(material.id)}/download" target="_blank" rel="noopener">Download</a>
      </article>`).join('') : '<p>No materials published to your courses yet.</p>';
  } catch (err) {
    list.textContent = 'Unable to load published course materials.';
    setStatus('materialStatus', err.message, 'error');
  }
}

async function loadAttendanceRoster() {
  const courseId = Number(document.getElementById('attendanceCourseSelect').value);
  const table = document.getElementById('courseAttendanceTable');
  const date = document.getElementById('attendanceDate').value;
  const tbody = table.querySelector('tbody');
  tbody.replaceChildren();
  table.hidden = true;
  if (!courseId || !date) {
    setStatus('attendanceStatus', 'Choose a course and class date to load the roster.', 'error');
    return;
  }
  setStatus('attendanceStatus', 'Loading attendance roster...', 'info');
  try {
    const [students, records] = await Promise.all([
      apiGet(`/teacher/courses/${courseId}/students`),
      apiGet(`/teacher/courses/${courseId}/attendance?date=${encodeURIComponent(date)}`),
    ]);
    const existing = new Map(records.map(record => [record.studentId, record]));
    tbody.innerHTML = students.map(student => {
      const existingRecord = existing.get(student.id);
      const status = existingRecord?.late ? 'late' : existingRecord ? existingRecord.present ? 'present' : 'absent' : '';
      return `<tr data-student-id="${student.id}"><td>${escapeHtml(student.fullName)}</td><td>${escapeHtml(student.registrationNumber || '—')}</td><td><label><input type="radio" name="attendance-${student.id}" value="present" ${status === 'present' ? 'checked' : ''} aria-label="Present for ${escapeHtml(student.fullName)}"> ✓</label></td><td><label><input type="radio" name="attendance-${student.id}" value="late" ${status === 'late' ? 'checked' : ''} aria-label="Late for ${escapeHtml(student.fullName)}"> ⏱</label></td><td><label><input type="radio" name="attendance-${student.id}" value="absent" ${status === 'absent' ? 'checked' : ''} aria-label="Absent for ${escapeHtml(student.fullName)}"> ✕</label></td></tr>`;
    }).join('');
    table.hidden = !students.length;
    setStatus('attendanceStatus', `${students.length} enrolled student${students.length === 1 ? '' : 's'} ready for approval.`, 'success');
  } catch (err) { setStatus('attendanceStatus', err.message, 'error'); }
}

async function approveClassAttendance() {
  const courseId = Number(document.getElementById('attendanceCourseSelect').value);
  const date = document.getElementById('attendanceDate').value;
  const rows = [...document.querySelectorAll('#courseAttendanceTable tbody tr[data-student-id]')];
  if (!courseId || !date || !rows.length) return setStatus('attendanceStatus', 'Choose a course with enrolled students first.', 'error');
  if (rows.some(row => !row.querySelector('input:checked'))) {
    return setStatus('attendanceStatus', 'Select Present, Late, or Absent for every student before approval.', 'error');
  }
  try {
    const response = await apiFetch('/teacher/attendance/approve', {
      method: 'POST',
      body: JSON.stringify({ courseId, date, records: rows.map(row => {
        const status = row.querySelector('input:checked').value;
        return {
          studentId: Number(row.dataset.studentId),
          present: status !== 'absent',
          late: status === 'late',
          remarks: status === 'late'
            ? 'Marked late by teacher'
            : status === 'present'
              ? 'Marked present by teacher'
              : 'Marked absent by teacher',
        };
      }) }),
    });
    const saved = await response.json();
    setStatus('attendanceStatus', `Approved ${saved.length} attendance record${saved.length === 1 ? '' : 's'} for ${date}.`, 'success');
  } catch (err) { setStatus('attendanceStatus', err.message, 'error'); }
}

async function submitAssignment(event) {
  event.preventDefault();
  const courseId = Number(selectedTeacherCourse()?.id);
  if (!courseId) return setStatus('assignmentStatus', 'Select an assigned course first.', 'error');
  try {
    await apiFetch('/portal/teacher/assignments', { method: 'POST', body: JSON.stringify({
      courseId,
      title: document.getElementById('assignmentTitle').value.trim(),
      description: document.getElementById('assignmentDescription').value.trim(),
      dueAt: document.getElementById('assignmentDueAt').value,
    }) });
    setStatus('assignmentStatus', 'Assignment published successfully.', 'success');
    event.target.reset();
    await loadTeacherAssignments();
  } catch (err) { setStatus('assignmentStatus', err.message, 'error'); }
}

async function loadTeacherAssignments() {
  const list = document.getElementById('teacherAssignmentList');
  if (!list) return;
  try {
    const assignments = await apiGet('/portal/teacher/assignments');
    list.innerHTML = assignments.length ? assignments.map(assignment => `
      <article class="teacher-request-item">
        <strong>${escapeHtml(assignment.title)}</strong>
        <span>Course #${escapeHtml(assignment.courseId)} · Due ${escapeHtml((assignment.dueAt || '').replace('T', ' '))}</span>
        ${assignment.description ? `<p>${escapeHtml(assignment.description)}</p>` : ''}
      </article>`).join('') : '<p>No assignments published yet.</p>';
  } catch (err) {
    list.textContent = 'Unable to load published assignments.';
    setStatus('assignmentStatus', err.message, 'error');
  }
}

function teacherLogout() { logout('admin-login.html'); }
function setStatus(id, message, kind) { const el = document.getElementById(id); if (el) { el.textContent = message; el.className = `teacher-status ${kind}`; } }
