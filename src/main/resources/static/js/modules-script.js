// API, apiGet(), requireRole(), logout(), escapeHtml() all come from auth.js,
// loaded before this file.

// Every module page requires a real, active STUDENT session.
requireRole(['STUDENT']);

let portalPreferenceSettings = {};
let portalPreferenceSaveQueue = Promise.resolve();

// ===== TAB SWITCHING =====
function switchTab(tabName, clickedButton = null) {
  // Hide all tabs
  document.querySelectorAll('.tab-content').forEach(tab => {
    tab.classList.remove('active');
  });

  // Remove active class from all buttons
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.remove('active');
  });

  // Show selected tab
  const tabElement = document.getElementById(tabName + 'Tab');
  if (tabElement) {
    tabElement.classList.add('active');
  }

  // Activate corresponding button
  const activeButton = clickedButton || document.querySelector(`[onclick="switchTab('${tabName}')"]`);
  if (activeButton) activeButton.classList.add('active');
}

// ===== DROPDOWN SETUP =====
document.addEventListener('DOMContentLoaded', function() {
  // Setup profile dropdown
  const profileBtn = document.getElementById('profileBtn');
  if (profileBtn) {
    profileBtn.addEventListener('click', function(e) {
      e.stopPropagation();
      const dropdown = this.closest('.dropdown');
      dropdown.classList.toggle('active');
    });
  }

  // Close dropdowns when clicking outside
  document.addEventListener('click', function() {
    document.querySelectorAll('.dropdown.active').forEach(dropdown => {
      dropdown.classList.remove('active');
    });
  });

  // Load real data for whichever module page this is
  initModuleData();
  setupModuleSearch();
  setupPaymentDialog();
  document.getElementById('gradeAppealForm')?.addEventListener('submit', submitGradeAppeal);
  document.getElementById('cancelGradeAppeal')?.addEventListener('click', () => {
    document.getElementById('gradeAppealDialog')?.close();
  });
  const hashTab = window.location.hash.replace('#', '');
  if (['security', 'preferences', 'grades', 'timetable', 'courses', 'assignments', 'events'].includes(hashTab)) {
    switchTab(hashTab);
  }
});

// ===== NAVIGATION =====
function goToDashboard() {
  window.location.href = 'dashboard.html';
}

function handleLogoutClick() {
  if (confirm('Are you sure you want to logout?')) {
    logout('index.html');
  }
}

// ===== REAL DATA LOADING (per page) =====
function initModuleData() {
  const page = window.location.pathname.split('/').pop();
  if (page === 'academics.html') loadAcademicsData();
  else if (page === 'attendance.html') loadAttendanceData();
  else if (page === 'finance.html') {
    loadFinanceData();
    handleFlutterwaveReturn();
  }
  else if (page === 'profile.html') loadProfileData();
  else if (page === 'campus-support.html') {
    loadServiceRequests('.tickets-table tbody');
    loadCampusData();
  }
}

function setupModuleSearch() {
  document.querySelectorAll('.search-bar input').forEach(input => {
    input.addEventListener('input', () => {
      const query = input.value.trim().toLowerCase();
      const page = window.location.pathname.split('/').pop();
      const selector = page === 'campus-support.html' ? '.event-card, .club-card, .faq-item, .route-card' :
        page === 'finance.html' ? '.finance-card, .payment-card, .fees-table tr' :
        page === 'profile.html' ? '.document-item, .requests-table tr' : '.course-card, .assignment-card, .exam-card';
      document.querySelectorAll(selector).forEach(item => { item.hidden = Boolean(query) && !item.textContent.toLowerCase().includes(query); });
    });
  });
}

async function loadCampusData() {
  try {
    const [events, routes, registrations, clubs, chatHistory] = await Promise.all([
      apiGet('/portal/events'), apiGet('/portal/routes'), apiGet('/portal/student/event-registrations'), apiGet('/portal/clubs'), apiGet('/campus/student/chat'),
    ]);
    window._campusEvents = events;
    window._eventRegistrations = registrations;
    window._clubs = clubs;
    renderChatHistory(chatHistory);
    const eventsContainer = document.querySelector('.events-container');
    if (eventsContainer) eventsContainer.innerHTML = events.length ? events.map(event => {
      const registration = registrations.find(item => item.eventId === event.id && item.status === 'REGISTERED');
      const startsAt = Date.parse(event.startsAt);
      const eventStarted = !Number.isFinite(startsAt) || startsAt <= Date.now();
      const actionLabel = registration ? 'Cancel registration' : eventStarted ? 'Registration closed' : 'Register';
      const disabled = !registration && eventStarted ? 'disabled' : '';
      return `<article class="event-card" data-event-id="${event.id}"><div class="event-header"><h3>${escapeHtml(event.title)}</h3></div><div class="event-body"><p class="event-description">${escapeHtml(event.description || '')}</p><p>${escapeHtml(event.startsAt)} · ${escapeHtml(event.venue)}</p></div><div class="event-actions"><button class="${registration ? 'btn-secondary' : 'btn-primary'}" ${disabled} onclick="${registration ? 'cancelEventRegistration' : 'registerEvent'}(this)">${actionLabel}</button><button class="btn-secondary" onclick="viewEventDetails(this)">Details</button></div></article>`;
    }).join('') : '<p>No campus events are currently published.</p>';
    const routesGrid = document.querySelector('.routes-grid');
    if (routesGrid) routesGrid.innerHTML = routes.length ? routes.map(route => `<article class="route-card" data-route-id="${route.id}"><div class="route-number">${route.id}</div><h4>${escapeHtml(route.name)}</h4><p>${escapeHtml(route.stops)}</p><button class="btn-secondary" onclick="viewRoute(${route.id})">View Route</button></article>`).join('') : '<p>No transport routes are currently published.</p>';
    const clubsContainer = document.querySelector('.clubs-container');
    if (clubsContainer) clubsContainer.innerHTML = clubs.length ? clubs.map(club => `<article class="club-card" data-club-id="${club.id}"><div class="club-icon">🎓</div><h3>${escapeHtml(club.name)}</h3><p class="club-description">${escapeHtml(club.description || '')}</p><div class="club-info"><span>👥 ${club.memberCount} Members</span><span>📅 ${escapeHtml(club.meetingInfo || 'Meeting details unavailable')}</span></div><button class="btn-primary" onclick="toggleClubMembership(${club.id}, ${Boolean(club.joined)})">${club.joined ? 'Leave Club' : 'Join Club'}</button></article>`).join('') : '<p>No clubs are currently available.</p>';
    const requests = await apiGet('/student/requests');
    window._requestsCache = requests;
    const hostelRequest = requests.find(request => request.type === 'hostel' || request.type === 'HOSTEL');
    const transportRequest = requests.find(request => request.type === 'transport' || request.type === 'TRANSPORT_PASS');
    const hostelStatus = document.getElementById('hostelStatus');
    const transportStatus = document.getElementById('transportPassStatus');
    const transportValidUntil = document.getElementById('transportPassValidUntil');
    const [hostelApplications, transportPass] = await Promise.all([
      apiGet('/campus/student/hostel'), apiGet('/campus/student/transport-pass').catch(() => null),
    ]);
    if (hostelStatus) hostelStatus.textContent = hostelApplications[0]?.status || (hostelRequest ? hostelRequest.status : 'No request');
    if (transportStatus) transportStatus.textContent = transportPass?.status || (transportRequest ? transportRequest.status : 'No pass');
    if (transportValidUntil) transportValidUntil.textContent = transportPass?.validUntil || 'Not available';
  } catch (err) { console.error('Failed to load campus data', err); }
}

function renderChatHistory(messages) {
  const container = document.getElementById('chatMessages');
  if (!container || !Array.isArray(messages)) return;
  container.replaceChildren();
  if (!messages.length) {
    addChatMessage('Hello! I am the MIT Student Portal Assistant. How can I help you today?', 'bot');
    return;
  }
  messages.forEach(message => addChatMessage(message.content, message.sender === 'STUDENT' ? 'user' : 'bot'));
}

