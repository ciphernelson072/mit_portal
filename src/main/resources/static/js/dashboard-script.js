// API, apiGet(), requireRole(), logout(), escapeHtml() all come from auth.js,
// loaded before this file.

// ===== INITIALIZATION =====
document.addEventListener('DOMContentLoaded', function() {
  if (!initializeDashboard()) return;
  const isDashboardPage = Boolean(document.getElementById('personalQuickLinksList'));
  setupEventListeners(isDashboardPage);
  if (isDashboardPage) {
    loadUserData();
    loadDashboardQuickLinks();
    updateCurrentDate();
  } else {
    loadStudentAvatar();
  }
  setupTheme();
});

function initializeDashboard() {
  return requireRole(['STUDENT']);
}

async function loadStudentAvatar() {
  if (!document.querySelector('.profile-avatar')) return;
  try {
    const profile = await apiGet('/student/profile');
    if (!profile.profileImagePath) return;
    const imageUrl = `${API}/files/download?filePath=${encodeURIComponent(profile.profileImagePath)}`;
    document.querySelectorAll('.profile-avatar').forEach(image => { image.src = imageUrl; });
  } catch (err) {
    console.error('Failed to load student profile image', err);
  }
}

const dashboardQuickLinkDefinitions = [
  { label: 'My Courses', description: 'View your current courses', href: 'academics.html#courses', icon: 'C' },
  { label: 'Assignments', description: 'Review and submit assignments', href: 'academics.html#assignments', icon: 'A' },
  { label: 'Campus Events', description: 'Browse campus events', href: 'campus-support.html#events', icon: 'E' },
  { label: 'Messages', description: 'Open your inbox and conversations', href: 'messages.html', icon: 'M' },
];

let dashboardUnreadNotificationCount = 0;
let dashboardUnreadMessageCount = 0;

async function loadDashboardQuickLinks() {
  const list = document.getElementById('personalQuickLinksList');
  if (!list) return;

  try {
    const preferences = await apiGet('/portal/preferences');
    const rawSettings = preferences?.settingsJson || '{}';
    const settings = typeof rawSettings === 'string' ? JSON.parse(rawSettings) : rawSettings;
    if (!settings || typeof settings !== 'object' || Array.isArray(settings)) {
      throw new Error('Saved quick-link preferences have an invalid format.');
    }

    const savedLinks = Array.isArray(settings.quickLinks) ? settings.quickLinks : [];
    const selectedLinks = dashboardQuickLinkDefinitions.filter((link, index) => {
      const selected = savedLinks[index];
      return typeof selected === 'boolean' ? selected : [true, true, false, false][index];
    });

    list.innerHTML = selectedLinks.length
      ? selectedLinks.map(link => `
        <a href="${link.href}" class="module-card">
          <div class="module-icon" aria-hidden="true">${link.icon}</div>
          <h3>${escapeHtml(link.label)}</h3>
          <p>${escapeHtml(link.description)}</p>
        </a>`).join('')
      : '<p class="quick-links-empty">No quick links selected. <a href="profile.html#preferences">Choose shortcuts in Profile settings</a>.</p>';
  } catch (err) {
    console.error('Failed to load dashboard quick links', err);
    list.innerHTML = '<p class="quick-links-empty">Quick links could not be loaded. Please reload the dashboard or manage them in <a href="profile.html#preferences">Profile settings</a>.</p>';
  }
}

