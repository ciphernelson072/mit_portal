/* =========================================================================
   Registrar / MIT Student Portal - shared auth module.
   Loaded before script.js / dashboard-script.js / modules-script.js on every
   page. Centralizes what used to be duplicated three times: the fetch wrapper,
   the session guard, refresh-on-401 retry, and logout.

   The JWT access token itself lives only in an HttpOnly cookie (set by the
   backend on /api/auth/login) - this file never reads or writes it, on
   purpose. Nothing here can hand a token to an XSS payload, because nothing
   here ever has the token in the first place.

   ROLE_KEY / USERNAME_KEY in localStorage are non-sensitive UI hints only
   (which dashboard to render, whose name to show) - they grant no access on
   their own. The real access-control boundary is the server checking the
   HttpOnly cookie on every request.
   ========================================================================= */

const API = (window.MIT_PORTAL_CONFIG?.apiBaseUrl || '/api').replace(/\/$/, '');
const API_URL = new URL(API, window.location.href);
const API_PATH_PREFIX = `${API_URL.pathname.replace(/\/$/, '')}/`;
const ROLE_KEY = 'mitRole';
const USERNAME_KEY = 'mitUsername';
const PASSWORD_CHANGE_KEY = 'mitPasswordChangeRequired';
const MFA_ENROLLMENT_KEY = 'mitMfaEnrollmentRequired';
const CSP_ACTION_ALLOWLIST = new Set(["__clickElementById","acknowledgeGradeReview","activateAcademicYear","adminLogout","applyForScholarship","approveClassAttendance","approveDraftFee","approveGrade","approveStudentReport","askQuestion","authorizeGradeCorrection","cancelAdminInput","cancelEventRegistration","changeLanguage","changePassword","closeModal","completeAdminRefund","composeMessage","configureCourseAssessments","confirmAdminPaymentAttempt","confirmDeactivateStaff","confirmDeactivateStudent","confirmGradeAppealConsultation","confirmRemoveStudent","confirmRemoveTeacher","confirmRemoveUser","createAdminFee","createGradeRetake","deactivateUser","decideAdmission","decideGradeAppeal","deleteCourseCategory","deleteStudentDocument","dismissAlert","downloadAdmissionDocument","downloadGradeImportTemplate","downloadHallTicket","downloadInvoice","downloadReceipt","downloadReport","downloadStudentDocument","editContact","editCourse","editProfile","editStudent","editTeacher","editUser","enrollAcceptedApplication","exportAdminData","exportGrades","exportTeacherGrades","exportTeacherRoster","filterByCourse","generateFinanceReport","generateReport","generateStudentReport","goToDashboard","handleAdminLogin","handleChatSubmit","handleLogin","handleLogoutClick","importGrades","initiatePayment","joinClub","loadAdminFlutterwaveReconciliations","loadAdminPaymentAttempts","loadAdminRefunds","loadAdmissionApplications","loadAttendanceRoster","loadCourseUnitRoster","loadDeletedUsers","loadHrManagement","loadPendingActions","loadRequests","loadRoster","loadTeacherPublicationStatuses","loadUsers","markAdminNotificationRead","markAllAdminNotificationsRead","markTeacherNotificationRead","openCourseCategoryForm","openCourseForm","openCourseUnitForm","openEditCourseCategoryForm","openEditStaffModal","openEditStudentModal","openGradeAppeal","openStaffForm","openStudentForm","openStudentGraduationAudit","openStudentLifecycleModal","openStudentTransferModal","openUserForm","publishAnnouncement","publishStudentReport","recalculateStudentCgpa","reconcileLateFlutterwavePayment","registerEvent","rejectAdminPaymentAttempt","rejectAdminRefund","rejectGrade","renewPass","requestAdminPasswordReset","requestExamPermit","requestGradeAppealConsultation","requestHostel","requestPasswordReset","requestService","restoreStudent","restoreTeacher","restoreUser","retryEmailDelivery","reviewAdminAttendanceDispute","reviewAttendanceDispute","reviewGradeAssessments","reviewHrLeave","savePreferences","savePrivacySettings","saveProgramAwardType","selectPaymentMethod","sendChatMessage","setAcademicTermRegistration","setAdmissionDocumentRequirementActive","submitAdminInput","submitAssignment","submitAttendanceDispute","submitStudentReportApproval","submitTeacherGradeAppealResponse","submitTicket","switchModule","switchTab","teacherLogout","testEmail","toggleClubMembership","toggleCourseCategory","toggleFAQ","togglePasswordVisibility","trackRequest","updateRequestAssignee","updateRequestStatus","uploadDocument","uploadTeacherPhoto","viewAdmissionDocuments","viewAssignment","viewCourseDetails","viewCourseGrades","viewEventDetails","viewInvoice","viewRequest","viewRoute","viewStudentCGPA","viewStudentCgpa","viewSubmission","viewTicket"]);