async function loadAcademicsData() {
  try {
    const [courses, grades, profile, assignments, exams, studentStatus, corrections, appeals, gradeStatuses] = await Promise.all([
      apiGet('/student/courses'),
      apiGet('/student/grades'),
      apiGet('/student/profile'),
      apiGet('/portal/student/assignments'),
      apiGet('/portal/student/exams'),
      apiGet('/student/my-status'),
      apiGet('/student/corrections'),
      apiGet('/student/grade-appeals'),
      apiGet('/student/grade-status'),
    ]);
    const timetable = await apiGet('/academic/student/timetable');
    const examResults = await apiGet('/academic/student/exam-results');
    const permits = await apiGet('/student/exam-permits');
    const resultStatus = await apiGet(`/reports/academic/${profile.id}/status`);
    loadAcademicRecords(profile.id);
    loadCourseRegistrationPanel();
    const semesters = [...new Set(courses.map(course => course.semester).filter(Boolean))];
    const coursesHeading = document.querySelector('#coursesTab > h2');
    if (coursesHeading) coursesHeading.textContent = semesters.length
      ? `Current Courses (${semesters.join(', ')})`
      : 'Current Courses';
    renderCourseCards(courses, grades);
    renderGradesTable(grades, profile, resultStatus, studentStatus, appeals, gradeStatuses);
    renderStudentCorrections(corrections);
    renderStudentGradeAppeals(appeals);
    window._assignments = assignments;
    window._exams = exams;
    renderAssignments(assignments);
    renderExams(exams, permits);
    renderTimetable(timetable, courses);
    renderExamResults(examResults, exams);
    renderAnalytics(grades, await apiGet('/student/attendance'), profile);
  } catch (err) {
    console.error('Failed to load academics data', err);
  }

  function renderStudentCorrections(corrections) {
    const container = document.getElementById('studentCorrectionList');
    if (!container) return;
    if (!Array.isArray(corrections) || !corrections.length) {
      container.innerHTML = '<p>No grade corrections are currently recorded.</p>';
      return;
    }
    container.innerHTML = `
      <table class="table"><thead><tr><th>Grade</th><th>Status</th><th>Reason</th><th>Authorized</th></tr></thead>
        <tbody>${corrections.map(item => `<tr><td>#${escapeHtml(item.gradeId)}</td><td>${escapeHtml(item.status)}</td><td>${escapeHtml(item.reason)}</td><td>${escapeHtml(item.authorizedAt || '—')}</td></tr>`).join('')}</tbody>
      </table>`;
  }

  function renderStudentGradeAppeals(appeals) {
    const container = document.getElementById('studentGradeAppealList');
    if (!container) return;
    if (!Array.isArray(appeals) || appeals.length === 0) {
      container.innerHTML = '<p>No grade appeals have been submitted.</p>';
      return;
    }
    container.innerHTML = `
      <table class="table"><thead><tr><th>Course</th><th>Status</th><th>Reason</th><th>Teacher response</th><th>Decision</th></tr></thead>
        <tbody>${appeals.map(item => `<tr>
          <td>${escapeHtml(item.courseCode)} · ${escapeHtml(item.courseName)}</td>
          <td>${escapeHtml(item.status)}</td>
          <td>${escapeHtml(item.reason)}</td>
          <td>${escapeHtml(item.teacherResponse || (item.teacherConsultedAt ? 'Consulted; no written response' : 'Not yet consulted'))}</td>
          <td>${escapeHtml(item.decisionNote || 'Pending')}</td>
        </tr>`).join('')}</tbody>
      </table>`;
  }
}

async function loadCourseRegistrationPanel() {
  const registrationsContainer = document.getElementById('myCourseRegistrations');
  const sectionsContainer = document.getElementById('eligibleCourseSections');
  if (!registrationsContainer || !sectionsContainer) return;

  registrationsContainer.innerHTML = '<p>Loading your registrations…</p>';
  sectionsContainer.innerHTML = '<p>Loading eligible sections…</p>';
  try {
    const [registrations, openTerms] = await Promise.all([
      apiGet('/registration/mine'),
      apiGet('/institution/academic-terms/open'),
    ]);
    const openTermIds = new Set(openTerms.map(term => Number(term.id)));
    registrationsContainer.innerHTML = registrations.length
      ? `<div class="roster-wrap"><table class="table"><thead><tr><th>Course</th><th>Term</th><th>Section</th><th>Status</th><th>Action</th></tr></thead><tbody>${
        registrations.map(registration => {
          const canDrop = openTermIds.has(Number(registration.termId));
          return `<tr>
            <td>${escapeHtml(registration.courseCode)} · ${escapeHtml(registration.courseName)}</td>
            <td>${escapeHtml(registration.termName || `Term ${registration.termId}`)}</td>
            <td>${escapeHtml(registration.sectionCode || '—')}</td>
            <td>${escapeHtml(registration.status)}</td>
            <td><button type="button" class="btn-secondary" data-drop-registration="${registration.id}" ${canDrop ? '' : 'disabled'}>
              ${canDrop ? 'Drop course' : 'Drop closed'}
            </button></td>
          </tr>`;
        }).join('')
      }</tbody></table></div>`
      : '<p>You are not registered for any course sections.</p>';

    registrationsContainer.querySelectorAll('[data-drop-registration]').forEach(button => {
      button.addEventListener('click', () => dropCourseRegistration(Number(button.dataset.dropRegistration)));
    });

    const sectionsByTerm = await Promise.all(openTerms.map(async term => ({
      term,
      sections: await apiGet(`/registration/sections?termId=${encodeURIComponent(term.id)}`),
    })));
    const sections = sectionsByTerm.flatMap(({ term, sections: termSections }) =>
      termSections.map(section => ({ ...section, termName: term.name })));
    sectionsContainer.innerHTML = sections.length
      ? `<div class="roster-wrap"><table class="table"><thead><tr><th>Course</th><th>Term</th><th>Section</th><th>Credits</th><th>Seats available</th><th>Action</th></tr></thead><tbody>${
        sections.map(section => {
          const alreadyRegistered = registrations.some(item =>
            Number(item.termId) === Number(section.termId) && Number(item.courseId) === Number(section.courseId));
          const isFull = Number(section.seatsAvailable) <= 0;
          return `<tr>
            <td>${escapeHtml(section.courseCode)} · ${escapeHtml(section.courseName)}</td>
            <td>${escapeHtml(section.termName)}</td>
            <td>${escapeHtml(section.sectionCode)}</td>
            <td>${escapeHtml(section.credits ?? '—')}</td>
            <td>${escapeHtml(`${section.seatsAvailable} / ${section.capacity}`)}</td>
            <td><button type="button" class="btn-primary" data-register-section="${section.id}" ${alreadyRegistered || isFull ? 'disabled' : ''}>
              ${alreadyRegistered ? 'Registered' : isFull ? 'Full' : 'Register'}
            </button></td>
          </tr>`;
        }).join('')
      }</tbody></table></div>`
      : '<p>No eligible course sections are currently open for registration.</p>';
    sectionsContainer.querySelectorAll('[data-register-section]').forEach(button => {
      button.addEventListener('click', () => registerCourseSection(Number(button.dataset.registerSection)));
    });
  } catch (error) {
    registrationsContainer.innerHTML = `<p role="alert">${escapeHtml(error.message || 'Unable to load registrations.')}</p>`;
    sectionsContainer.innerHTML = `<p role="alert">${escapeHtml(error.message || 'Unable to load eligible sections.')}</p>`;
  }
}

async function registerCourseSection(sectionId) {
  try {
    await apiFetch(`/registration/sections/${sectionId}`, { method: 'POST' });
    await loadCourseRegistrationPanel();
    await refreshStudentTimetableAfterRegistration();
  } catch (error) {
    alert(error.message || 'Unable to register for this course section.');
  }
}

async function refreshStudentTimetableAfterRegistration() {
  try {
    const [timetable, courses] = await Promise.all([
      apiGet('/academic/student/timetable'),
      apiGet('/student/courses'),
    ]);
    renderTimetable(timetable, courses);
  } catch (error) {
    console.error('Registration changed, but the timetable could not be refreshed', error);
    alert('Your registration was updated, but the timetable could not be refreshed. Reload the page to see the latest schedule.');
  }
}

async function dropCourseRegistration(registrationId) {
  if (!confirm('Drop this course registration? This action is available only while registration is open.')) return;
  try {
    await apiFetch(`/registration/${registrationId}`, { method: 'DELETE' });
    await loadCourseRegistrationPanel();
    await refreshStudentTimetableAfterRegistration();
  } catch (error) {
    alert(error.message || 'Unable to drop this course registration.');
  }
}

async function loadAcademicRecords(studentId) {
  const transcriptSummary = document.getElementById('transcriptSummary');
  const degreeAuditSummary = document.getElementById('degreeAuditSummary');
  const officialStatus = document.getElementById('officialTranscriptStatus');
  if (!transcriptSummary || !degreeAuditSummary) return;

  let status;
  try {
    status = await apiGet(`/reports/academic/${studentId}/status`);
    if (officialStatus) {
      officialStatus.style.display = 'inline-block';
      officialStatus.className = status?.published ? 'status-badge success'
        : status?.approved ? 'status-badge warning' : 'status-badge muted';
      officialStatus.textContent = status?.published
        ? `Official results published by ${status.publishedBy || 'Admin'} on ${status.publishedAt || 'recently'}`
        : status?.approved
          ? 'Results approved; awaiting administrator publication'
          : status?.publicationStatus === 'CHANGES_REQUIRED'
            ? 'Results changed after approval and require review'
            : 'Results pending administrator approval';
    }
    if (!status?.published) {
      transcriptSummary.textContent = 'Final grades, GPA, credits earned, and the official transcript are locked until results are published.';
      document.querySelector('#transcriptTable tbody').innerHTML =
        '<tr><td colspan="5">Results are not yet finalized.</td></tr>';
      document.getElementById('downloadTranscript').disabled = true;
      degreeAuditSummary.textContent = 'Academic standing and completion are not finalized until results are published.';
      document.querySelector('#missingCoursesTable tbody').innerHTML =
        '<tr><td colspan="3">Course completion details are pending publication.</td></tr>';
      return;
    }
  } catch (error) {
    transcriptSummary.textContent = error.message || 'Unable to load result publication status.';
    document.querySelector('#transcriptTable tbody').replaceChildren();
    degreeAuditSummary.textContent = 'Academic result status is unavailable.';
    document.querySelector('#missingCoursesTable tbody').replaceChildren();
    return;
  }

  try {
    const transcript = await apiGet(`/academic-records/${studentId}/transcript`);
    transcriptSummary.textContent = `${transcript.studentName} · CGPA ${transcript.cgpa ?? '—'}${status.awardClassification ? ` · ${status.awardClassification}` : ''} · ${transcript.courseCount} approved courses`;
    document.querySelector('#transcriptTable tbody').innerHTML = transcript.grades.length
      ? transcript.grades.map(grade => `<tr><td>${escapeHtml(grade.courseCode)} · ${escapeHtml(grade.courseName)}</td><td>${escapeHtml(grade.credits ?? '—')}</td><td>${escapeHtml(grade.grade)}</td><td>${escapeHtml(grade.gpaScore ?? '—')}</td><td>${escapeHtml(grade.approvedAt || '—')}</td></tr>`).join('')
      : '<tr><td colspan="5">No approved grades are available yet.</td></tr>';
    const download = document.getElementById('downloadTranscript');
    download.disabled = false;
    download.addEventListener('click', () => downloadPdf(
      `/academic-records/${studentId}/transcript/download`,
      `transcript-${studentId}.pdf`,
      'Transcript download is unavailable.',
    ));
  } catch (error) {
    transcriptSummary.textContent = error.message || 'Unable to load transcript.';
    document.querySelector('#transcriptTable tbody').replaceChildren();
  }

  try {
    const audit = await apiGet(`/academic-records/${studentId}/degree-audit`);
    degreeAuditSummary.textContent = `${audit.programName} · ${audit.completedRequiredCourses}/${audit.requiredCourses} required courses complete · ${audit.eligibleForGraduation ? 'Eligible for graduation' : 'Requirements outstanding'}`;
    document.querySelector('#missingCoursesTable tbody').innerHTML = audit.missingRequiredCourses.length
      ? audit.missingRequiredCourses.map(course => `<tr><td>${escapeHtml(course.courseCode)}</td><td>${escapeHtml(course.courseName)}</td><td>${escapeHtml(course.semesterNumber)}</td></tr>`).join('')
      : '<tr><td colspan="3">No missing required courses.</td></tr>';
  } catch (error) {
    degreeAuditSummary.textContent = error.message || 'Degree audit is unavailable.';
    document.querySelector('#missingCoursesTable tbody').replaceChildren();
  }
}

function renderTimetable(entries, courses) {
  const tab = document.getElementById('timetableTab');
  if (!tab) return;
  const names = Object.fromEntries(courses.map(course => [course.id, course.name]));
  const rows = entries.slice().sort((a, b) => `${a.dayOfWeek}${a.startsAt}`.localeCompare(`${b.dayOfWeek}${b.startsAt}`));
  const target = tab.querySelector('.timetable-container, .schedule-container') || tab;
  target.innerHTML = rows.length ? `<div class="table-container"><table class="table"><thead><tr><th>Day</th><th>Course</th><th>Time</th><th>Venue</th></tr></thead><tbody>${rows.map(entry => `<tr><td>${escapeHtml(entry.dayOfWeek)}</td><td>${escapeHtml(names[entry.courseId] || `Course #${entry.courseId}`)}</td><td>${escapeHtml(entry.startsAt)} - ${escapeHtml(entry.endsAt)}</td><td>${escapeHtml(entry.venue)}</td></tr>`).join('')}</tbody></table></div>` : '<p>No timetable entries have been published.</p>';
}

function renderExamResults(results, exams) {
  const tab = document.getElementById('examsTab');
  if (!tab) return;
  const byExam = Object.fromEntries(exams.map(exam => [exam.id, exam]));
  const resultSection = document.createElement('section');
  resultSection.className = 'exam-results-live';
  resultSection.innerHTML = `<h3>Published Results</h3>${results.length ? `<table class="table"><thead><tr><th>Exam</th><th>Score</th><th>Remarks</th></tr></thead><tbody>${results.map(result => `<tr><td>${escapeHtml(byExam[result.examId]?.title || `Exam #${result.examId}`)}</td><td>${result.score}/${result.maxScore}</td><td>${escapeHtml(result.remarks || '—')}</td></tr>`).join('')}</tbody></table>` : '<p>No exam results have been published.</p>'}`;
  tab.appendChild(resultSection);
}

function renderAssignments(assignments) {
  const list = document.querySelector('.assignments-list');
  if (!list) return;
  list.innerHTML = assignments.length ? assignments.map(assignment => `<article class="assignment-card"><div class="assignment-header"><h3>${escapeHtml(assignment.title)}</h3><span class="assignment-course">Course #${assignment.courseId}</span></div><div class="assignment-body"><p>${escapeHtml(assignment.description || 'No description')}</p><p class="due-date">Due: ${escapeHtml(assignment.dueAt)}</p></div><div class="assignment-actions"><button class="btn-secondary" onclick="viewAssignment(${assignment.id})">View Details</button><button class="btn-primary" onclick="submitAssignment(${assignment.id})">Submit</button></div></article>`).join('') : '<p>No assignments are currently published.</p>';
}

function renderExams(exams, permits = []) {
  const container = document.querySelector('.exams-container');
  if (!container) return;
  const permitByExam = Object.fromEntries(permits.map(permit => [permit.examId, permit]));
  container.innerHTML = exams.length ? `<div class="exam-card"><div class="exam-header"><h3>Published Examinations</h3></div><div class="exam-list">${exams.map(exam => {
    const permit = permitByExam[exam.id];
    const status = permit?.status || 'NOT_REQUESTED';
    const action = status === 'NOT_REQUESTED'
      ? `<button class="btn-secondary" type="button" onclick="requestExamPermit(${exam.id})">Request permit</button>`
      : `<span class="status-badge">Permit: ${escapeHtml(status)}</span>`;
    return `<div class="exam-item"><div class="exam-info"><p class="exam-course"><strong>${escapeHtml(exam.title)}</strong> (Course #${exam.courseId})</p><p class="exam-date">Exam: ${escapeHtml(exam.startsAt)}</p><p class="exam-location">Venue: ${escapeHtml(exam.venue)}</p>${action}</div></div>`;
  }).join('')}</div><button class="btn-secondary" onclick="downloadHallTicket()">Download Hall Tickets</button></div>` : '<p>No examinations are currently scheduled.</p>';
}

async function requestExamPermit(examId) {
  try {
    await apiFetch('/student/exam-permits', {
      method: 'POST',
      body: JSON.stringify({ examId }),
    });
    const [exams, permits] = await Promise.all([apiGet('/portal/student/exams'), apiGet('/student/exam-permits')]);
    renderExams(exams, permits);
  } catch (err) {
    alert(err.message || 'Exam permit request failed.');
  }
}

function renderStudentGradeStatuses(statuses, finalized) {
  const section = document.getElementById('provisionalGradeStatuses');
  const container = document.getElementById('provisionalGradeStatusList');
  if (!section || !container) return;

  section.hidden = finalized;
  if (finalized) {
    container.replaceChildren();
    return;
  }
  if (!Array.isArray(statuses) || statuses.length === 0) {
    container.innerHTML = '<p>No grade submissions are currently recorded.</p>';
    return;
  }

  const statusLabels = {
    PENDING: 'Awaiting review',
    APPROVED: 'Approved; awaiting official publication',
    REJECTED: 'Returned for revision',
  };
  container.innerHTML = `
    <table class="table"><thead><tr><th>Course Code</th><th>Course</th><th>Status</th></tr></thead>
      <tbody>${statuses.map(item => `<tr>
        <td>${escapeHtml(item.courseCode || '—')}</td>
        <td>${escapeHtml(item.courseName || '—')}</td>
        <td>${escapeHtml(statusLabels[item.status] || 'Status unavailable')}</td>
      </tr>`).join('')}</tbody>
    </table>`;
}

function renderCourseCards(courses, grades) {
  const grid = document.querySelector('.courses-grid');
  if (!grid) return;

  const gradeByCourse = {};
  grades.forEach(g => { gradeByCourse[g.courseName] = g; });

  if (!courses.length) {
    grid.innerHTML = `<p style="color:var(--text-secondary,#666); padding:1rem;">You're not enrolled in any courses yet.</p>`;
    return;
  }

  const grouped = courses.reduce((groups, course) => {
    const key = course.category || 'OTHER';
    (groups[key] ||= []).push(course);
    return groups;
  }, {});
  grid.innerHTML = Object.entries(grouped).map(([category, categoryCourses]) => `
    <section class="course-category-group"><h3>${escapeHtml(category === 'SCIENCES' ? 'Sciences' : category === 'ARTS' ? 'Arts' : 'Other Courses')}</h3>
    <div class="courses-grid">${categoryCourses.map(c => {
    const g = gradeByCourse[c.name];
    return `
      <div class="course-card">
        <div class="course-header">
          <h3>${escapeHtml(c.courseCode || '—')} · ${escapeHtml(c.name)}</h3>
        </div>
        <div class="course-info">
          <p><strong>Instructor:</strong> ${escapeHtml(c.teacherName || 'TBA')}</p>
          <p><strong>Semester:</strong> ${escapeHtml(c.semester || 'Not assigned')}</p>
          <p><strong>Category:</strong> ${escapeHtml(c.category || 'OTHER')}</p>
          <p><strong>Course units:</strong> ${c.credits == null ? '—' : c.credits}</p>
          <p><strong>Status:</strong> <span class="status-active">Active</span></p>
        </div>
        <div class="course-stats">
          <div class="stat">
            <span class="stat-label">Current Grade</span>
            <span class="stat-value">${g ? escapeHtml(g.gradeValue) : 'Not graded yet'}</span>
          </div>
        </div>
        <button class="btn-primary" type="button" onclick="viewCourseDetails(${c.id}, '${escapeHtml(c.name).replace(/'/g, "\\'")}')">View Details</button>
      </div>`;
    }).join('')}</div></section>`).join('');
  // Progress bar and Assignments count from the original static cards are omitted
  // here rather than faked - neither is tracked anywhere in the backend.
}

function renderGradesTable(grades, profile, resultStatus = null, studentStatus = null, appeals = [], gradeStatuses = []) {
  const gpaCards = document.querySelectorAll('.gpa-card .gpa-value');
  const finalized = resultStatus?.published === true;
  renderStudentGradeStatuses(gradeStatuses, finalized);
  const publicationStatus = document.getElementById('gradePublicationStatus');
  if (publicationStatus) {
    publicationStatus.className = finalized ? 'status-badge success'
      : resultStatus?.approved ? 'status-badge warning' : 'status-badge muted';
    publicationStatus.textContent = finalized
      ? `PUBLISHED · ${resultStatus.publishedAt || 'Official results'}`
      : resultStatus?.approved
        ? 'AWAITING ADMIN PUBLICATION'
        : resultStatus?.publicationStatus === 'CHANGES_REQUIRED'
          ? 'CHANGES REQUIRE REAPPROVAL'
          : 'PENDING ADMIN APPROVAL';
  }
  const noticeTitle = document.getElementById('gradeCgpaNoticeTitle');
  const noticeText = document.getElementById('gradeCgpaNoticeText');
  if (noticeTitle) noticeTitle.textContent = finalized ? 'OFFICIAL RESULTS PUBLISHED' : 'CGPA NOT YET AVAILABLE';
  if (noticeText) noticeText.textContent = finalized
    ? `Official results were published by ${resultStatus.publishedBy || 'the Academic Office'} on ${resultStatus.publishedAt || 'the date shown on your report'}. Academic standing: ${resultStatus.academicStandingStatus || 'NOT FINALIZED'}${resultStatus.awardClassification ? `. Award classification: ${resultStatus.awardClassification}` : ''}.`
    : 'Final grades, GPA, credits earned, and academic standing will be shown after results are approved and published by the Academic Office.';
  const missingCourses = document.getElementById('missingResultCourses');
  if (missingCourses) {
    const pending = Array.isArray(studentStatus?.missingResultCourses) ? studentStatus.missingResultCourses : [];
    missingCourses.hidden = finalized || pending.length === 0;
    missingCourses.textContent = pending.length
      ? `No result submission is recorded for: ${pending.map(escapeHtml).join(', ')}.`
      : '';
  }
  if (gpaCards[0]) gpaCards[0].textContent = profile.cgpa != null
    ? Number(profile.cgpa).toFixed(2) : finalized ? '—' : 'NOT FINALIZED';
  if (gpaCards[1]) gpaCards[1].textContent = !finalized ? 'NOT FINALIZED'
    : resultStatus?.semesterGpa == null ? '—' : Number(resultStatus.semesterGpa).toFixed(2);
  const semesterLabel = document.querySelectorAll('.gpa-card .gpa-range')[0];
  if (semesterLabel) semesterLabel.textContent = resultStatus?.latestSemester
    ? `Latest approved semester: ${resultStatus.latestSemester}` : 'Latest semester with approved grades';
  if (gpaCards[2]) gpaCards[2].textContent = finalized
    ? resultStatus?.creditsEarned == null ? '—' : String(resultStatus.creditsEarned)
    : 'NOT FINALIZED';

  const tbody = document.querySelector('.grades-table tbody');
  if (!tbody) return;

  if (!grades.length) {
    tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; color:var(--text-secondary,#666);">${finalized ? 'No published grades are available.' : 'Results are pending administrative approval and publication.'}</td></tr>`;
    return;
  }

  const activeAppealStatuses = new Set(['OPEN', 'TEACHER_REVIEW', 'READY_FOR_DECISION']);
  const activeGradeAppeals = new Set((Array.isArray(appeals) ? appeals : [])
    .filter(appeal => activeAppealStatuses.has(appeal.status))
    .map(appeal => Number(appeal.gradeId)));
  tbody.innerHTML = grades.map(g => {
    const components = Array.isArray(g.assessmentComponents) ? g.assessmentComponents : [];
    const display = (type) => {
      const matching = components.filter(component => component.componentType === type);
      return matching.length
        ? matching.map(component => `${escapeHtml(component.displayName)}: ${component.score == null ? 'MISSING' : `${escapeHtml(component.score)}/${escapeHtml(component.maxScore)}`}`).join('<br>')
        : '—';
    };
    return `
      <tr>
        <td>${escapeHtml(g.courseCode || '—')}</td>
        <td>${escapeHtml(g.courseName)}</td>
        <td>${display('COURSEWORK')}</td>
        <td>${display('FINAL_PAPER')}</td>
        <td>${display('OTHER')}</td>
        <td>${g.totalMark == null ? '—' : Number(g.totalMark).toFixed(2)}</td>
        <td><span class="grade-badge">${escapeHtml(g.grade ?? g.gradeValue ?? '—')}</span></td>
        <td>${g.gpaScore != null ? Number(g.gpaScore).toFixed(2) : '—'}</td>
        <td>${g.retakeRequired ? 'RETAKE REQUIRED · ' : ''}${escapeHtml(g.attemptType === 'RETAKE_SUBMITTED' ? `RETAKE SUBMITTED · attempt ${g.attemptNumber}` : g.status || 'PENDING')}</td>
        <td>${finalized && g.id
          ? activeGradeAppeals.has(Number(g.id))
            ? 'Appeal in progress'
            : `<button class="btn-small" type="button" onclick="openGradeAppeal(${Number(g.id)})">Request review</button>`
          : '—'}</td>
      </tr>`;
  }).join('');
}

function openGradeAppeal(gradeId) {
  const dialog = document.getElementById('gradeAppealDialog');
  if (!dialog) return;
  document.getElementById('gradeAppealGradeId').value = String(gradeId);
  document.getElementById('gradeAppealCourse').textContent = `Grade record #${gradeId}`;
  document.getElementById('gradeAppealReason').value = '';
  document.getElementById('gradeAppealFormStatus').textContent = '';
  dialog.showModal();
}

async function submitGradeAppeal(event) {
  event.preventDefault();
  const status = document.getElementById('gradeAppealFormStatus');
  const gradeId = Number(document.getElementById('gradeAppealGradeId').value);
  const reason = document.getElementById('gradeAppealReason').value.trim();
  if (!gradeId || !reason) {
    status.textContent = 'Enter a reason for requesting the review.';
    return;
  }
  try {
    await apiFetch('/student/grade-appeals', {
      method: 'POST',
      body: JSON.stringify({ gradeId, reason }),
    });
    document.getElementById('gradeAppealDialog').close();
    await loadAcademicsData();
  } catch (error) {
    status.textContent = error.message || 'Unable to submit the grade appeal.';
  }
}

function renderAnalytics(grades, attendance, profile = null) {
  const grid = document.querySelector('.analytics-grid');
  if (!grid) return;
  const present = attendance.filter(record => record.present || record.late).length;
  const attendancePercent = attendance.length ? Math.round((present / attendance.length) * 100) : null;
  const graded = grades.filter(grade => grade.gpaScore != null);
  const averageGpa = profile?.cgpa == null ? null : Number(profile.cgpa);
  const maxGpa = 5;
  grid.innerHTML = `
    <div class="analytics-card"><h3>Current Performance</h3><div class="attendance-stats"><div class="stat-circle"><p>${averageGpa == null ? '—' : averageGpa.toFixed(2)}</p><span>Official CGPA</span></div><div class="stat-circle"><p>${attendancePercent == null ? '—' : `${attendancePercent}%`}</p><span>Attendance</span></div></div></div>
    <div class="analytics-card"><h3>GPA By Course</h3><div class="performance-bars">${graded.length ? graded.map(grade => {
      const score = Number(grade.gpaScore);
      const percentage = Math.max(0, Math.min(100, Math.round((score / maxGpa) * 100)));
      return `<div class="performance-item"><span class="performance-label">${escapeHtml(grade.courseName)}</span><div class="performance-bar"><div class="performance-fill" style="width:${percentage}%"></div></div><span class="performance-percent">${score.toFixed(2)}</span></div>`;
    }).join('') : '<p>No GPA records are available yet.</p>'}</div></div>
    <div class="analytics-card"><h3>Grades Recorded</h3><div class="performance-bars">${grades.length ? grades.map(grade => `<div class="performance-item"><span class="performance-label">${escapeHtml(grade.courseName)}</span><span class="performance-percent">${escapeHtml(grade.gradeValue)}</span></div>`).join('') : '<p>No grades recorded yet.</p>'}</div></div>
    <div class="analytics-card"><h3>Attendance Records</h3><p>${attendance.length ? `${present} of ${attendance.length} recorded classes attended.` : 'No attendance records yet.'}</p></div>`;
}

async function loadAttendanceData() {
  const status = document.getElementById('attendanceLoadStatus');
  try {
    const records = await apiGet('/student/attendance');
    attendanceRecords = records;
    const courseFilter = document.querySelector('select[onchange*="filterByCourse"]');
    if (courseFilter) {
      const names = [...new Set(records.map(record => record.courseName))];
      courseFilter.innerHTML = '<option value="">All courses</option>' + names.map(name => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join('');
    }
    renderAttendanceOverview(records);
    renderAttendanceRecords(records);
    renderAttendanceTrend(records);
    await loadStudentAttendanceDisputes();
    if (status) status.textContent = records.length
      ? 'Attendance records loaded.'
      : 'No attendance records are available yet.';
  } catch (err) {
    console.error('Failed to load attendance data', err);
    attendanceRecords = [];
    if (status) status.textContent = 'Unable to load attendance records. Please refresh or try again later.';
    document.querySelectorAll('.summary-card .summary-value').forEach((value, index) => {
      value.textContent = '—';
    });
    document.querySelectorAll('.summary-card .summary-detail').forEach(detail => {
      detail.textContent = 'Attendance could not be loaded.';
    });
    const courses = document.querySelector('.attendance-courses');
    if (courses) courses.textContent = 'Attendance could not be loaded.';
    const alerts = document.querySelector('.alerts-container');
    if (alerts) alerts.textContent = 'Attendance status could not be loaded.';
    const tbody = document.querySelector('.attendance-table tbody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="5">Attendance records could not be loaded.</td></tr>';
    const chart = document.querySelector('.chart-placeholder');
    if (chart) chart.textContent = 'Attendance trend could not be loaded.';
  }
}

function renderAttendanceOverview(records) {
  const total = records.length;
  const presentCount = records.filter(r => r.present || r.late).length;
  const absentCount = total - presentCount;
  const overallPct = total ? Math.round((presentCount / total) * 100) : null;

  // Top 4 summary cards: Overall %, Present, Absent, Late Arrivals.
  const summaryValues = document.querySelectorAll('.summary-card .summary-value');
  const summaryDetails = document.querySelectorAll('.summary-card .summary-detail');
  if (summaryValues[0]) summaryValues[0].textContent = overallPct != null ? `${overallPct}%` : '—';
  if (summaryDetails[0]) summaryDetails[0].textContent = total ? `${presentCount} out of ${total} classes attended` : 'No records yet';
  if (summaryValues[1]) summaryValues[1].textContent = String(presentCount);
  if (summaryValues[2]) summaryValues[2].textContent = String(absentCount);
  const lateCount = records.filter(record => record.late === true).length;
  if (summaryValues[3]) summaryValues[3].textContent = String(lateCount);
  if (summaryDetails[3]) summaryDetails[3].textContent = 'recorded late arrivals';
  const alerts = document.querySelector('.alerts-container');
  if (alerts) {
    alerts.textContent = total
      ? 'Attendance totals reflect the records currently available for your account.'
      : 'Attendance status is unavailable until attendance records are added.';
  }

  // Build per-course cards from the recorded attendance rather than placeholders.
  const container = document.querySelector('.attendance-courses');
  if (!container) return;

  const byCourse = {};
  records.forEach(r => {
    (byCourse[r.courseName] ||= []).push(r);
  });
  const courseNames = Object.keys(byCourse);

  if (!courseNames.length) {
    container.innerHTML = `<p style="color:var(--text-secondary,#666);">No attendance recorded yet.</p>`;
    return;
  }

  container.innerHTML = courseNames.map(name => {
    const list = byCourse[name];
    const present = list.filter(r => r.present || r.late).length;
    const pct = Math.round((present / list.length) * 100);
    return `
      <div class="course-attendance">
        <div class="course-header-att">
          <div><p class="course-name">${escapeHtml(name)}</p></div>
          <div class="attendance-percent">${pct}%</div>
        </div>
        <div class="attendance-breakdown-bar">
          <div class="present-bar" style="width: ${pct}%"></div>
          <div class="absent-bar" style="width: ${100 - pct}%"></div>
        </div>
        <p class="attendance-detail">${present}/${list.length} attended</p>
      </div>`;
  }).join('');
}

function renderAttendanceTrend(records) {
  const chart = document.querySelector('.chart-placeholder');
  if (!chart) return;
  const byMonth = records.reduce((months, record) => {
    const month = String(record.date || '').slice(0, 7) || 'Unknown';
    const current = months[month] || { attended: 0, total: 0 };
    current.total += 1;
    if (record.present || record.late) current.attended += 1;
    months[month] = current;
    return months;
  }, {});
  const months = Object.entries(byMonth).sort(([left], [right]) => left.localeCompare(right)).slice(-6);
  chart.innerHTML = months.length
    ? `<div class="performance-bars">${months.map(([month, value]) => {
        const percentage = Math.round((value.attended / value.total) * 100);
        return `<div class="performance-item"><span class="performance-label">${escapeHtml(month)}</span><div class="performance-bar"><div class="performance-fill" style="width:${percentage}%"></div></div><span class="performance-percent">${percentage}%</span></div>`;
      }).join('')}</div>`
    : '<p>No attendance trend is available yet.</p>';
}

function renderAttendanceRecords(records) {
  const tbody = document.querySelector('.attendance-table tbody');
  if (!tbody) return;

  if (!records.length) {
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:var(--text-secondary,#666);">No attendance recorded yet.</td></tr>`;
    return;
  }

  tbody.innerHTML = records.map(r => {
    const startTime = r.classStartTime ? String(r.classStartTime).slice(0, 5) : null;
    const endTime = r.classEndTime ? String(r.classEndTime).slice(0, 5) : null;
    const time = startTime && endTime ? `${startTime} - ${endTime}` : startTime || endTime || '—';
    const status = r.late ? '⏱ Late' : r.present ? '✓ Present' : '✕ Absent';
    return `
    <tr>
      <td>${escapeHtml(r.date)}</td>
      <td>${escapeHtml(r.courseName)}</td>
      <td>${escapeHtml(time)}</td>
      <td><span class="status-badge ${r.present || r.late ? 'present-status' : 'absent-status'}">${status}</span></td>
      <td>${escapeHtml(r.remarks || '—')}<br><button class="btn-secondary" type="button" onclick="submitAttendanceDispute(${Number(r.id)})">Dispute</button></td>
    </tr>`;
  }).join('');
}

async function submitAttendanceDispute(attendanceId) {
  const reason = prompt('Explain why this attendance record is incorrect:');
  if (!reason) return;
  const requestedPresent = confirm('Request that the record be changed to PRESENT? Choose Cancel for ABSENT/LATE correction.');
  try {
    await apiFetch('/attendance/disputes', {
      method: 'POST',
      body: JSON.stringify({ attendanceId, reason, requestedPresent }),
    });
    alert('Attendance dispute submitted.');
    await loadStudentAttendanceDisputes();
  } catch (err) {
    alert(err.message || 'Attendance dispute could not be submitted.');
  }
}

async function loadStudentAttendanceDisputes() {
    const container = document.getElementById('attendanceDisputeStatus');
    if (!container) return;
    try {
      const disputes = await apiGet('/attendance/disputes');
      container.innerHTML = disputes.length ? disputes.map(dispute => `
        <article>
          <strong>${escapeHtml(dispute.courseName)} - ${escapeHtml(dispute.attendanceDate)}</strong>
          <span>Status: ${escapeHtml(dispute.status)}</span>
          ${dispute.resolution ? `<p>${escapeHtml(dispute.resolution)}</p>` : ''}
        </article>`).join('') : '<p>No attendance disputes submitted.</p>';
    } catch (err) {
      container.textContent = 'Attendance disputes could not be loaded.';
  }
}

async function loadFinanceData() {
  const results = await Promise.allSettled([
    apiGet('/fees'),
    apiGet('/portal/scholarships'),
    apiGet('/portal/student/payments'),
    apiGet('/portal/student/scholarship-applications'),
    apiGet('/finance/payments/attempts/mine'),
    apiGet('/finance/payments/destinations'),
  ]);
  const data = results.map((result, index) => {
    if (result.status === 'fulfilled') return result.value;
    console.error(`Failed to load finance data from request ${index + 1}`, result.reason);
    return null;
  });
  const [fees, scholarships, payments, applications, paymentAttempts, destinations] = data;

  window._studentFees = Array.isArray(fees) ? fees : [];
  window._scholarships = Array.isArray(scholarships) ? scholarships : [];
  window._studentPayments = Array.isArray(payments) ? payments : [];
  window._paymentAttempts = Array.isArray(paymentAttempts) ? paymentAttempts : [];
  window._paymentDestinations = Array.isArray(destinations) ? destinations : [];
  window._scholarshipApplications = Array.isArray(applications) ? applications : [];

  if (Array.isArray(fees)) {
    renderFinanceSummary(fees);
    renderPendingPayments(fees);
    renderInvoices(fees);
    if (!Array.isArray(destinations) || !destinations.length) {
      document.querySelectorAll('.finance-card.total-due button').forEach(button => { button.disabled = true; });
    }
  } else {
    showFinanceUnavailable('.finance-card .card-amount', '.fees-table tbody', 'Unable to load fee records.');
    showFinanceUnavailable(null, '.pending-payments', 'Unable to load pending fees.');
    showFinanceUnavailable(null, '.invoices-list', 'Unable to load invoices.');
    document.querySelectorAll('.finance-card .card-detail').forEach(el => { el.textContent = 'Fee data unavailable'; });
    document.querySelectorAll('.finance-card.total-due button').forEach(button => { button.disabled = true; });
  }

  if (Array.isArray(payments)) renderPaymentTransactions(payments);
  else showFinanceUnavailable(null, '.receipts-table tbody', 'Unable to load payment records.');

  if (Array.isArray(paymentAttempts)) renderPaymentAttempts(paymentAttempts);
  else showFinanceUnavailable(null, '#paymentAttemptsList', 'Unable to load payment verification status.');

  if (Array.isArray(scholarships) && Array.isArray(applications)) renderScholarships(scholarships, applications);
  else showFinanceUnavailable(null, '.scholarships-container', 'Unable to load scholarship records.');

}

function showFinanceUnavailable(amountSelector, contentSelector, message) {
  if (amountSelector) {
    document.querySelectorAll(amountSelector).forEach(el => { el.textContent = 'Unavailable'; });
  }
  const content = contentSelector && document.querySelector(contentSelector);
  if (!content) return;
  if (content.matches('tbody')) {
    const columns = content.closest('table')?.querySelectorAll('thead th').length || 1;
    content.innerHTML = `<tr><td colspan="${columns}">${escapeHtml(message)}</td></tr>`;
  } else {
    content.textContent = message;
  }
}

function renderInvoices(fees) {
  const container = document.querySelector('.invoices-list');
  if (!container) return;
  container.innerHTML = fees.length ? fees.map(fee => {
    const amountPaid = Number(fee.amountPaid ?? (fee.paid ? fee.amount : 0));
    const balance = Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
    const status = balance <= 0 ? 'Settled' : amountPaid > 0 ? 'Partially paid' : 'Pending';
    return `<article class="invoice-item"><div class="invoice-header"><p class="invoice-number">Invoice #${fee.id}</p><span class="invoice-amount">UGX ${Number(fee.amount).toLocaleString()}</span></div><div class="invoice-details"><p>${escapeHtml(fee.description)}</p><p>Due: ${escapeHtml(fee.dueDate || 'Not set')}</p><p>Paid: UGX ${amountPaid.toLocaleString()} · Balance: UGX ${balance.toLocaleString()}</p><p>Status: ${status}</p></div><div class="invoice-actions"><button class="btn-secondary" onclick="viewInvoice(${fee.id})">View</button><button class="btn-secondary" onclick="downloadInvoice(${fee.id})">Download</button>${balance > 0 ? `<button class="btn-primary" onclick="selectPaymentMethod(${fee.id})">Pay balance</button>` : ''}</div></article>`;
  }).join('') : '<p>No invoices found.</p>';
}

function renderScholarships(scholarships, applications = []) {
  const container = document.querySelector('.scholarships-container');
  if (!container) return;
  container.innerHTML = scholarships.length ? scholarships.map(item => {
    const application = applications.find(value => value.scholarshipId === item.id);
    return `<article class="scholarship-card"><div class="scholarship-header"><h3>${escapeHtml(item.name)}</h3><span class="scholarship-badge">${application ? escapeHtml(application.status) : (item.open ? 'Open' : 'Closed')}</span></div><div class="scholarship-body"><p>${escapeHtml(item.description || '')}</p><p>Amount: UGX ${Number(item.amount).toLocaleString()}</p>${application ? '<p>Application submitted.</p>' : (item.open ? `<button class="btn-primary" onclick="applyForScholarship(${item.id})">Apply</button>` : '')}</div></article>`;
  }).join('') : '<p>No scholarships available.</p>';
}

function renderPaymentTransactions(payments) {
  const tbody = document.querySelector('.receipts-table tbody');
  if (!tbody) return;
  tbody.innerHTML = payments.length ? payments.map(payment => `
    <tr><td>TX-${payment.id}</td><td>UGX ${Number(payment.amount).toLocaleString()}</td>
    <td>${escapeHtml((payment.createdAt || '').slice(0, 10))}</td>
    <td>${escapeHtml((payment.paymentChannel || 'PORTAL').replaceAll('_', ' '))}</td>
    <td>Fee #${Number(payment.feeId)}${payment.providerReference ? `<br><small>Ref: ${escapeHtml(payment.providerReference)}</small>` : ''}</td>
    <td><button class="btn-secondary small" onclick="downloadReceipt(${Number(payment.id)})">Download receipt</button></td></tr>`).join('') : '<tr><td colspan="6">No payment transactions found.</td></tr>';
}

function renderFinanceSummary(fees) {
  const balanceFor = fee => Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
  const amountPaidFor = fee => Number(fee.amountPaid ?? (fee.paid ? fee.amount : 0));
  const unpaid = fees.filter(fee => balanceFor(fee) > 0);
  const totalDue = fees.reduce((sum, fee) => sum + balanceFor(fee), 0);
  const totalPaid = fees.reduce((sum, fee) => sum + amountPaidFor(fee), 0);
  const nextDue = unpaid
    .filter(f => f.dueDate)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0];

  const cards = document.querySelectorAll('.finance-card');
  cards.forEach(card => {
    const amountEl = card.querySelector('.card-amount');
    const detailEl = card.querySelector('.card-detail');
    if (!amountEl) return;
    if (card.classList.contains('total-due')) {
      amountEl.textContent = `UGX ${totalDue.toLocaleString()}`;
      if (detailEl) detailEl.textContent = nextDue ? `Due by ${nextDue.dueDate}` : 'Nothing due';
      const payButton = card.querySelector('button');
      if (payButton) payButton.disabled = unpaid.length === 0;
    } else if (card.classList.contains('amount-paid')) {
      amountEl.textContent = `UGX ${totalPaid.toLocaleString()}`;
      if (detailEl) detailEl.textContent = 'All recorded payments';
    } else if (card.classList.contains('balance')) {
      // The original demo used this card for a credit balance, which the backend's
      // debt-only Fee model has no equivalent for - repurposed to show the same
      // outstanding total as "Total Fees Due" would double up, so instead this shows
      // how many fee items are still unpaid, which is genuinely new information.
      amountEl.textContent = String(unpaid.length);
      if (detailEl) detailEl.textContent = unpaid.length === 1 ? 'fee item outstanding' : 'fee items outstanding';
    } else if (card.classList.contains('transactions')) {
      amountEl.textContent = String(fees.length);
      if (detailEl) detailEl.textContent = 'Total fee records';
    }
  });

  const tbody = document.querySelector('.fees-table tbody');
  if (!tbody) return;
  if (!fees.length) {
    tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--text-secondary,#666);">No fees on record.</td></tr>`;
    return;
  }
  const rows = fees.map(f => `
    <tr>
      <td>${escapeHtml(f.description)}</td>
      <td>${Number(f.amount).toLocaleString()}</td>
      <td><span class="status-badge ${balanceFor(f) <= 0 ? 'paid-status' : 'pending-status'}">${balanceFor(f) <= 0 ? 'Settled' : amountPaidFor(f) > 0 ? 'Partially paid' : 'Pending'}</span></td>
    </tr>`);
  rows.push(`
    <tr class="total-row">
      <td><strong>Total</strong></td>
      <td><strong>UGX ${fees.reduce((sum, fee) => sum + Number(fee.amount), 0).toLocaleString()}</strong></td>
      <td><strong>${unpaid.length ? 'Due' : 'Settled'}</strong></td>
    </tr>`);
  tbody.innerHTML = rows.join('');
}

function renderPendingPayments(fees) {
  const container = document.querySelector('.pending-payments');
  if (!container) return;

  const unpaid = fees.filter(f => Number(f.balance ?? (f.paid ? 0 : f.amount)) > 0);
  if (!unpaid.length) {
    container.innerHTML = `<p style="color:var(--text-secondary,#666);">No pending payments. You're all caught up.</p>`;
    return;
  }

  container.innerHTML = unpaid.map(f => {
    const balance = Number(f.balance ?? (f.paid ? 0 : f.amount));
    const pendingAttempt = (window._paymentAttempts || []).some(attempt =>
      attempt.feeId === f.id && attempt.status === 'PENDING');
    return `
    <div class="payment-card">
      <div class="payment-header">
        <h3>${escapeHtml(f.description)}</h3>
        <span class="payment-amount">UGX ${balance.toLocaleString()} due</span>
      </div>
      <div class="payment-body">
        <div class="payment-detail">
          <span class="detail-label">Due Date:</span>
          <span class="detail-value">${escapeHtml(f.dueDate || 'Not set')}</span>
        </div>
        <p class="payment-description">Fee ID #${f.id}</p>
      </div>
      <div class="payment-actions">
        <button class="btn-primary" ${pendingAttempt ? 'disabled' : ''} onclick="selectPaymentMethod(${f.id})">${pendingAttempt ? 'Payment under review' : 'Choose payment method'}</button>
      </div>
    </div>`;
  }).join('');
  // "Days Remaining" from the original demo is omitted - it required client-side
  // date math the original code never actually did either (it was static text).
}

function renderPaymentAttempts(attempts) {
  const container = document.getElementById('paymentAttemptsList');
  if (!container) return;
  if (!attempts.length) {
    container.innerHTML = '<p>No payment requests have been submitted.</p>';
    return;
  }
  container.innerHTML = attempts.map(attempt => `
    <article class="payment-attempt-row">
      <div><strong>Fee #${Number(attempt.feeId)}</strong><span> ${escapeHtml(attempt.paymentChannel || 'BANK_TRANSFER').replaceAll('_', ' ')}</span></div>
      <div>UGX ${Number(attempt.amount).toLocaleString()}</div>
      <div>Reference: ${escapeHtml(attempt.providerReference)}</div>
      <span class="status-badge ${attempt.status === 'COMPLETED' ? 'paid-status' : 'pending-status'}">${escapeHtml(attempt.status)}</span>
    </article>`).join('');
}

async function loadProfileData() {
  try {
    const profile = await apiGet('/student/profile');
    const preferences = await apiGet('/portal/preferences');
    if (preferences.theme) setTheme(preferences.theme);
    const privacySettings = (() => {
      try {
        const raw = preferences?.settingsJson || '{}';
        return typeof raw === 'string' ? JSON.parse(raw) : raw;
      } catch (e) {
        console.warn('Saved portal preference settings are invalid JSON; using defaults.', e);
        return {};
      }
    })();
    portalPreferenceSettings = privacySettings && typeof privacySettings === 'object' && !Array.isArray(privacySettings)
      ? privacySettings
      : {};
    applyPreferenceCheckboxes(
      '#securityTab input[type="checkbox"]',
      portalPreferenceSettings.privacy,
      [true, true, false, true]
    );
    applyPreferenceCheckboxes(
      '#notificationPreferences input[type="checkbox"]',
      portalPreferenceSettings.notifications,
      [true, true, false, true, true, true]
    );
    applyPreferenceCheckboxes(
      '#quickLinkPreferences input[type="checkbox"]',
      portalPreferenceSettings.quickLinks,
      [true, true, false, false]
    );
    const languageSelect = document.querySelector('#preferencesTab .preference-select');
    if (languageSelect) languageSelect.value = preferences.language || 'English';
    const savedTheme = ['light', 'dark', 'system'].includes(preferences.theme)
      ? preferences.theme
      : 'light';
    const themeControl = document.querySelector(`#preferencesTab input[name="theme"][value="${savedTheme}"]`);
    if (themeControl) themeControl.checked = true;
    window.setSharedTheme?.(savedTheme);
    const profileUpdated = document.getElementById('profileLastChanged');
    if (profileUpdated) {
      profileUpdated.textContent = formatProfileDate(profile.updatedAt || profile.lastUpdated || new Date().toISOString());
    }
    // Personal Info tab: "Basic Details" form. Fields in DOM order are
    // First Name, Last Name, Contact Email, Phone, Date of Birth, Gender, Address, City,
    // Country - only Email, Phone, and Address have a backend equivalent (Date of
    // Birth and Gender aren't tracked anywhere in the Student entity at all, so
    // those two inputs are left with their original placeholder values).
    const basicInputs = document.querySelectorAll('#personalTab .form-grid input, #personalTab .form-grid select');
    const [firstName, lastName] = splitName(profile.fullName);
    if (basicInputs[0]) basicInputs[0].value = firstName;
    if (basicInputs[1]) basicInputs[1].value = lastName;
    if (basicInputs[2]) basicInputs[2].value = profile.email || '';
    if (basicInputs[3]) basicInputs[3].value = profile.phone || '';
    if (basicInputs[4]) basicInputs[4].value = profile.alternativePhone || '';
    if (basicInputs[5]) basicInputs[5].value = profile.dateOfBirth || '';
    if (basicInputs[6]) basicInputs[6].value = profile.gender || '';
    if (basicInputs[7]) basicInputs[7].value = profile.address || '';
    if (basicInputs[8]) basicInputs[8].value = profile.city || '';
    if (basicInputs[9]) basicInputs[9].value = profile.country || '';

    const contactSpans = document.querySelectorAll('#personalTab .contact-item span:not(.contact-label)');
    if (contactSpans[0]) contactSpans[0].textContent = profile.email || 'Not on file';
    if (contactSpans[1]) contactSpans[1].textContent = profile.phone || 'Not on file';
    if (contactSpans[2]) contactSpans[2].textContent = profile.alternativePhone || 'Not on file';
    if (contactSpans[3]) contactSpans[3].textContent = profile.address || 'Not on file';
    // "Alternative Phone" (index 2) has no backend equivalent - left as-is.

    // Academic Info tab: 8 info-cards in order (Reg. Number, Program, Year,
    // Semester, CGPA, Credits Completed, Status, Admission Date). Only Program
    // (approximated by className, the closest field available) and CGPA are real;
    // the rest aren't tracked anywhere in the backend.
    const infoValues = document.querySelectorAll('#academicTab .info-value');
    infoValues.forEach(value => { value.textContent = '—'; });
    if (infoValues[0]) infoValues[0].textContent = profile.registrationNumber || '—';
    if (infoValues[1]) infoValues[1].textContent = profile.className || '—';
    if (infoValues[4]) infoValues[4].textContent = profile.cgpa != null ? Number(profile.cgpa).toFixed(2) : '—';
    if (infoValues[6]) infoValues[6].innerHTML = '<span class="status-badge">Active</span>';
    if (infoValues[7]) infoValues[7].textContent = profile.enrollmentDate || '—';
    document.querySelectorAll('#academicTab .academic-table td:nth-child(2)').forEach(value => { value.textContent = '—'; });
    const programDetails = document.querySelectorAll('#academicTab .academic-table td:nth-child(2)');
    if (programDetails[0]) programDetails[0].textContent = profile.className || '—';

    loadServiceRequests('.requests-table tbody');
    loadDocuments();
  } catch (err) {
    console.error('Failed to load profile data', err);
  }
}

function formatProfileDate(value) {
  if (!value) return 'Not set';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

function splitName(fullName) {
  if (!fullName) return ['', ''];
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) return [parts[0], ''];
  return [parts[0], parts.slice(1).join(' ')];
}

function applyPreferenceCheckboxes(selector, savedValues, defaults) {
  document.querySelectorAll(selector).forEach((checkbox, index) => {
    const value = Array.isArray(savedValues) ? savedValues[index] : undefined;
    checkbox.checked = typeof value === 'boolean' ? value : defaults[index];
  });
}

// escapeHtml() is provided globally by auth.js.

// ===== ACADEMICS MODULE =====
async function viewCourseDetails(courseId, courseName) {
  try {
    const materials = await apiGet(`/materials/course/${courseId}`);
    const overlay = document.createElement('div');
    overlay.className = 'request-modal-overlay';
    overlay.innerHTML = `<div class="request-modal" role="dialog" aria-modal="true"><button type="button" class="modal-close" aria-label="Close">&times;</button><h2>${escapeHtml(courseName)}</h2><div class="course-material-list">${materials.length ? materials.map(material => `<div class="course-material-item"><strong>${escapeHtml(material.title)}</strong><span>${escapeHtml(material.description || 'Course material')}</span><button type="button" class="btn-secondary material-download" data-material-id="${material.id}">Download file</button></div>`).join('') : '<p>No course materials have been published yet.</p>'}</div></div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('.modal-close').addEventListener('click', () => overlay.remove());
    overlay.querySelectorAll('.material-download').forEach(button => button.addEventListener('click', () => {
      window.open(`${API}/materials/${button.dataset.materialId}/download`, '_blank', 'noopener');
    }));
  } catch (err) {
    alert(err.message || 'Course details could not be loaded.');
  }
}

function viewAssignment(assignmentId = null) {
  const assignment = (window._assignments || []).find(item => item.id === Number(assignmentId)) || (window._assignments || [])[0];
  alert(assignment ? `${assignment.title}\n${assignment.description || ''}\nDue: ${assignment.dueAt}` : 'No assignments are currently published.');
}

async function submitAssignment(assignmentId = null) {
  const assignment = (window._assignments || []).find(item => item.id === Number(assignmentId)) || (window._assignments || [])[0];
  if (!assignment) { alert('No assignments are currently published.'); return; }
  const dialog = document.getElementById('assignmentSubmissionDialog');
  const form = document.getElementById('assignmentSubmissionForm');
  const title = document.getElementById('assignmentSubmissionName');
  const content = document.getElementById('assignmentSubmissionContent');
  const status = document.getElementById('assignmentSubmissionStatus');
  if (!dialog || !form || !title || !content || !status) {
    console.error('Assignment submission form is unavailable.');
    alert('Assignment submission is unavailable. Please refresh the page and try again.');
    return;
  }
  title.textContent = assignment.title;
  content.value = '';
  status.textContent = '';
  dialog.dataset.assignmentId = String(assignment.id);
  dialog.showModal();
}

document.getElementById('cancelAssignmentSubmission')?.addEventListener('click', () => {
  document.getElementById('assignmentSubmissionDialog')?.close();
});

document.getElementById('assignmentSubmissionForm')?.addEventListener('submit', async event => {
  event.preventDefault();
  const dialog = document.getElementById('assignmentSubmissionDialog');
  const content = document.getElementById('assignmentSubmissionContent');
  const status = document.getElementById('assignmentSubmissionStatus');
  const assignmentId = Number(dialog?.dataset.assignmentId);
  const submission = content?.value.trim() || '';
  if (!assignmentId || !submission) {
    if (status) status.textContent = 'Enter your assignment response before submitting.';
    return;
  }
  const submitButton = event.target.querySelector('[type="submit"]');
  if (submitButton) submitButton.disabled = true;
  if (status) status.textContent = 'Submitting...';
  try {
    await apiFetch(`/portal/student/assignments/${assignmentId}/submissions`, {
      method: 'POST',
      body: JSON.stringify({ content: submission }),
    });
    dialog?.close();
    alert('Assignment submitted successfully.');
  } catch (err) {
    if (status) status.textContent = err.message || 'Assignment submission failed.';
  } finally {
    if (submitButton) submitButton.disabled = false;
  }
});

async function viewSubmission() {
  try {
    const submissions = await apiGet('/portal/student/submissions');
    if (!Array.isArray(submissions)) {
      throw new Error('The submissions response has an invalid format.');
    }
    const assignmentsById = Object.fromEntries((window._assignments || []).map(item => [item.id, item]));
    alert(submissions.length ? submissions.map(item => {
      const assignment = assignmentsById[item.assignmentId];
      const title = assignment?.title || `Assignment #${item.assignmentId}`;
      return `${title}\nSubmitted ${item.submittedAt}\n\n${item.content || '(No response text)'}`;
    }).join('\n\n---\n\n') : 'No submissions found.');
  } catch (err) { alert(err.message || 'Unable to load submissions.'); }
}

async function downloadHallTicket() {
  try {
    await downloadPdf('/academic/student/hall-ticket', 'hall-ticket.pdf', 'Hall ticket is not available.');
  } catch (err) { alert(err.message || 'Unable to create hall ticket.'); }
}

async function viewExamSchedule() {
  try {
    const exams = window._exams || await apiGet('/portal/student/exams');
    alert(exams.length ? exams.map(exam => `${exam.title} - ${exam.startsAt} - ${exam.venue}`).join('\n') : 'No exams are currently scheduled.');
  } catch (err) { alert(err.message || 'Unable to load exam schedule.'); }
}

// ===== ATTENDANCE MODULE =====
let attendanceRecords = [];

function filterByCourse(courseName) {
  const filtered = courseName ? attendanceRecords.filter(record => record.courseName === courseName) : attendanceRecords;
  renderAttendanceRecords(filtered);
}

async function downloadReport(type) {
  try {
    let records = attendanceRecords;
    const studentId = attendanceRecords[0]?.studentId;
    if (studentId && type !== 'trend') {
      const endDate = new Date();
      const monthsBack = type === 'monthly' ? 1 : 6;
      const targetMonth = new Date(endDate.getFullYear(), endDate.getMonth() - monthsBack, 1);
      const lastDayInTargetMonth = new Date(
        targetMonth.getFullYear(), targetMonth.getMonth() + 1, 0
      ).getDate();
      const startDate = new Date(
        targetMonth.getFullYear(),
        targetMonth.getMonth(),
        Math.min(endDate.getDate(), lastDayInTargetMonth)
      );
      const formatDate = date => [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, '0'),
        String(date.getDate()).padStart(2, '0'),
      ].join('-');
      records = await apiGet(`/attendance/range?studentId=${studentId}&startDate=${formatDate(startDate)}&endDate=${formatDate(endDate)}`);
    }
    downloadCsv(`attendance-${type}.csv`, records.map(record => ({
      date: record.date,
      courseName: record.courseName,
      classStartTime: record.classStartTime || '',
      classEndTime: record.classEndTime || '',
      status: record.late ? 'Late' : record.present ? 'Present' : 'Absent',
      remarks: record.remarks || '',
    })));
  } catch (err) {
    alert(err.message || `Unable to download ${type} attendance report.`);
  }
}