// ===== USER DATA =====
async function loadUserData() {
  const [profileResult, statusResult, courseUnitsResult, coursesResult] = await Promise.allSettled([
    apiGet('/student/profile'),
    apiGet('/student/my-status'),
    apiGet('/student/course-units'),
    apiGet('/student/courses'),
  ]);
  const profile = profileResult.status === 'fulfilled'
    && profileResult.value && typeof profileResult.value === 'object'
    ? profileResult.value
    : null;
  const status = statusResult.status === 'fulfilled'
    && statusResult.value && typeof statusResult.value === 'object' && !Array.isArray(statusResult.value)
    ? statusResult.value
    : null;
  const courseUnits = getFulfilledArray(courseUnitsResult);
  const courses = getFulfilledArray(coursesResult);

  if (profile) {
    document.querySelectorAll('.student-name').forEach(el => {
      el.textContent = (profile.fullName || '').split(' ')[0] || profile.username || 'Student';
    });
    const profileNameEl = document.querySelector('.profile-name');
    if (profileNameEl) profileNameEl.textContent = profile.fullName || profile.username || 'Student';
    const profileEmailEl = document.querySelector('.profile-email');
    if (profileEmailEl) profileEmailEl.textContent = profile.email || '—';
    const programValues = document.querySelectorAll('.program-info p');
    if (programValues[0]) programValues[0].innerHTML = `<strong>Reg No:</strong> ${escapeHtml(profile.registrationNumber || '—')}`;
    if (programValues[2]) programValues[2].innerHTML = `<strong>Enrollment:</strong> ${escapeHtml(profile.enrollmentDate || '—')}`;
    if (profile.profileImagePath) {
      const imageUrl = `${API}/files/download?filePath=${encodeURIComponent(profile.profileImagePath)}`;
      document.querySelectorAll('.profile-image, .profile-avatar').forEach(image => {
        image.src = imageUrl;
      });
    }
  } else {
    console.error('Failed to load student profile', profileResult.reason || new Error('The profile response has an invalid format.'));
    document.querySelectorAll('.student-name').forEach(el => { el.textContent = 'Unavailable'; });
    const profileNameEl = document.querySelector('.profile-name');
    if (profileNameEl) profileNameEl.textContent = 'Unavailable';
    const profileEmailEl = document.querySelector('.profile-email');
    if (profileEmailEl) profileEmailEl.textContent = '—';
    const programValues = document.querySelectorAll('.program-info p');
    programValues.forEach((value, index) => {
      const labels = ['Reg No:', 'Program:', 'Enrollment:', 'Status:'];
      if (labels[index]) value.innerHTML = `<strong>${labels[index]}</strong> —`;
    });
  }
  const programValues = document.querySelectorAll('.program-info p');
  if (programValues[1]) {
    programValues[1].innerHTML = `<strong>Program:</strong> ${escapeHtml(status?.studentProgramName || 'Not available')}`;
  }
  const lifecycleStatus = status?.studentLifecycleStatus;
  if (programValues[3]) {
    const displayStatus = lifecycleStatus
      ? lifecycleStatus.toLowerCase().replace(/(^|_)\w/g, character => character.replace('_', '').toUpperCase())
      : 'Not available';
    programValues[3].innerHTML = `<strong>Status:</strong> <span>${escapeHtml(displayStatus)}</span>`;
  }

  if (status) {
    renderStatCards(profile, status, courses);
    if (Array.isArray(status.grades)) {
      renderGrades(status.grades);
    } else {
      console.error('Student grade data has an invalid format.');
      showDashboardCardMessage('Recent Grades', 'Grades could not be loaded.');
    }
    if (Number.isSafeInteger(status.attendanceCount) && status.attendanceCount >= 0
        && Number.isSafeInteger(status.presentAttendanceCount)
        && status.presentAttendanceCount >= 0
        && status.presentAttendanceCount <= status.attendanceCount) {
      renderAttendance(status.attendanceCount, status.presentAttendanceCount);
    } else {
      console.error('Student attendance data has an invalid format.');
      showDashboardCardMessage('Attendance Overview', 'Attendance could not be loaded.');
    }
    renderFeeItems(status);
  } else {
    console.error('Failed to load student status', statusResult.reason || new Error('The status response has an invalid format.'));
    renderStatCards(profile, null, courses);
    showDashboardCardMessage('Recent Grades', 'Grades could not be loaded.');
    showDashboardCardMessage('Attendance Overview', 'Attendance could not be loaded.');
    showDashboardCardMessage('Pending Payments', 'Finance status could not be loaded.');
  }

  if (courses || courseUnits) {
    renderCourses(courses, courseUnits || []);
  } else {
    console.error('Failed to load student course units', courseUnitsResult.reason || new Error('The course units response has an invalid format.'));
    showDashboardCardMessage('Current Courses', 'Courses could not be loaded.');
  }

  if (!courseUnits) {
    console.warn('Course unit details are unavailable', courseUnitsResult.reason || new Error('The course units response has an invalid format.'));
  }
  if (!courses) {
    console.error('Failed to load student courses', coursesResult.reason || new Error('The courses response has an invalid format.'));
  }

  loadUpcomingDeadlines(courses || []);
  renderAnnouncementsCard();
  loadMessages();
  loadNotifications();
}