function dispatchCspAction(event) {
  let element = event.target instanceof Element ? event.target : event.target?.parentElement;
  while (element && element !== document.documentElement) {
    if (element.dataset.cspAction && element.dataset.cspEvent === event.type) {
      const actionName = element.dataset.cspAction;
      if (!CSP_ACTION_ALLOWLIST.has(actionName)) {
        console.error(`Blocked unregistered portal action: ${actionName}`);
        return;
      }
      const args = JSON.parse(decodeURIComponent(element.dataset.cspArgs || '%5B%5D')).map(argument => {
        const argumentType = argument?.__mitCspArg || argument?.$mitCspArg;
        if (argumentType === 'element') return element;
        if (argumentType === 'event') return event;
        if (argumentType === 'value') return element.value;
        if (argumentType === 'checked') return element.checked;
        return argument;
      });
      const action = actionName === '__clickElementById'
        ? elementId => {
          const target = document.getElementById(elementId);
          if (!target) throw new Error(`Missing action target: ${elementId}`);
          target.click();
        }
        : window[actionName];
      if (typeof action !== 'function') {
        console.error(`Registered portal action is unavailable: ${actionName}`);
        return;
      }
      const result = action.apply(element, args);
      if (result === false || element.dataset.cspPreventDefault === 'true') {
        event.preventDefault();
      }
    }
    element = element.parentElement;
  }
}

for (const eventType of ['click', 'submit', 'change', 'input', 'keypress', 'keyup', 'focus', 'blur']) {
  document.addEventListener(eventType, dispatchCspAction, eventType === 'focus' || eventType === 'blur');
}

if (!document.querySelector('link[data-mit-csp-inline-styles]')) {
  const stylesheet = document.createElement('link');
  stylesheet.rel = 'stylesheet';
  stylesheet.href = '/css/csp-inline-styles.css';
  stylesheet.dataset.mitCspInlineStyles = '';
  document.head.appendChild(stylesheet);
}