// ===== FINANCE MODULE =====
async function initiatePayment() {
  try {
    const fees = await apiGet('/fees');
    const unpaid = fees.find(fee => Number(fee.balance ?? (fee.paid ? 0 : fee.amount)) > 0);
    if (unpaid) await selectPaymentMethod(unpaid.id);
    else alert('There are no pending fees to pay.');
  } catch (err) { alert(err.message || 'Unable to load fees.'); }
}

async function selectPaymentMethod(type) {
  const feeId = Number(type);
  const fee = (window._studentFees || []).find(item => item.id === feeId);
  if (!fee) { alert('Fee is not available in the current records.'); return; }
  const balance = Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
  if (balance <= 0) { alert('This fee is already settled.'); return; }
  const dialog = document.getElementById('paymentDialog');
  if (!dialog) return;
  window._selectedPaymentFee = fee;
  document.getElementById('paymentFeeSummary').innerHTML = `<strong>${escapeHtml(fee.description)}</strong><span>Balance due: UGX ${balance.toLocaleString()}</span>`;
  document.getElementById('providerReference').value = '';
  document.getElementById('paymentFormStatus').textContent = '';
  document.getElementById('submitPayment').disabled = true;
  renderPaymentChannels();
  dialog.showModal();
}

function renderPaymentChannels() {
  const container = document.getElementById('paymentChannels');
  const instructions = document.getElementById('selectedPaymentInstructions');
  const submit = document.getElementById('submitPayment');
  const referenceGroup = document.getElementById('manualPaymentReferenceGroup');
  const referenceInput = document.getElementById('providerReference');
  const destinations = window._paymentDestinations || [];
  container.innerHTML = destinations.map(destination => `
    <label class="payment-channel-option ${destination.available ? '' : 'unavailable'}">
      <input type="radio" name="paymentChannel" value="${escapeHtml(destination.channel)}" ${destination.available ? '' : 'disabled'}>
      <span><strong>${escapeHtml(destination.label)}</strong><small>${destination.available ? escapeHtml(destination.destination) : escapeHtml(destination.destination)}</small></span>
      <span class="channel-state">${destination.available ? 'Available' : destination.channel === 'MASTERCARD' ? 'Gateway setup required' : 'Not configured'}</span>
    </label>`).join('');
  container.querySelectorAll('input[name="paymentChannel"]').forEach(input => {
    input.addEventListener('change', () => {
      const destination = destinations.find(item => item.channel === input.value);
      if (!destination) return;
      instructions.innerHTML = `<strong>${escapeHtml(destination.destination)}</strong><p>${escapeHtml(destination.instructions)}</p>`;
      const isFlutterwave = destination.channel === 'FLUTTERWAVE';
      referenceGroup.hidden = isFlutterwave;
      referenceInput.required = !isFlutterwave;
      submit.textContent = isFlutterwave ? 'Continue to secure checkout' : 'Submit for verification';
      submit.disabled = false;
    });
  });
  referenceGroup.hidden = false;
  referenceInput.required = true;
  submit.textContent = 'Submit for verification';
  instructions.innerHTML = '<p>Select an available channel to view its destination and instructions.</p>';
  submit.disabled = true;
}