function getFulfilledArray(result) {
  return result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : null;
}

async function loadNotifications() {
  try {
    const [notifications, preferences] = await Promise.all([
      apiGet('/notifications'),
      apiGet('/portal/preferences').catch(err => {
        console.error('Failed to load notification display preferences', err);
        return null;
      }),
    ]);
    const list = document.querySelector('.notification-list');
    if (!list) return;
    if (!Array.isArray(notifications)) {
      throw new Error('The notifications response has an invalid format.');
    }

    let categorySettings = null;
    try {
      const rawSettings = preferences?.settingsJson || '{}';
      const settings = typeof rawSettings === 'string' ? JSON.parse(rawSettings) : rawSettings;
      if (settings && typeof settings === 'object' && !Array.isArray(settings)) {
        categorySettings = settings.notifications;
      }
    } catch (err) {
      console.error('Saved notification preferences are invalid JSON', err);
    }

    const enabledCategories = new Set(
      ['ASSIGNMENT', 'GRADE', 'SYSTEM', 'PAYMENT', 'ATTENDANCE', 'ANNOUNCEMENT'].filter((category, index) => {
        const selected = Array.isArray(categorySettings) ? categorySettings[index] : undefined;
        return typeof selected === 'boolean' ? selected : [true, true, false, true, true, true][index];
      })
    );
    const visibleNotifications = notifications.filter(item =>
      enabledCategories.has(item.category || 'SYSTEM')
    );
    dashboardUnreadNotificationCount = visibleNotifications.filter(item => !item.read).length;
    list.innerHTML = visibleNotifications.length ? visibleNotifications.slice(0, 8).map(item => `
      <button type="button" class="notification-item ${item.read ? '' : 'unread'}" data-notification-id="${item.id}">
        <span class="notification-content"><strong class="notification-title">${escapeHtml(item.title)}</strong>
        <span class="notification-text">${escapeHtml(item.message)}</span>
        <span class="notification-time">${escapeHtml(item.createdAt || '')}</span></span>
      </button>`).join('') : `<p class="notification-empty">${
        notifications.length
          ? 'No notifications match your dashboard preferences.'
          : 'No notifications yet.'
      }</p>`;
    list.querySelectorAll('[data-notification-id]').forEach(item => item.addEventListener('click', async () => {
      const id = Number(item.dataset.notificationId);
      if (!id) return;
      try {
        await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
        if (item.classList.contains('unread')) {
          item.classList.remove('unread');
          dashboardUnreadNotificationCount = Math.max(0, dashboardUnreadNotificationCount - 1);
        }
        updateNotificationBadge();
      } catch (err) {
        console.error('Failed to mark notification read', err);
      }
    }));
    updateNotificationBadge();
  } catch (err) {
    console.error('Failed to load notifications', err);
    const list = document.querySelector('.notification-list');
    if (list) list.innerHTML = '<p class="notification-empty">Notifications could not be loaded.</p>';
    dashboardUnreadNotificationCount = 0;
    updateNotificationBadge();
  }
}