function installRequestFeedback() {
  if (window.__mitRequestFeedbackInstalled) return;
  window.__mitRequestFeedbackInstalled = true;

  let activeRequests = 0;
  let showProcessingTimer;
  let hideToastTimer;
  let processingIndicator;
  let successToast;

  function ensureFeedbackElements() {
    if (!processingIndicator) {
      if (!document.querySelector('link[data-mit-request-feedback-styles]')) {
        const stylesheet = document.createElement('link');
        stylesheet.rel = 'stylesheet';
        stylesheet.href = '/css/request-feedback.css';
        stylesheet.dataset.mitRequestFeedbackStyles = '';
        document.head.appendChild(stylesheet);
      }
      processingIndicator = document.createElement('div');
      processingIndicator.id = 'mitRequestProcessing';
      processingIndicator.setAttribute('role', 'status');
      processingIndicator.setAttribute('aria-live', 'polite');
      processingIndicator.setAttribute('aria-atomic', 'true');
      processingIndicator.hidden = true;
      processingIndicator.innerHTML = '<span class="mit-request-spinner" aria-hidden="true"></span><span>Processing your request…</span>';
      successToast = document.createElement('div');
      successToast.id = 'mitRequestSuccess';
      successToast.setAttribute('role', 'status');
      successToast.setAttribute('aria-live', 'polite');
      successToast.setAttribute('aria-atomic', 'true');
      successToast.hidden = true;
      document.body.append(processingIndicator, successToast);
    }
  }

  function notifySuccess() {
    ensureFeedbackElements();
    successToast.textContent = 'Your request was completed successfully.';
    successToast.hidden = false;
    window.clearTimeout(hideToastTimer);
    hideToastTimer = window.setTimeout(() => {
      successToast.hidden = true;
    }, 4000);
  }

  const originalFetch = window.fetch.bind(window);
  let csrfTokenPromise;

  function getCsrfToken() {
    if (!csrfTokenPromise) {
      csrfTokenPromise = originalFetch(`${API}/auth/csrf`, {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      }).then(async response => {
        if (!response.ok) throw new Error(`Could not load CSRF token (${response.status})`);
        const payload = await response.json();
        if (typeof payload?.token !== 'string' || !payload.token) {
          throw new Error('CSRF token response was invalid');
        }
        return payload.token;
      }).catch(error => {
        csrfTokenPromise = null;
        throw error;
      });
    }
    return csrfTokenPromise;
  }

  window.fetch = async function (...args) {
    const input = args[0];
    const requestUrl = input instanceof Request ? input.url : String(input);
    let url;
    try {
      url = new URL(requestUrl, window.location.href);
    } catch (error) {
      return originalFetch(...args);
    }
    if (url.origin !== API_URL.origin || !url.pathname.startsWith(API_PATH_PREFIX)) {
      return originalFetch(...args);
    }

    const init = args[1] || {};
    const method = String(init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    activeRequests += 1;
    if (activeRequests === 1) {
      showProcessingTimer = window.setTimeout(() => {
        ensureFeedbackElements();
        processingIndicator.hidden = false;
      }, 150);
    }

    try {
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        const headers = new Headers(input instanceof Request ? input.headers : undefined);
        new Headers(init.headers).forEach((value, name) => headers.set(name, value));
        headers.set('X-XSRF-TOKEN', await getCsrfToken());
        args = [input, { ...init, headers }];
      }
      const response = await originalFetch(...args);
      const authPath = `${API_URL.pathname.replace(/\/$/, '')}/auth/`;
      const readOnlyPostPaths = [
        `${authPath}validate-reset-token`,
        `${authPath}validate-email-token`,
        `${API_PATH_PREFIX}public/admissions/applications/status`,
      ];
      if (response.ok && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
          && ![`${authPath}login`, `${authPath}logout`, `${authPath}refresh`, ...readOnlyPostPaths]
            .includes(url.pathname)) {
        notifySuccess();
      }
      return response;
    } finally {
      activeRequests = Math.max(0, activeRequests - 1);
      if (activeRequests === 0) {
        window.clearTimeout(showProcessingTimer);
        if (processingIndicator) processingIndicator.hidden = true;
      }
    }
  };
}

installRequestFeedback();

function normalizeRole(roleValue) {
  return String(roleValue || '').trim().toUpperCase();
}

function getPortalPageForRole(roleValue) {
  switch (normalizeRole(roleValue)) {
    case 'ADMIN':
      return 'admin-dashboard.html';
    case 'TEACHER':
      return 'teacher-dashboard.html';
    case 'STUDENT':
      return 'dashboard.html';
    default:
      return 'index.html';
  }
}

function getLoginPageForRole(roleValue = getRole()) {
  return ['ADMIN', 'TEACHER'].includes(normalizeRole(roleValue))
    ? 'admin-login.html'
    : 'index.html';
}

function getRole() { return localStorage.getItem(ROLE_KEY); }
function getUsername() { return localStorage.getItem(USERNAME_KEY); }
function getPasswordChangeRequired() {
  return localStorage.getItem(PASSWORD_CHANGE_KEY) === 'true';
}
function isMfaEnrollmentRequired() {
  return localStorage.getItem(MFA_ENROLLMENT_KEY) === 'true';
}