function setupPaymentDialog() {
  const dialog = document.getElementById('paymentDialog');
  const close = () => dialog?.close();
  document.getElementById('closePaymentDialog')?.addEventListener('click', close);
  document.getElementById('cancelPayment')?.addEventListener('click', close);
  document.getElementById('paymentReferenceForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const fee = window._selectedPaymentFee;
    const channel = document.querySelector('input[name="paymentChannel"]:checked')?.value;
    const providerReference = document.getElementById('providerReference').value.trim();
    const status = document.getElementById('paymentFormStatus');
    const submit = document.getElementById('submitPayment');
    const isFlutterwave = channel === 'FLUTTERWAVE';
    if (!fee || !channel || (!isFlutterwave && !providerReference)) return;
    submit.disabled = true;
    status.textContent = isFlutterwave ? 'Creating your secure checkout…' : 'Submitting payment request…';
    try {
      const response = await apiFetch(isFlutterwave
        ? '/finance/payments/flutterwave/checkout'
        : '/finance/payments/attempts', {
        method: 'POST',
        body: JSON.stringify(isFlutterwave
          ? { feeId: fee.id }
          : { feeId: fee.id, amount: Number(fee.balance ?? (fee.paid ? 0 : fee.amount)), paymentChannel: channel, providerReference }),
      });
      if (isFlutterwave) {
        const checkout = await response.json();
        const checkoutUrl = new URL(checkout.checkoutUrl);
        if (checkoutUrl.protocol !== 'https:' || checkoutUrl.hostname !== 'checkout.flutterwave.com') {
          throw new Error('The secure checkout link could not be validated.');
        }
        status.textContent = 'Redirecting to Flutterwave secure checkout…';
        window.location.assign(checkoutUrl.href);
        return;
      }
      status.textContent = 'Submitted. Your fee remains pending until finance verifies the payment.';
      await loadFinanceData();
      window.setTimeout(() => dialog.close(), 900);
    } catch (err) {
      status.textContent = err.message || 'Payment request could not be submitted.';
      submit.disabled = false;
    }
  });
}