async function loadMessages() {
  try {
    const messages = await apiGet('/messages/received');
    const list = document.querySelector('.message-list');
    const badge = document.getElementById('messageBadge');
    if (!list) return;
    if (!Array.isArray(messages)) {
      throw new Error('The messages response has an invalid format.');
    }

    const unread = messages.filter(message => !message.read);
    dashboardUnreadMessageCount = unread.length;
    if (badge) {
      badge.textContent = unread.length;
      badge.hidden = unread.length === 0;
    }
    if (!messages.length) {
      list.innerHTML = '<p class="message-empty">No messages yet.</p>';
      return;
    }
    list.innerHTML = messages.slice(0, 5).map(message => `
      <button type="button" class="message-item ${message.read ? '' : 'unread'}" data-message-id="${message.id}">
        <span class="message-avatar">👤</span>
        <span class="message-content">
          <strong class="message-from">${escapeHtml(message.senderUsername)}</strong>
          <span class="message-preview">${escapeHtml(message.subject)}</span>
        </span>
      </button>`).join('');
    list.querySelectorAll('.message-item').forEach(item => {
      item.addEventListener('click', async () => {
        const messageId = item.dataset.messageId;
        try {
          await apiFetch(`/messages/${messageId}/read`, { method: 'POST' });
          if (item.classList.contains('unread')) {
            item.classList.remove('unread');
            dashboardUnreadMessageCount = Math.max(0, dashboardUnreadMessageCount - 1);
          }
          if (badge) {
            badge.textContent = dashboardUnreadMessageCount;
            badge.hidden = dashboardUnreadMessageCount === 0;
          }
        } catch (err) {
          console.error('Failed to mark message read', err);
        }
      });
    });
  } catch (err) {
    console.error('Failed to load messages', err);
    const list = document.querySelector('.message-list');
    if (list) list.innerHTML = '<p class="message-empty">Messages could not be loaded.</p>';
    const badge = document.getElementById('messageBadge');
    dashboardUnreadMessageCount = 0;
    if (badge) badge.hidden = true;
  }
}

async function composeMessage() {
  const receiverUsername = prompt('Recipient username:');
  if (!receiverUsername) return;
  const subject = prompt('Subject:');
  if (!subject) return;
  const content = prompt('Message:');
  if (!content) return;
  try {
    await apiFetch('/messages', {
      method: 'POST',
      body: JSON.stringify({ receiverUsername, subject, content }),
    });
    alert('Message sent successfully.');
  } catch (err) {
    alert(err.message || 'Message could not be sent.');
  }
}

function renderStatCards(profile, status, courses) {
  const stats = document.querySelectorAll('.stat-value');
  if (stats[0]) stats[0].textContent = profile?.cgpa != null
    ? Number(profile.cgpa).toFixed(2)
    : status?.resultPublicationStatus === 'PUBLISHED' ? '—' : 'NOT FINALIZED';
  const total = Number.isSafeInteger(status?.attendanceCount) ? status.attendanceCount : null;
  const presentCount = Number.isSafeInteger(status?.presentAttendanceCount) ? status.presentAttendanceCount : null;
  const validAttendance = total != null && presentCount != null
    && total >= 0 && presentCount >= 0 && presentCount <= total;
  const pct = validAttendance && total > 0 ? Math.round((presentCount / total) * 100) : null;
  if (stats[1]) stats[1].textContent = pct != null ? `${pct}%` : '—';
  if (!Array.isArray(courses) || !courses.length) {
    if (stats[2]) stats[2].textContent = '—';
    if (stats[3]) stats[3].textContent = '—';
    return;
  }

  if (stats[2]) {
    stats[2].textContent = status?.resultPublicationStatus === 'PUBLISHED'
      ? status.creditsEarned != null ? String(status.creditsEarned) : '—'
      : 'NOT FINALIZED';
  }
  const semesters = [...new Set(courses.map(course => course.semester).filter(Boolean))];
  if (stats[3]) stats[3].textContent = semesters.length === 1 ? semesters[0] : semesters.length > 1 ? 'Multiple' : '—';
}