async function getResponseErrorMessage(response, fallback) {
  const contentType = response.headers?.get('content-type') || '';
  const isJson = isJsonResponse(response);
  const bodyResponse = typeof response.clone === 'function' ? response.clone() : response;

  if (isJson) {
    try {
      const body = await bodyResponse.json();
      const message = typeof body?.message === 'string' ? body.message.trim() : '';
      if (message && !/^\s*(?:<!doctype\s+html|<html\b|<head\b|<body\b)/i.test(message)) {
        return message.slice(0, 300);
      }
    } catch (error) {
      return fallback;
    }
    return fallback;
  }

  if (response.status >= 500 || /text\/html/i.test(contentType)) return fallback;

  try {
    const text = (await bodyResponse.text()).trim();
    if (!text || /^\s*(?:<!doctype\s+html|<html\b|<head\b|<body\b)/i.test(text)) return fallback;
    return text.slice(0, 300);
  } catch (error) {
    return fallback;
  }
}

function isJsonResponse(response) {
  return /(?:application\/json|[+]json)\b/i.test(response.headers?.get('content-type') || '');
}

function setSession(role, username, passwordChangeRequired = false) {
  localStorage.setItem(ROLE_KEY, role);
  localStorage.setItem(USERNAME_KEY, username);
  localStorage.setItem(PASSWORD_CHANGE_KEY, String(Boolean(passwordChangeRequired)));
  localStorage.removeItem(MFA_ENROLLMENT_KEY);
}

function setMfaEnrollmentRequired(required) {
  if (required) {
    localStorage.setItem(MFA_ENROLLMENT_KEY, 'true');
  } else {
    localStorage.removeItem(MFA_ENROLLMENT_KEY);
  }
}