async function handleFlutterwaveReturn() {
  const params = new URLSearchParams(window.location.search);
  if (params.get('payment') !== 'return') return;

  const status = document.getElementById('flutterwaveReturnStatus');
  const txRef = params.get('tx_ref');
  const transactionId = Number(params.get('transaction_id'));
  window.history.replaceState({}, document.title, `${window.location.pathname}${window.location.hash}`);
  if (!txRef || !Number.isSafeInteger(transactionId) || transactionId <= 0) {
    if (status) status.textContent = 'Checkout was not completed or no transaction ID was returned. The fee has not been marked paid.';
    return;
  }

  if (status) status.textContent = 'Verifying the payment directly with Flutterwave…';
  try {
    const response = await apiFetch('/finance/payments/flutterwave/verify', {
      method: 'POST',
      body: JSON.stringify({ txRef, transactionId }),
    });
    const attempt = await response.json();
    const providerStatus = (params.get('status') || '').toLowerCase();
    if (status) {
      status.textContent = attempt.status === 'COMPLETED'
        ? 'Payment verified and applied to your fee.'
        : attempt.status === 'FAILED'
          ? 'Flutterwave reports that this payment did not complete. The fee remains unpaid.'
          : ['failed', 'cancelled'].includes(providerStatus)
            ? 'Flutterwave did not complete this attempt. Your fee remains unpaid; retry from the finance page if the checkout is still active.'
          : 'Payment is still processing. Your fee will update after Flutterwave confirms it.';
    }
    await loadFinanceData();
  } catch (error) {
    if (status) {
      status.textContent = `${error.message || 'Payment verification is unavailable.'} Your fee has not been marked paid.`;
    }
  }
}