function renderAttendance(total, presentCount) {
  const card = getDashboardCard('Attendance Overview');
  const content = card?.querySelector('.card-content');
  if (!content) return;
  if (!Number.isSafeInteger(total) || total < 0
      || !Number.isSafeInteger(presentCount) || presentCount < 0 || presentCount > total) {
    content.textContent = 'Attendance could not be loaded.';
    return;
  }
  if (!total) {
    content.innerHTML = '<p>No attendance records are available yet.</p>';
    return;
  }

  const absentCount = total - presentCount;
  const pct = Math.round((presentCount / total) * 100);
  content.innerHTML = `
    <p class="attendance-percent">${pct}%</p>
    <p class="attendance-label">Attendance rate across ${total} recorded classes</p>
    <div class="attendance-legend">
      <span class="legend-item"><span class="legend-color present" aria-hidden="true"></span><span>Present: ${presentCount}</span></span>
      <span class="legend-item"><span class="legend-color absent" aria-hidden="true"></span><span>Absent: ${absentCount}</span></span>
    </div>`;
}

function renderCourses(courses, courseUnits) {
  const card = getDashboardCard('Current Courses');
  const content = card?.querySelector('.card-content');
  if (!content) return;
  const enrolledCourses = Array.isArray(courses) && courses.length
    ? courses
    : Array.from(new Map((courseUnits || []).map(unit => [
      Number(unit.courseId),
      { id: unit.courseId, courseCode: unit.courseCode, name: unit.courseName },
    ])).values());
  const unitsByCourse = new Map();
  (courseUnits || []).forEach(unit => {
    const courseId = Number(unit.courseId);
    if (!unitsByCourse.has(courseId)) unitsByCourse.set(courseId, []);
    unitsByCourse.get(courseId).push(unit);
  });
  content.innerHTML = enrolledCourses.length ? enrolledCourses.map(course => {
    const units = unitsByCourse.get(Number(course.id)) || [];
    const details = units.length
      ? units.map(unit => `${escapeHtml(unit.unitCode)} · ${escapeHtml(unit.name)}`).join('<br>')
      : [course.teacherName, course.semester].filter(Boolean).map(escapeHtml).join(' · ');
    const title = `${course.courseCode || ''}${course.courseCode ? ' · ' : ''}${course.name || 'Course'}`;
    return `<div class="course-item"><div class="course-info"><p class="course-name">${escapeHtml(title)}</p><p>${details}</p></div></div>`;
  }).join('') : '<p>No enrolled courses.</p>';
}

function renderGrades(grades) {
  const card = getDashboardCard('Recent Grades');
  const content = card?.querySelector('.card-content');
  if (content) content.innerHTML = grades.length ? grades.slice(0, 5).map(g => `<div class="deadline-item"><div class="deadline-info"><p class="deadline-title">${escapeHtml(g.courseName || 'Course')}: ${escapeHtml(g.gradeValue || '—')}</p><p class="deadline-course">${escapeHtml(g.remarks || '')}</p></div></div>`).join('') : '<p>No grades posted yet.</p>';
}

async function loadUpcomingDeadlines(courses) {
  try {
    const [assignments, submissions] = await Promise.all([
      apiGet('/portal/student/assignments'),
      apiGet('/portal/student/submissions'),
    ]);
    if (!Array.isArray(assignments) || !Array.isArray(submissions)) {
      throw new Error('The assignment deadline response has an invalid format.');
    }
    const submittedIds = new Set(submissions.map(item => Number(item.assignmentId)));
    const courseNames = new Map((courses || []).map(course => [
      Number(course.id),
      `${course.courseCode || ''}${course.courseCode ? ' · ' : ''}${course.name || 'Course'}`,
    ]));
    const now = Date.now();
    const upcoming = assignments
      .map(assignment => ({ ...assignment, dueDate: new Date(assignment.dueAt) }))
      .filter(assignment =>
        Number.isFinite(assignment.dueDate.getTime())
        && assignment.dueDate.getTime() >= now
        && !submittedIds.has(Number(assignment.id))
      )
      .sort((left, right) => left.dueDate - right.dueDate)
      .slice(0, 5);
    const card = getDashboardCard('Upcoming Deadlines');
    const content = card?.querySelector('.card-content');
    if (!content) return;
    content.innerHTML = upcoming.length ? upcoming.map(assignment => {
      const courseName = courseNames.get(Number(assignment.courseId)) || `Course #${assignment.courseId}`;
      const dateLabel = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(assignment.dueDate);
      return `<div class="deadline-item"><div class="deadline-date"><span class="deadline-day">${escapeHtml(dateLabel)}</span></div><div class="deadline-info"><p class="deadline-title">${escapeHtml(assignment.title || 'Assignment')}</p><p class="deadline-course">${escapeHtml(courseName)} · Due ${escapeHtml(dateLabel)}</p></div></div>`;
    }).join('') : '<p>No upcoming assignment deadlines.</p>';
  } catch (err) {
    console.error('Failed to load upcoming assignment deadlines', err);
    showDashboardCardMessage('Upcoming Deadlines', 'Assignment deadlines could not be loaded.');
  }
}