function clearSession() {
  localStorage.removeItem(ROLE_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(PASSWORD_CHANGE_KEY);
  localStorage.removeItem(MFA_ENROLLMENT_KEY);
}

let refreshInFlight = null;

/**
 * Attempts to exchange the refresh-token cookie for a new access-token cookie.
 * Coalesces concurrent callers into a single in-flight request, so a page that
 * fires several API calls at once on load doesn't trigger several parallel
 * refresh attempts.
 */
function attemptRefresh() {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API}/auth/refresh`, { method: 'POST', credentials: 'include' })
      .then(res => res.ok)
      .catch(() => false)
      .finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

/**
 * The one fetch wrapper every page-specific script should use for API calls.
 * - Always sends the HttpOnly auth cookie (credentials: 'include').
 * - On a 401, tries exactly one silent refresh-and-retry before giving up -
 *   this is what makes a short-lived (1 hour) access token invisible to the
 *   user instead of forcing a re-login every hour.
 * - If refresh also fails, clears the session hint and redirects to login.
 */
async function apiFetch(path, options = {}) {
  const doFetch = () => fetch(`${API}${path}`, {
    ...options,
    credentials: 'include',
    headers: options.body instanceof FormData
      ? (options.headers || {})
      : { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });

  let res = await doFetch();

  if (res.status === 401) {
    const refreshed = await attemptRefresh();
    if (refreshed) {
      res = await doFetch();
    }
    if (res.status === 401) {
      clearSession();
      window.location.href = '/admin-login.html';
      throw new Error('Session expired');
    }
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.clone().json();
      if (body && typeof body.message === 'string') message = body.message;
    } catch (e) {
      // Some endpoints return an empty response or plain text on failure.
    }
    throw new Error(message);
  }

  return res;
}

async function apiGet(path) {
  const res = await apiFetch(path);
  if (!res.ok) throw new Error(`Request to ${path} failed (${res.status})`);
  return res.json();
}

function safeDownloadFilename(value, fallback = 'download') {
  const filename = String(value || fallback)
    .replace(/[\r\n"]/g, '')
    .replace(/[\\/]/g, '_')
    .trim();
  return filename || fallback;
}

function filenameFromResponse(response, fallbackFilename = 'download') {
  const disposition = response.headers.get('Content-Disposition') || '';
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (encoded) {
    try {
      return safeDownloadFilename(decodeURIComponent(encoded[1]), fallbackFilename);
    } catch (error) {
      return safeDownloadFilename(encoded[1], fallbackFilename);
    }
  }
  const quoted = disposition.match(/filename="([^"]+)"/i);
  const plain = disposition.match(/filename=([^;]+)/i);
  return safeDownloadFilename(quoted?.[1] || plain?.[1], fallbackFilename);
}

function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = safeDownloadFilename(filename);
  link.hidden = true;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function csvCell(value) {
  let text = value === null || value === undefined
    ? ''
    : (typeof value === 'object' ? JSON.stringify(value) : String(value));
  if (/^\s*[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

function recordsToCsv(records) {
  if (!records.length) throw new Error('There are no records to export.');
  const columns = [];
  const seen = new Set();
  records.forEach(record => {
    if (record && typeof record === 'object' && !Array.isArray(record)) {
      Object.keys(record).forEach(key => {
        if (!seen.has(key)) {
          seen.add(key);
          columns.push(key);
        }
      });
    }
  });
  if (!columns.length) {
    return `\uFEFF"value"\r\n${records.map(csvCell).join('\r\n')}`;
  }
  const rows = [columns.map(csvCell).join(',')];
  records.forEach(record => {
    rows.push(columns.map(column => csvCell(record?.[column])).join(','));
  });
  return `\uFEFF${rows.join('\r\n')}`;
}

function downloadCsv(filename, records) {
  const csv = recordsToCsv(Array.isArray(records) ? records : [records]);
  triggerBlobDownload(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename);
}

async function downloadResponse(response, fallbackFilename = 'download') {
  if (!response?.ok) throw new Error('The requested file could not be downloaded.');
  triggerBlobDownload(await response.blob(), filenameFromResponse(response, fallbackFilename));
}

function validateUploadFile(file, allowedExtensions, maxBytes = 10 * 1024 * 1024) {
  if (!file) throw new Error('Choose a file before uploading.');
  if (file.size <= 0) throw new Error('The selected file is empty.');
  if (file.size > maxBytes) throw new Error(`Files must be ${Math.floor(maxBytes / (1024 * 1024))} MB or smaller.`);
  const extension = String(file.name || '').split('.').pop().toLowerCase();
  if (!allowedExtensions.includes(extension)) {
    throw new Error(`Unsupported file type. Allowed: ${allowedExtensions.map(value => `.${value}`).join(', ')}.`);
  }
  return file;
}

/**
 * Redirects to the appropriate login page unless the localStorage role hint
 * matches one of the allowed roles. This is a UX shortcut only (avoids
 * flashing protected content before the first real API call would 401) - the
 * actual enforcement is server-side on every /api/** call via apiFetch above.
 */
function requireRole(allowedRoles, loginPage = 'index.html') {
  const role = normalizeRole(getRole());
  const effectiveRoles = new Set((Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles]).map(normalizeRole));

  if (!role || !effectiveRoles.has(role)) {
    window.location.href = loginPage;
    return false;
  }

  const currentPage = (window.location.pathname.split('/').pop() || 'index.html').toLowerCase();
  const expectedPage = getPortalPageForRole(role).toLowerCase();
  const protectedEntryPages = new Set(['dashboard.html', 'teacher-dashboard.html', 'admin-dashboard.html']);

  if (protectedEntryPages.has(currentPage) && currentPage !== expectedPage) {
    window.location.href = expectedPage;
    return false;
  }

  return true;
}

async function logout(redirectTo = getLoginPageForRole()) {
  try {
    await fetch(`${API}/auth/logout`, { method: 'POST', credentials: 'include' });
  } catch (e) {
    // Best-effort - even if this fails, clear the local hint and send them to
    // login; the access-token cookie will simply expire on its own regardless.
  }
  clearSession();
  window.location.href = redirectTo;
}

function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function loadPwaUpdateSupport() {
  if (!('serviceWorker' in navigator) || window.__mitPortalPwaUpdateScriptRequested) return;
  window.__mitPortalPwaUpdateScriptRequested = true;
  const script = document.createElement('script');
  script.src = '/js/pwa-update.js';
  script.async = true;
  script.onerror = () => console.error('Unable to load MIT Portal update support.');
  document.head.appendChild(script);
}

loadPwaUpdateSupport();