function viewPaymentDetails() {
  const fee = (window._studentFees || []).find(item => Number(item.balance ?? (item.paid ? 0 : item.amount)) > 0)
    || (window._studentFees || [])[0];
  if (!fee) { alert('No fee records found.'); return; }
  const amountPaid = Number(fee.amountPaid ?? (fee.paid ? fee.amount : 0));
  const balance = Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
  alert(`${fee.description}\nAmount: UGX ${Number(fee.amount).toLocaleString()}\nPaid: UGX ${amountPaid.toLocaleString()}\nBalance: UGX ${balance.toLocaleString()}\nStatus: ${balance <= 0 ? 'Settled' : amountPaid > 0 ? 'Partially paid' : 'Pending'}\nDue: ${fee.dueDate || 'Not set'}`);
}

function viewInvoice(invoiceId) {
  const fee = (window._studentFees || []).find(item => String(item.id) === String(invoiceId))
    || (window._studentFees || []).find(item => item.description?.toLowerCase().includes(String(invoiceId).toLowerCase()));
  if (!fee) {
    alert(`Invoice ${invoiceId} is not available in the current fee records.`);
    return;
  }
  const amountPaid = Number(fee.amountPaid ?? (fee.paid ? fee.amount : 0));
  const balance = Number(fee.balance ?? (fee.paid ? 0 : fee.amount));
  const status = balance <= 0 ? 'Settled' : amountPaid > 0 ? 'Partially paid' : 'Pending';
  alert(`${fee.description}\nAmount: UGX ${Number(fee.amount).toLocaleString()}\nPaid: UGX ${amountPaid.toLocaleString()}\nBalance: UGX ${balance.toLocaleString()}\nStatus: ${status}`);
}

function downloadInvoice(invoiceId) {
  const fee = (window._studentFees || []).find(item => String(item.id) === String(invoiceId))
    || (window._studentFees || []).find(item => item.description?.toLowerCase().includes(String(invoiceId).toLowerCase()));
  if (!fee) { alert('Invoice is not available in the current fee records.'); return; }
  downloadPdf(`/fees/${fee.id}/invoice`, `invoice-${fee.id}.pdf`, 'Invoice is not available.');
}

function downloadReceipt(receiptId) {
  const paymentId = Number(receiptId);
  if (!paymentId) { alert('Receipt is not available in the current payment records.'); return; }
  downloadPdf(`/fees/receipts/${paymentId}`, `receipt-${paymentId}.pdf`, 'Receipt is not available.');
}

async function downloadPdf(path, filename, fallbackMessage) {
  try {
    const response = await apiFetch(path);
    await downloadResponse(response, filename);
  } catch (err) {
    alert(err.message || fallbackMessage);
  }
}

function viewMoreScholarships() {
  const items = window._scholarships || [];
  const open = items.filter(item => item.open);
  if (!open.length) { alert('No scholarships are currently open.'); return; }
  const choice = prompt(`Available scholarships:\n${open.map(item => `${item.id}: ${item.name} - UGX ${Number(item.amount).toLocaleString()}`).join('\n')}\n\nEnter the scholarship ID to apply:`);
  if (choice) applyForScholarship(Number(choice));
}