function renderFeeItems(status) {
  const card = getDashboardCard('Pending Payments');
  const content = card?.querySelector('.card-content');
  if (!content) return;
  const balance = Number(status?.balance);
  if (status?.balance == null || !Number.isFinite(balance)) {
    content.textContent = 'Finance status is unavailable.';
    return;
  }
  const dueDate = status.nextDueDate && status.nextDueDate !== 'None'
    ? ` due ${escapeHtml(status.nextDueDate)}`
    : '';
  content.innerHTML = `<div class="payment-item ${balance > 0 ? 'alert' : ''}"><p class="payment-title">${balance > 0 ? 'Outstanding balance' : 'No outstanding balance'}</p><p class="payment-amount">${balance > 0 ? `UGX ${balance.toLocaleString()}${dueDate}` : ''}</p><a class="payment-btn" href="finance.html">View Finance</a></div>`;
}

async function renderAnnouncementsCard() {
  try {
    const announcements = await apiGet('/announcements');
    if (!Array.isArray(announcements)) throw new Error('The announcements response has an invalid format.');
    const card = getDashboardCard('Announcements');
    const content = card?.querySelector('.card-content');
    const recentAnnouncements = [...announcements]
      .sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0))
      .slice(0, 5);
    if (content) content.innerHTML = recentAnnouncements.length ? recentAnnouncements.map(a => `<div class="announcement-item"><p class="announcement-title">${escapeHtml(a.title)}</p><p class="announcement-text">${escapeHtml(a.content)}</p><p class="announcement-date">${escapeHtml((a.createdAt || '').slice(0, 10))}</p></div>`).join('') : '<p>No announcements.</p>';
  } catch (err) {
    console.error('Failed to load dashboard announcements', err);
    showDashboardCardMessage('Announcements', 'Announcements could not be loaded.');
  }
}

function getDashboardCard(title) {
  return Array.from(document.querySelectorAll('.card'))
    .find(item => item.querySelector('.card-header h3')?.textContent.includes(title));
}

function showDashboardCardMessage(title, message) {
  const content = getDashboardCard(title)?.querySelector('.card-content');
  if (!content) return;
  content.innerHTML = `<p class="card-inline-message">${escapeHtml(message)}</p>`;
}

// ===== EVENT LISTENERS =====
function setupEventListeners(isDashboardPage) {
  // This file is loaded on every page (dashboard + all 5 module pages) for the
  // shared header behavior, but only dashboard.html has the full header markup
  // (notification/message bells, global search) - the module pages share the
  // profile dropdown and theme switcher but not those. Every element lookup here
  // is null-checked so a missing header piece on a given page just means "no
  // listener for that piece" instead of a thrown exception that aborts every
  // other setup step in this function (setupTheme/updateCurrentDate included).
  if (isDashboardPage) {
    document.getElementById('notificationBtn')?.addEventListener('click', function() {
      toggleDropdown('notification');
    });

    document.getElementById('messageBtn')?.addEventListener('click', function() {
      toggleDropdown('message');
    });

    document.getElementById('profileBtn')?.addEventListener('click', function() {
      toggleDropdown('profile');
    });
  }

  document.getElementById('themeBtn')?.addEventListener('click', function() {
    toggleDropdown('theme');
  });


  // Theme options
  document.querySelectorAll('.theme-option').forEach(option => {
    option.addEventListener('click', function() {
      const theme = this.getAttribute('data-theme');
      setTheme(theme);
    });
  });

  // Close dropdowns when clicking outside
  document.addEventListener('click', function(e) {
    if (!e.target.closest('.dropdown')) {
      document.querySelectorAll('.dropdown.active').forEach(dropdown => {
        closeDropdown(dropdown);
      });
    }
  });

  document.addEventListener('keydown', function(e) {
    if (e.key !== 'Escape') return;
    const openDropdown = document.querySelector('.dropdown.active');
    if (!openDropdown) return;

    const focusedDropdown = document.activeElement?.closest('.dropdown.active') || openDropdown;
    document.querySelectorAll('.dropdown.active').forEach(closeDropdown);
    focusedDropdown.querySelector('button')?.focus();
  });

  // Global search
  document.getElementById('globalSearch')?.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
      performSearch(this.value);
    }
  });
  document.querySelector('.search-btn')?.addEventListener('click', function() {
    const search = document.getElementById('globalSearch');
    if (search) performSearch(search.value);
  });

}

// ===== DROPDOWNS =====
function toggleDropdown(type) {
  const button = document.getElementById(`${type}Btn`);
  const dropdown = button?.closest('.dropdown');
  if (!button || !dropdown) return;

  const shouldOpen = !dropdown.classList.contains('active');
  document.querySelectorAll('.dropdown.active').forEach(closeDropdown);
  if (shouldOpen) {
    dropdown.classList.add('active');
    button.setAttribute('aria-expanded', 'true');
  }
}

function closeDropdown(dropdown) {
  dropdown.classList.remove('active');
  dropdown.querySelector('button')?.setAttribute('aria-expanded', 'false');
}

// ===== THEME SWITCHER =====
function setupTheme() {
  const savedTheme = localStorage.getItem('theme') || 'system';
  setTheme(savedTheme);
}

function setTheme(theme) {
  const themeBtn = document.getElementById('themeBtn');
  const themeDropdown = themeBtn?.closest('.dropdown');
  if (themeDropdown) {
    closeDropdown(themeDropdown);
  } else {
    themeBtn?.setAttribute('aria-expanded', 'false');
  }

  if (typeof window.setSharedTheme === 'function') {
    window.setSharedTheme(theme);
    return;
  }

  const body = document.body;
  
  if (theme === 'dark') {
    body.classList.add('dark-mode');
    if (themeBtn) themeBtn.textContent = '☀️';
  } else if (theme === 'light') {
    body.classList.remove('dark-mode');
    if (themeBtn) themeBtn.textContent = '🌙';
  } else if (theme === 'system') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    if (prefersDark) {
      body.classList.add('dark-mode');
      if (themeBtn) themeBtn.textContent = '☀️';
    } else {
      body.classList.remove('dark-mode');
      if (themeBtn) themeBtn.textContent = '🌙';
    }
  }
  
  localStorage.setItem('theme', theme);
  
}

// ===== NOTIFICATIONS =====
function updateNotificationBadge() {
  const badge = document.getElementById('notificationBadge');
  if (!badge) return;
  badge.textContent = dashboardUnreadNotificationCount;
  badge.hidden = dashboardUnreadNotificationCount === 0;
}

// ===== SEARCH =====
function performSearch(query) {
  if (!query.trim()) {
    alert('Please enter a search term');
    return;
  }
  
  const normalized = query.trim().toLowerCase();
  const matches = Array.from(document.querySelectorAll('.card, .module-card, .announcement-item, .course-item'))
    .filter(item => item.textContent.toLowerCase().includes(normalized));
  if (matches.length) {
    const match = matches[0];
    match.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (!match.matches('a, button, input, select, textarea, [tabindex]')) {
      match.tabIndex = -1;
    }
    match.focus();
  } else {
    alert(`No dashboard results found for "${query.trim()}".`);
  }
}