async function applyForScholarship(id) {
  try {
    await apiFetch(`/portal/student/scholarships/${id}/apply`, { method: 'POST' });
    alert('Scholarship application submitted.');
  } catch (err) { alert(err.message || 'Scholarship application failed.'); }
}

// ===== PROFILE MODULE =====
function editProfile() {
  const form = document.querySelector('#personalTab .form-grid');
  if (!form) return;
  const fields = form.querySelectorAll('input, select');
  fields.forEach(field => { field.disabled = false; });

  let saveButton = document.getElementById('saveProfileButton');
  if (!saveButton) {
    saveButton = document.createElement('button');
    saveButton.id = 'saveProfileButton';
    saveButton.className = 'btn-primary';
    saveButton.type = 'button';
    saveButton.textContent = 'Save Profile';
    saveButton.addEventListener('click', saveStudentProfile);
    form.parentElement.appendChild(saveButton);
  }
  saveButton.hidden = false;
}

async function saveStudentProfile() {
  const fields = document.querySelectorAll('#personalTab .form-grid input, #personalTab .form-grid select');
  const [firstName, lastName, email, phone, alternativePhone, dateOfBirth, gender, address, city, country] = Array.from(fields);
  try {
    await apiFetch('/student/profile', {
      method: 'PUT',
      body: JSON.stringify({
        fullName: `${firstName.value.trim()} ${lastName.value.trim()}`.trim(),
        email: email.value.trim(),
        phone: phone.value.trim(),
        address: address.value.trim(),
        alternativePhone: alternativePhone.value.trim(),
        dateOfBirth: dateOfBirth.value,
        gender: gender.value,
        city: city.value.trim(),
        country: country.value.trim(),
      }),
    });
    alert('Profile updated successfully.');
    await loadProfileData();
    document.querySelectorAll('#personalTab .form-grid input, #personalTab .form-grid select')
      .forEach(field => { field.disabled = true; });
    const saveButton = document.getElementById('saveProfileButton');
    if (saveButton) saveButton.hidden = true;
  } catch (err) {
    alert(err.message || 'Profile update failed.');
  }
}

function editContact() {
  editProfile();
}

function uploadDocument() {
  const input = document.getElementById('documentFileInput');
  if (!input) return;
  input.onchange = async () => {
    let file = input.files[0];
    if (!file) return;
    const name = prompt('Document name:', file.name);
    if (!name) return;
    try {
      file = validateUploadFile(file, ['pdf', 'jpg', 'jpeg', 'png', 'gif']);
    } catch (err) {
      alert(err.message);
      input.value = '';
      return;
    }
    const form = new FormData();
    form.append('name', name.trim());
    form.append('file', file);
    try {
      const response = await apiFetch('/student/documents', { method: 'POST', body: form });
      if (!response.ok) {
        throw new Error(await getResponseErrorMessage(response, 'Document upload failed. Please try again shortly.'));
      }
      alert('Document uploaded successfully.');
      await loadDocuments();
    } catch (err) { alert(err.message || 'Document upload failed.'); }
    input.value = '';
  };
  input.click();
}

async function downloadDocument() {
  try {
    const documents = await apiGet('/student/documents');
    if (!documents.length) {
      alert('No documents are available to download.');
      return;
    }
    await downloadStudentDocument(documents[0].id);
  } catch (err) {
    alert(err.message || 'Unable to load documents.');
  }
}

async function loadDocuments() {
  const list = document.getElementById('documentsList');
  if (!list) return;
  try {
    const documents = await apiGet('/student/documents');
    list.innerHTML = documents.length ? documents.map(document => `
      <div class="document-item">
        <div class="document-icon">📄</div>
        <div class="document-info">
          <p class="document-name">${escapeHtml(document.name)}</p>
          <p class="document-date">Uploaded: ${escapeHtml((document.uploadedAt || '').slice(0, 10))}</p>
          <p class="document-status">${escapeHtml(document.status)}</p>
        </div>
          <button class="btn-secondary small" onclick="downloadStudentDocument(${document.id})">📥</button><button class="btn-secondary small" onclick="deleteStudentDocument(${document.id})">🗑</button>
      </div>`).join('') : '<p class="document-empty">No documents uploaded yet.</p>';
  } catch (err) {
    list.innerHTML = `<p class="document-empty">${escapeHtml(err.message)}</p>`;
  }
}

async function downloadStudentDocument(id) {
  try {
    const response = await apiFetch(`/student/documents/${id}/download`);
    await downloadResponse(response, `student-document-${id}`);
  } catch (err) { alert(err.message || 'Document download failed.'); }
}

async function deleteStudentDocument(id) {
  if (!confirm('Delete this document?')) return;
  try { await apiFetch(`/student/documents/${id}`, { method: 'DELETE' }); await loadDocuments(); }
  catch (err) { alert(err.message || 'Document deletion failed.'); }
}

function requestService(serviceType) {
  const serviceNames = {
    'idcard': 'Student ID Card',
    'transcript': 'Academic Transcript',
    'reference': 'Reference Letter',
    'hostel': 'Hostel Accommodation',
    'transport': 'Transport Pass'
  };

  openServiceRequestForm(serviceType, serviceNames[serviceType]);
}

function openServiceRequestForm(type, title) {
  const modal = document.createElement('div');
  modal.className = 'request-modal-overlay';
  modal.innerHTML = `<div class="request-modal" role="dialog" aria-modal="true" aria-labelledby="requestModalTitle">
    <button type="button" class="modal-close" aria-label="Close">&times;</button>
    <h2 id="requestModalTitle">${escapeHtml(title)} request</h2>
    <form id="serviceRequestForm">
      <label for="requestSubject">Subject</label>
      <input id="requestSubject" name="subject" value="${escapeHtml(title)}" maxlength="255" required>
      <label for="requestDetails">Details</label>
      <textarea id="requestDetails" name="details" rows="5" maxlength="2000" placeholder="Add the information the registrar needs"></textarea>
      <div class="request-form-actions"><button type="button" class="btn-secondary modal-cancel">Cancel</button><button type="submit" class="btn-primary">Submit request</button></div>
    </form>
  </div>`;
  document.body.appendChild(modal);
  const close = () => modal.remove();
  modal.querySelector('.modal-close').addEventListener('click', close);
  modal.querySelector('.modal-cancel').addEventListener('click', close);
  modal.querySelector('form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = new FormData(event.target);
    await createServiceRequest(type, form.get('subject'), form.get('details'));
    close();
  });
  modal.querySelector('#requestSubject').focus();
}

function trackRequest(id) {
  const request = (window._requestsCache || []).find(item => item.id === id) || (window._requestsCache || [])[0];
  alert(request ? `Request #${request.id}\n${request.subject}\nStatus: ${request.status}` : 'No service requests found.');
}

function downloadRequest(id) {
  const request = (window._requestsCache || []).find(item => item.id === id) || (window._requestsCache || [])[0];
  if (!request) { alert('No service requests found.'); return; }
  downloadPdf(`/student/requests/${request.id}/download`, `request-${request.id}.pdf`, 'Request document is not available.');
}