// ===== MODULE NAVIGATION =====
function navigateToModule(module) {
  const moduleLinks = {
    'academics': 'academics.html',
    'attendance': 'attendance.html',
    'finance': 'finance.html',
    'profile': 'profile.html',
    'campus': 'campus-support.html',
    'support': 'campus-support.html'
  };
  const target = moduleLinks[module];
  if (target) window.location.href = target;
}

// ===== LOGOUT =====
// logout() itself comes from auth.js (calls POST /api/auth/logout to revoke the
// refresh token server-side, then clears the local role hint and redirects).
function handleLogoutClick() {
  if (confirm('Are you sure you want to logout?')) {
    logout('index.html');
  }
}

// ===== DATE & TIME =====
function updateCurrentDate() {
  const now = new Date();
  const day = String(now.getDate()).padStart(2, '0');
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const year = now.getFullYear();
  const today = `${day}/${month}/${year}`;

  const dateElement = document.getElementById('currentDate');
  if (dateElement) {
    dateElement.textContent = today;
  }
}

// ===== PROFILE ACTIONS =====
document.addEventListener('DOMContentLoaded', function() {
  const editProfileBtn = document.querySelector('.edit-profile-btn');
  if (editProfileBtn) {
    editProfileBtn.addEventListener('click', function() {
      let fileInput = document.getElementById('profilePhotoInput');
      if (!fileInput) {
        fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.id = 'profilePhotoInput';
        fileInput.accept = 'image/jpeg,image/png,image/gif';
        fileInput.hidden = true;
        document.body.appendChild(fileInput);
        fileInput.addEventListener('change', uploadProfilePhoto);
      }
      fileInput.click();
    });
  }
});

async function uploadProfilePhoto(event) {
  let file = event.target.files[0];
  if (!file) return;

  try {
    file = validateUploadFile(file, ['jpg', 'jpeg', 'png', 'gif']);
    const profile = await apiGet('/student/profile');
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiFetch(`/files/student/${profile.id}/profile-image`, {
      method: 'POST',
      body: formData,
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Profile photo upload failed. Please try again shortly.'));
    }

    const updatedProfile = await apiGet('/student/profile');
    if (updatedProfile.profileImagePath) {
      const imageUrl = `${API}/files/download?filePath=${encodeURIComponent(updatedProfile.profileImagePath)}`;
      document.querySelectorAll('.profile-image, .profile-avatar').forEach(image => {
        image.src = imageUrl;
      });
    }
    alert('Profile photo updated successfully.');
  } catch (err) {
    alert(err.message || 'Profile photo upload failed.');
  } finally {
    event.target.value = '';
  }
}

// ===== KEYBOARD SHORTCUTS =====
document.addEventListener('keydown', function(e) {
  // Ctrl/Cmd + K for search
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    const search = document.getElementById('globalSearch');
    if (search) {
      e.preventDefault();
      search.focus();
    }
  }
  
  // Ctrl/Cmd + / for help
  if ((e.ctrlKey || e.metaKey) && e.key === '/') {
    e.preventDefault();
    showKeyboardShortcutsHelp();
  }
});

function showKeyboardShortcutsHelp() {
  alert(`Keyboard Shortcuts:
  
  Ctrl/Cmd + K  - Focus on search
  Ctrl/Cmd + /  - Show this help`);
}

// ===== ACCESSIBILITY =====
// Add ARIA labels and roles for screen readers
window.addEventListener('load', function() {
  // Add role and aria-label to buttons
  document.querySelectorAll('.icon-btn').forEach(btn => {
    if (!btn.getAttribute('aria-label')) {
      const icon = btn.textContent.trim();
      let label = 'Button';
      
      if (icon.includes('🔔')) label = 'Notifications';
      if (icon.includes('💬')) label = 'Messages';
      if (icon.includes('🌙')) label = 'Theme';
      
      btn.setAttribute('aria-label', label);
    }
  });
});

// ===== PRINT FUNCTIONALITY =====
function printDashboard() {
  window.print();
}