function changePassword() {
  const currentPassword = prompt('Enter your current password:');
  if (!currentPassword) return;
  const newPassword = prompt('Enter a new password (at least 8 characters):');
  if (!newPassword) return;
  apiFetch('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
    .then(() => alert('Password changed. Please sign in again.'))
    .then(() => logout('index.html'))
    .catch(err => alert(err.message || 'Password change failed.'));
}

function disableMFA() {
  if (!confirm('Disable two-factor authentication?')) return;
  apiFetch('/auth/mfa/disable', { method: 'POST' })
    .then(() => alert('Two-factor authentication disabled.'))
    .catch(err => alert(err.message || 'Unable to update MFA.'));
}

function savePrivacySettings() {
  const values = Array.from(document.querySelectorAll('#securityTab input[type="checkbox"]')).map(input => input.checked);
  savePortalPreferences({ settings: { privacy: values } });
}

function changeLanguage(lang) {
  savePortalPreferences({ language: lang });
}

function savePreferences() {
  const theme = document.querySelector('input[name="theme"]:checked')?.value || 'light';
  const [notifications, quickLinks] = [
    document.querySelectorAll('#notificationPreferences input[type="checkbox"]'),
    document.querySelectorAll('#quickLinkPreferences input[type="checkbox"]'),
  ].map(group => Array.from(group, input => input.checked));
  savePortalPreferences({ theme, settings: { notifications, quickLinks } })
    .then(saved => {
      if (saved) window.setSharedTheme?.(theme);
    });
}

function savePortalPreferences(payload) {
  const operation = portalPreferenceSaveQueue.then(async () => {
    const settings = { ...portalPreferenceSettings, ...(payload.settings || {}) };
    const request = { ...payload, settingsJson: JSON.stringify(settings) };
    delete request.settings;
    try {
      await apiFetch('/portal/preferences', { method: 'PUT', body: JSON.stringify(request) });
      portalPreferenceSettings = settings;
      alert('Preferences saved.');
      return true;
    } catch (err) {
      alert(err.message || 'Preferences could not be saved.');
      return false;
    }
  });
  portalPreferenceSaveQueue = operation;
  return operation;
}

// ===== CAMPUS LIFE MODULE =====
function registerEvent(button) {
  const eventName = button?.closest('.event-card')?.querySelector('h3')?.textContent || 'Campus event';
  const eventId = button?.closest('.event-card')?.dataset.eventId;
  if (!eventId) return createServiceRequest('EVENT_REGISTRATION', eventName, 'Student event registration request');
  apiFetch(`/portal/student/events/${eventId}/register`, { method: 'POST' })
    .then(() => loadCampusData())
    .catch(err => alert(err.message || 'Event registration failed.'));
}

function cancelEventRegistration(button) {
  const eventId = button?.closest('.event-card')?.dataset.eventId;
  if (!eventId) return;
  apiFetch(`/portal/student/events/${eventId}/registration`, { method: 'DELETE' })
    .then(() => loadCampusData())
    .catch(err => alert(err.message || 'Event registration could not be cancelled.'));
}

function viewEventDetails(button) {
  const card = button?.closest('.event-card');
  const event = (window._campusEvents || []).find(item => item.id === Number(card?.dataset.eventId));
  alert(event ? `${event.title}\n${event.description || ''}\n${event.startsAt}\n${event.venue}` : (card ? card.innerText : 'Select an event to view its details.'));
}

function joinClub(clubName) {
  const club = (window._clubs || []).find(item => item.slug === clubName || item.name.toLowerCase().includes(clubName.toLowerCase()));
  if (club) return toggleClubMembership(club.id, club.joined);
  createServiceRequest('CLUB_MEMBERSHIP', `${clubName} club membership`, 'Student club membership request');
}

async function toggleClubMembership(clubId, joined) {
  try {
    await apiFetch(`/portal/student/clubs/${clubId}/${joined ? 'leave' : 'join'}`, { method: joined ? 'DELETE' : 'POST' });
    await loadCampusData();
  } catch (err) { alert(err.message || 'Unable to update club membership.'); }
}

function viewRoute(routeNumber) {
  apiGet('/portal/routes').then(routes => {
    const card = document.querySelectorAll('.route-card')[Number(routeNumber) - 1];
    const route = routes.find(item => item.id === Number(card?.dataset.routeId)) || routes[Number(routeNumber) - 1];
    alert(route ? `${route.name}\nStops: ${route.stops}` : `Transport route ${routeNumber} is not available.`);
  }).catch(err => alert(err.message || 'Unable to load transport routes.'));
}

function requestHostel() {
  const details = prompt('Add accommodation preferences or requirements (optional):');
  if (details === null) return;
  if (details.length > 2000) { alert('Accommodation details must be 2,000 characters or fewer.'); return; }
  apiFetch('/campus/student/hostel', { method: 'POST', body: JSON.stringify({ details }) }).then(() => loadCampusData()).catch(err => alert(err.message || 'Hostel request failed.'));
}

function renewPass() {
  apiFetch('/campus/student/transport-pass/renew', { method: 'POST' }).then(() => loadCampusData()).catch(err => alert(err.message || 'Transport pass renewal failed.'));
}

// ===== HELPDESK MODULE =====
function submitTicket() {
  const subject = prompt('What do you need help with?');
  if (!subject) return;
  const details = prompt('Add more details (optional):');
  if (details !== null) createServiceRequest('SUPPORT_TICKET', subject, details);
}

async function viewTicket(id) {
  const request = (window._requestsCache || []).find(item => item.id === id);
  if (!request) { alert('Ticket not found.'); return; }
  try {
    const detail = await apiGet(`/student/requests/${id}`);
    alert(`#REQ-${detail.id}\n${detail.subject}\nType: ${detail.type}\nStatus: ${detail.status}\n\n${detail.details || 'No details provided.'}`);
  } catch (err) { alert(err.message || 'Unable to load ticket details.'); }
}

async function createServiceRequest(type, subject, details) {
  try {
    await apiFetch('/student/requests', {
      method: 'POST',
      body: JSON.stringify({ type, subject, details }),
    });
    alert('Request submitted successfully.');
    await loadServiceRequests('.tickets-table tbody');
    await loadServiceRequests('.requests-table tbody');
  } catch (err) {
    alert(err.message || 'Request submission failed.');
  }
}

async function loadServiceRequests(selector) {
  const tbody = document.querySelector(selector);
  if (!tbody) return;
  try {
    const requests = await apiGet('/student/requests');
    window._requestsCache = requests;
    if (!requests.length) {
      tbody.innerHTML = '<tr><td colspan="6">No requests submitted yet.</td></tr>';
      return;
    }
    if (selector.includes('tickets-table')) {
      tbody.innerHTML = requests.map(request => `
        <tr><td>#REQ-${request.id}</td><td>${escapeHtml(request.subject)}</td>
        <td>${escapeHtml(request.type)}</td><td><span class="status-badge">${escapeHtml(request.status)}</span></td>
        <td>${escapeHtml((request.createdAt || '').slice(0, 10))}</td>
        <td><button class="btn-secondary small" onclick="viewTicket(${request.id})">View</button></td></tr>`).join('');
    } else {
      tbody.innerHTML = requests.map(request => `
        <tr><td>#REQ-${request.id}</td><td>${escapeHtml(request.subject)}</td>
        <td>${escapeHtml((request.createdAt || '').slice(0, 10))}</td>
        <td><span class="status-badge">${escapeHtml(request.status)}</span></td>
        <td><button class="btn-secondary small" onclick="trackRequest(${request.id})">Track</button></td></tr>`).join('');
    }
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6">${escapeHtml(err.message)}</td></tr>`;
  }
}

// ===== FAQ MODULE =====
function toggleFAQ(element) {
  const answer = element.nextElementSibling;
  const arrow = element.querySelector('.faq-arrow');
  
  answer.style.display = answer.style.display === 'block' ? 'none' : 'block';
  arrow.style.transform = answer.style.display === 'block' ? 'rotate(90deg)' : 'rotate(0)';
}

// ===== CHAT SUPPORT =====
function sendChatMessage() {
  const input = document.getElementById('chatInput');
  const message = input.value.trim();
  
  if (!message) return;
  if (message.length > 5000) { alert('Support messages must be 5,000 characters or fewer.'); return; }

  // Add user message
  addChatMessage(message, 'user');
  input.value = '';

  apiFetch('/campus/student/chat', { method: 'POST', body: JSON.stringify({ content: message }) })
    .then(response => response.json())
    .then(reply => addChatMessage(reply.content || 'Your question was sent to the support queue.', 'bot'))
    .catch(() => addChatMessage('Your question could not be submitted. Please try the Help Desk.', 'bot'));
}

function addChatMessage(text, sender) {
  const messagesContainer = document.getElementById('chatMessages');
  const messageDiv = document.createElement('div');
  messageDiv.className = `chat-message ${sender}`;

  const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  // Built via DOM APIs + textContent, not innerHTML string interpolation - "text"
  // is raw, unescaped user input (this used to go straight into innerHTML, so
  // typing something like <img src=x onerror=alert(1)> into the chat box would
  // execute immediately - a real, live XSS sink, not just a theoretical one).
  const contentDiv = document.createElement('div');
  contentDiv.className = 'message-content';
  contentDiv.textContent = text;

  const timeSpan = document.createElement('span');
  timeSpan.className = 'message-time';
  timeSpan.textContent = time;

  messageDiv.appendChild(contentDiv);
  messageDiv.appendChild(timeSpan);

  messagesContainer.appendChild(messageDiv);
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function askQuestion(question) {
  document.getElementById('chatInput').value = question;
  sendChatMessage();
}

function handleChatSubmit(event) {
  if (event.key === 'Enter') {
    sendChatMessage();
  }
}

// ===== ALERT STYLES =====
const styleSheet = document.createElement('style');
styleSheet.textContent = `
  .chat-container {
    height: 600px;
    display: flex;
    flex-direction: column;
    background: var(--bg-light);
    border-radius: 12px;
    overflow: hidden;
  }

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .chat-message {
    display: flex;
    flex-direction: column;
    gap: 4px;
    animation: slideIn 0.3s ease;
  }

  @keyframes slideIn {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .chat-message.user {
    align-items: flex-end;
  }

  .chat-message.bot {
    align-items: flex-start;
  }

  .message-content {
    max-width: 70%;
    padding: 12px 16px;
    border-radius: 8px;
    font-size: 13px;
    word-break: break-word;
  }

  .chat-message.user .message-content {
    background: var(--color-primary);
    color: white;
  }

  .chat-message.bot .message-content {
    background: var(--bg-light-secondary);
    color: var(--text-dark);
  }

  .message-time {
    font-size: 11px;
    color: var(--text-muted);
    padding: 0 8px;
  }

  .chat-suggestions {
    padding: 16px 20px;
    border-top: 1px solid var(--border-light);
    background: var(--bg-light-secondary);
  }

  .chat-suggestions p {
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 8px;
    font-weight: 600;
  }

  .suggestion-btn {
    display: inline-block;
    padding: 6px 12px;
    background: var(--bg-light);
    border: 1px solid var(--border-light);
    border-radius: 6px;
    font-size: 12px;
    cursor: pointer;
    margin-right: 8px;
    margin-bottom: 8px;
    transition: all 0.2s;
  }

  .suggestion-btn:hover {
    border-color: var(--color-primary);
    background: var(--bg-light);
  }

  .chat-input {
    padding: 16px 20px;
    border-top: 1px solid var(--border-light);
    display: flex;
    gap: 8px;
  }

  .chat-input input {
    flex: 1;
    padding: 10px 12px;
    border: 1px solid var(--border-light);
    border-radius: 6px;
    font-size: 13px;
    outline: none;
  }

  .chat-input input:focus {
    border-color: var(--color-primary);
  }

  .checkbox-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    cursor: pointer;
    font-size: 13px;
  }

  .checkbox-item input[type="checkbox"],
  .checkbox-item input[type="radio"] {
    cursor: pointer;
    accent-color: var(--color-primary);
  }

  .faq-item {
    background: var(--bg-light);
    border: 1px solid var(--border-light);
    border-radius: 8px;
    margin-bottom: 12px;
    overflow: hidden;
  }

  .faq-question {
    padding: 16px;
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    align-items: center;
    user-select: none;
    transition: background 0.2s;
  }

  .faq-question:hover {
    background: var(--bg-light-secondary);
  }

  .faq-arrow {
    transition: transform 0.3s;
    font-size: 18px;
  }

  .faq-answer {
    display: none;
    padding: 0 16px 16px;
    background: var(--bg-light-secondary);
    border-top: 1px solid var(--border-light);
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
  }

  .contact-info-box {
    display: grid;
    gap: 16px;
    margin-bottom: 20px;
  }

  .contact-item {
    display: flex;
    gap: 16px;
    padding: 16px;
    background: var(--bg-light-secondary);
    border-radius: 8px;
  }

  .contact-item .icon {
    font-size: 24px;
    min-width: 32px;
  }

  .contact-item p {
    margin: 4px 0;
    font-size: 13px;
  }

  .text-muted {
    color: var(--text-muted);
    font-size: 12px;
  }

  .filter-bar {
    margin-bottom: 20px;
  }

  .filter-select {
    padding: 10px 12px;
    border: 1px solid var(--border-light);
    border-radius: 6px;
    font-size: 13px;
    background: var(--bg-light);
    color: var(--text-dark);
    outline: none;
    cursor: pointer;
  }

  .filter-select:focus {
    border-color: var(--color-primary);
  }

  .table-pagination {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    background: var(--bg-light-secondary);
    margin-top: 16px;
    border-radius: 8px;
  }

  .preference-select,
  .preference-options {
    margin-bottom: 20px;
  }

  .radio-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    cursor: pointer;
    font-size: 13px;
  }

  .radio-item input[type="radio"] {
    cursor: pointer;
    accent-color: var(--color-primary);
  }

  .clubs-container {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 16px;
  }

  .club-card {
    background: var(--bg-light);
    border: 1px solid var(--border-light);
    border-radius: 12px;
    padding: 20px;
    text-align: center;
    transition: all 0.3s;
  }

  .club-card:hover {
    box-shadow: var(--shadow-lg);
    transform: translateY(-2px);
  }

  .club-icon {
    font-size: 32px;
    margin-bottom: 8px;
  }

  .club-card h3 {
    font-size: 14px;
    margin: 0 0 8px 0;
  }

  .club-description {
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 12px;
  }

  .club-info {
    font-size: 11px;
    color: var(--text-muted);
    margin-bottom: 16px;
    padding-top: 12px;
    border-top: 1px solid var(--border-light);
  }

  .club-info span {
    display: block;
    margin: 4px 0;
  }

  .event-card,
  .route-card {
    background: var(--bg-light);
    border: 1px solid var(--border-light);
    border-radius: 12px;
    padding: 20px;
    margin-bottom: 16px;
    transition: all 0.2s;
  }

  .event-card.featured {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px rgba(216, 27, 96, 0.1);
  }

  .event-header,
  .route-card .route-number {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .event-card h3,
  .route-card h4 {
    margin: 0;
    font-size: 15px;
  }

  .route-number {
    background: var(--color-primary);
    color: white;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
  }

  .event-detail,
  .payment-detail {
    font-size: 12px;
    margin: 8px 0;
  }

  .detail-label {
    font-weight: 600;
    color: var(--text-secondary);
  }

  .routes-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 20px;
  }

  .event-actions,
  .assignment-actions,
  .invoice-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
  }

  .accommodation-card,
  .pass-status {
    background: var(--bg-light-secondary);
    padding: 16px;
    border-radius: 8px;
    margin-bottom: 20px;
  }

  .accommodation-detail {
    display: flex;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid var(--border-light);
    font-size: 13px;
  }

  .accommodation-detail:last-child {
    border-bottom: none;
  }

  .rules-list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .rules-list li {
    padding: 8px 0;
    font-size: 13px;
    color: var(--text-secondary);
  }

  .rules-list li:before {
    content: "✓ ";
    color: var(--color-success);
    font-weight: 600;
    margin-right: 8px;
  }

  .facilities-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 12px;
    margin: 16px 0;
  }

  .facility-item {
    background: var(--bg-light-secondary);
    padding: 12px;
    border-radius: 8px;
    text-align: center;
    font-size: 13px;
  }

  .attendance-courses {
    display: grid;
    gap: 16px;
  }

  .course-attendance {
    background: var(--bg-light);
    border: 1px solid var(--border-light);
    border-radius: 8px;
    padding: 16px;
  }

  .course-header-att {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .course-code {
    font-weight: 600;
    color: var(--color-primary);
    font-size: 12px;
  }

  .course-name {
    font-size: 13px;
    font-weight: 500;
  }

  .attendance-percent {
    font-size: 18px;
    font-weight: 700;
    color: var(--color-primary);
  }

  .attendance-breakdown-bar {
    display: flex;
    height: 6px;
    border-radius: 3px;
    overflow: hidden;
    margin-bottom: 8px;
  }

  .present-bar {
    background: var(--color-success);
    flex: 1;
  }

  .absent-bar {
    background: var(--color-danger);
    flex: 1;
  }

  .attendance-detail {
    font-size: 12px;
    color: var(--text-secondary);
  }

  .alerts-container {
    display: grid;
    gap: 12px;
  }

  .alert-box {
    padding: 16px;
    border-radius: 8px;
    display: flex;
    gap: 12px;
    border-left: 4px solid;
  }

  .alert-box.success {
    background: rgba(67, 160, 71, 0.1);
    border-left-color: var(--color-success);
  }

  .alert-box.warning {
    background: rgba(251, 140, 0, 0.1);
    border-left-color: var(--color-warning);
  }

  .alert-icon {
    font-size: 20px;
    min-width: 24px;
  }

  .alert-content {
    flex: 1;
  }

  .alert-title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 2px;
  }

  .alert-text {
    font-size: 12px;
    color: var(--text-secondary);
  }

  .payment-card {
    background: var(--bg-light);
    border-left: 4px solid var(--border-light);
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 12px;
  }

  .payment-card.alert {
    border-left-color: var(--color-danger);
    background: rgba(229, 57, 53, 0.02);
  }

  .payment-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--border-light);
  }

  .payment-header h3 {
    margin: 0;
    font-size: 14px;
  }

  .payment-amount {
    font-weight: 700;
    color: var(--color-primary);
  }

  .payment-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
  }

  .faq-container {
    max-width: 600px;
  }

  .helpdesk-hero {
    background: var(--bg-light-secondary);
    padding: 20px;
    border-radius: 8px;
    text-align: center;
    margin-bottom: 30px;
  }

  .helpdesk-hero h3 {
    margin-top: 0;
  }
`;

document.head.appendChild(styleSheet);

// ===== INITIALIZATION =====
document.addEventListener('DOMContentLoaded', function() {
  // Initialize all dropdowns
  document.querySelectorAll('.dropdown-menu').forEach(menu => {
    menu.addEventListener('click', function(e) {
      e.stopPropagation();
    });
  });
});
