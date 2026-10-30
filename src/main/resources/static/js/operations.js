const operationDomains = [
  { name: 'Admissions', description: 'Review applications and enroll accepted applicants.', roles: ['ADMIN'], endpoints: [
    { label: 'Review applications', method: 'GET', path: '/admissions/applications', permissions: ['PERM_ADMISSIONS_REVIEW'] },
    { label: 'Decide application', method: 'PATCH', path: '/admissions/applications/{id}/decision', permissions: ['PERM_ADMISSIONS_REVIEW'], body: { status: 'ACCEPTED', reviewerNotes: '' }, fieldOptions: { status: ['ACCEPTED', 'REJECTED'] } },
    { label: 'Find matching student accounts', method: 'GET', path: '/admissions/applications/{id}/enrollment-candidates', permissions: ['PERM_ADMISSIONS_REVIEW'] },
    { label: 'Enroll an existing student account', method: 'POST', path: '/admissions/applications/{id}/enroll', permissions: ['PERM_ADMISSIONS_REVIEW'], body: { studentId: 0 } },
    { label: 'Create account and enroll accepted applicant', method: 'POST', path: '/admissions/applications/{id}/enroll-new', permissions: ['PERM_ADMISSIONS_REVIEW'], body: { username: '', fullName: '', email: '', phone: '' }, optionalFields: ['phone'] },
  ] },
  { name: 'Accommodation', description: 'Review pending student applications, manage rooms, and create allocations.', roles: ['ADMIN', 'STUDENT'], endpoints: [
    { label: 'List rooms', method: 'GET', path: '/accommodation/rooms', roles: ['ADMIN', 'STUDENT'], roleAccess: ['STUDENT'], permissions: ['PERM_ACCOMMODATION_MANAGE'] },
    { label: 'Create room', method: 'POST', path: '/accommodation/rooms', roles: ['ADMIN'], permissions: ['PERM_ACCOMMODATION_MANAGE'], body: { roomCode: '', building: '', capacity: 1 } },
    { label: 'Pending applications', method: 'GET', path: '/accommodation/applications/pending', roles: ['ADMIN'], permissions: ['PERM_ACCOMMODATION_MANAGE'] },
    { label: 'Create allocation', method: 'POST', path: '/accommodation/allocations', roles: ['ADMIN'], permissions: ['PERM_ACCOMMODATION_MANAGE'], body: { applicationId: 0, hostelRoomId: 0 } },
    { label: 'My allocation', method: 'GET', path: '/accommodation/allocations/mine', roles: ['STUDENT'] },
  ] },
  { name: 'Curriculum', description: 'Maintain curriculum courses and prerequisites.', roles: ['ADMIN', 'TEACHER', 'STUDENT'], endpoints: [
    { label: 'My curriculum', method: 'GET', path: '/curriculum/mine', roles: ['STUDENT'] },
    { label: 'Create curriculum course', method: 'POST', path: '/curriculum/courses', permissions: ['PERM_REGISTRATION_MANAGE'], body: { programId: 0, courseId: 0, semesterNumber: 1, required: true } },
    { label: 'Create prerequisite', method: 'POST', path: '/curriculum/prerequisites', permissions: ['PERM_REGISTRATION_MANAGE'], body: { courseId: 0, prerequisiteCourseId: 0 } },
  ] },
  { name: 'Finance extensions', description: 'Create student fees and manage waivers, holds, refunds, and reconciliation entries.', roles: ['ADMIN', 'STUDENT'], endpoints: [
    { label: 'List student fees', method: 'GET', path: '/admin/fees', roles: ['ADMIN'], permissions: ['PERM_FINANCE_FEE_MANAGE'] },
    { label: 'Create student fee', method: 'POST', path: '/fees', roles: ['ADMIN'], permissions: ['PERM_FINANCE_FEE_MANAGE'], body: { studentId: 0, description: '', amount: 0.01, dueDate: '' } },
    { label: 'Submit fee waiver', method: 'POST', path: '/finance/waivers', roles: ['ADMIN'], permissions: ['PERM_FINANCE_WAIVER'], body: { feeId: 0, amount: 0.01, reason: '' } },
    { label: 'Create financial hold', method: 'POST', path: '/finance/holds', roles: ['ADMIN'], permissions: ['PERM_FINANCE_HOLD_MANAGE'], body: { studentId: 0, reason: '' } },
    { label: 'My financial holds', method: 'GET', path: '/finance/holds/mine', roles: ['STUDENT'] },
    { label: 'Release financial hold', method: 'POST', path: '/finance/holds/{id}/release', roles: ['ADMIN'], permissions: ['PERM_FINANCE_HOLD_MANAGE'] },
    { label: 'Create refund', method: 'POST', path: '/finance/refunds', roles: ['ADMIN'], permissions: ['PERM_FINANCE_REFUND'], body: { paymentAttemptId: 0, amount: 0.01, reason: '' } },
    { label: 'Create reconciliation entry', method: 'POST', path: '/finance/reconciliation/entries', roles: ['ADMIN'], permissions: ['PERM_FINANCE_RECONCILIATION'], body: { providerReference: '', statementAmount: 0.01, statementDate: '' } },
    { label: 'List reconciliation entries', method: 'GET', path: '/finance/reconciliation/entries', roles: ['ADMIN'], permissions: ['PERM_FINANCE_RECONCILIATION'] },
    { label: 'Reconciliation summary', method: 'GET', path: '/finance/reconciliation/summary', roles: ['ADMIN'], permissions: ['PERM_FINANCE_RECONCILIATION'] },
  ] },
  { name: 'Human resources', description: 'Create staff records and review leave requests.', roles: ['ADMIN', 'TEACHER'], endpoints: [
    { label: 'List staff', method: 'GET', path: '/hr/staff', roles: ['ADMIN'], permissions: ['PERM_HR_MANAGE'] },
    { label: 'Create staff member', method: 'POST', path: '/hr/staff', roles: ['ADMIN'], permissions: ['PERM_HR_MANAGE'], body: { userId: 0, employeeNumber: '', fullName: '', title: '', departmentId: 0, hireDate: '' } },
    { label: 'Request leave', method: 'POST', path: '/hr/leave', roles: ['ADMIN', 'TEACHER'], roleAccess: ['ADMIN', 'TEACHER'], permissions: ['PERM_LEAVE_REQUEST'], body: { startDate: '', endDate: '', reason: '' } },
    { label: 'My leave', method: 'GET', path: '/hr/leave/mine', roles: ['ADMIN', 'TEACHER'], roleAccess: ['ADMIN', 'TEACHER'], permissions: ['PERM_LEAVE_REQUEST'] },
    { label: 'Pending leave', method: 'GET', path: '/hr/leave/pending', roles: ['ADMIN'], permissions: ['PERM_HR_MANAGE'] },
    { label: 'Review leave', method: 'POST', path: '/hr/leave/{id}/review', roles: ['ADMIN'], permissions: ['PERM_HR_MANAGE'], body: { status: 'APPROVED' }, fieldOptions: { status: ['APPROVED', 'REJECTED'] } },
  ] },
  { name: 'Scholarships', description: 'Create funding opportunities, manage application availability, and review student applications.', roles: ['ADMIN'], endpoints: [
    { label: 'List scholarships', method: 'GET', path: '/admin/scholarships' },
    { label: 'Create scholarship', method: 'POST', path: '/admin/scholarships', body: { name: '', description: '', amount: 0.01 } },
    { label: 'Open or close applications', method: 'PATCH', path: '/admin/scholarships/{id}/availability', query: { open: true } },
    { label: 'List scholarship applications', method: 'GET', path: '/admin/scholarships/applications' },
    { label: 'Review scholarship application', method: 'PATCH', path: '/admin/scholarships/applications/{id}/status', body: { status: 'APPROVED' }, fieldOptions: { status: ['APPROVED', 'REJECTED'] } },
  ] },
  { name: 'Library', description: 'Search the catalogue and manage loans.', roles: ['ADMIN', 'STUDENT', 'TEACHER'], endpoints: [
    { label: 'Search catalogue', method: 'GET', path: '/library/catalog' },
    { label: 'Add library item', method: 'POST', path: '/library/items', roles: ['ADMIN'], permissions: ['PERM_LIBRARY_MANAGE'], body: { isbn: '', title: '', author: '', totalCopies: 1 } },
    { label: 'My loans', method: 'GET', path: '/library/loans/mine', roles: ['STUDENT'] },
    { label: 'Create loan', method: 'POST', path: '/library/loans', roles: ['ADMIN'], permissions: ['PERM_LIBRARY_MANAGE'], body: { studentId: 0, libraryItemId: 0, dueDate: '' } },
    { label: 'Return loan', method: 'POST', path: '/library/loans/{id}/return', roles: ['ADMIN'], permissions: ['PERM_LIBRARY_MANAGE'] },
  ] },
  { name: 'Procurement and assets', description: 'Manage suppliers, requests, and institutional assets.', roles: ['ADMIN'], endpoints: [
    { label: 'List suppliers', method: 'GET', path: '/procurement/suppliers', permissions: ['PERM_PROCUREMENT_MANAGE'] },
    { label: 'Create supplier', method: 'POST', path: '/procurement/suppliers', permissions: ['PERM_PROCUREMENT_MANAGE'], body: { supplierCode: '', name: '', email: '', phone: '' } },
    { label: 'Pending requests', method: 'GET', path: '/procurement/requests/pending', permissions: ['PERM_PROCUREMENT_MANAGE'] },
    { label: 'Create procurement request', method: 'POST', path: '/procurement/requests', permissions: ['PERM_PROCUREMENT_MANAGE'], body: { description: '', estimatedAmount: 0.01, supplierId: 0 } },
    { label: 'Review request', method: 'POST', path: '/procurement/requests/{id}/review', permissions: ['PERM_PROCUREMENT_MANAGE'], body: { status: 'APPROVED' }, fieldOptions: { status: ['APPROVED', 'REJECTED'] } },
    { label: 'List assets', method: 'GET', path: '/procurement/assets', permissions: ['PERM_PROCUREMENT_MANAGE'] },
    { label: 'Create asset', method: 'POST', path: '/procurement/assets', permissions: ['PERM_PROCUREMENT_MANAGE'], body: { assetTag: '', name: '', description: '', purchaseValue: 0, purchaseDate: '', assignedToUserId: 0 } },
  ] },
  { name: 'Research and theses', description: 'Review submitted projects and thesis workflows. Students submit projects and thesis PDFs from the Research page.', roles: ['ADMIN', 'TEACHER', 'STUDENT'], endpoints: [
    { label: 'My projects', method: 'GET', path: '/research/projects/mine', roles: ['STUDENT'] },
    { label: 'My thesis submissions', method: 'GET', path: '/research/theses/mine', roles: ['STUDENT'] },
    { label: 'Pending projects', method: 'GET', path: '/research/projects/pending', roles: ['ADMIN', 'TEACHER'], permissions: ['PERM_RESEARCH_REVIEW'] },
    { label: 'Review project', method: 'POST', path: '/research/projects/{id}/review', roles: ['ADMIN', 'TEACHER'], permissions: ['PERM_RESEARCH_REVIEW'], body: { status: 'APPROVED' }, fieldOptions: { status: ['APPROVED', 'REJECTED'] } },
    { label: 'Pending theses', method: 'GET', path: '/research/theses/pending', roles: ['ADMIN', 'TEACHER'], permissions: ['PERM_RESEARCH_REVIEW'] },
    { label: 'Review thesis', method: 'POST', path: '/research/theses/{id}/review', roles: ['ADMIN', 'TEACHER'], permissions: ['PERM_RESEARCH_REVIEW'], body: { status: 'APPROVED' }, fieldOptions: { status: ['APPROVED', 'REVISION_REQUIRED', 'REJECTED'] } },
  ] },
  { name: 'Registration', description: 'Browse sections, register, and withdraw from courses.', roles: ['ADMIN', 'STUDENT'], endpoints: [
    { label: 'List sections', method: 'GET', path: '/registration/sections', query: { termId: 0 } },
    { label: 'Create course section', method: 'POST', path: '/registration/sections', roles: ['ADMIN'], permissions: ['PERM_REGISTRATION_MANAGE'], body: { courseId: 0, academicTermId: 0, sectionCode: '', capacity: 1 } },
    { label: 'My registrations', method: 'GET', path: '/registration/mine', roles: ['STUDENT'] },
    { label: 'Register for section', method: 'POST', path: '/registration/sections/{sectionId}', roles: ['STUDENT'] },
    { label: 'Withdraw registration', method: 'DELETE', path: '/registration/{registrationId}', roles: ['STUDENT'] },
  ] },
  { name: 'Exam permits', description: 'Review and approve student examination permit requests.', roles: ['ADMIN'], endpoints: [
    { label: 'Pending permits', method: 'GET', path: '/admin/exam-permits/pending', permissions: ['PERM_EXAM_PERMIT_MANAGE'] },
    { label: 'Approve permit', method: 'POST', path: '/admin/exam-permits/{permitId}/approve', permissions: ['PERM_EXAM_PERMIT_MANAGE'] },
    { label: 'Reject permit', method: 'POST', path: '/admin/exam-permits/{permitId}/reject', permissions: ['PERM_EXAM_PERMIT_MANAGE'], body: { reason: '' } },
  ] },
  { name: 'Permissions', description: 'Manage permissions and role assignments.', roles: ['ADMIN'], endpoints: [
    { label: 'List permissions', method: 'GET', path: '/permissions' },
    { label: 'Create permission', method: 'POST', path: '/permissions', body: { code: '', description: '' } },
    { label: 'Assign permission', method: 'POST', path: '/permissions/assign', body: { role: '', permissionId: 0 } },
    { label: 'List role permissions', method: 'GET', path: '/permissions/roles/{role}' },
    { label: 'Remove assignment', method: 'DELETE', path: '/permissions/assign', body: { role: '', permissionId: 0 } },
  ] },
  { name: 'Quality and accreditation', description: 'Track programme outcomes and accreditation reviews.', roles: ['ADMIN', 'TEACHER'], endpoints: [
    { label: 'Create programme outcome', method: 'POST', path: '/quality/outcomes', permissions: ['PERM_QUALITY_REVIEW'], body: { programId: 0, code: '', description: '' } },
    { label: 'Programme outcomes', method: 'GET', path: '/quality/programs/{programId}/outcomes', permissions: ['PERM_QUALITY_REVIEW'] },
    { label: 'Pending accreditations', method: 'GET', path: '/quality/accreditations/pending', permissions: ['PERM_QUALITY_REVIEW'] },
    { label: 'Create accreditation', method: 'POST', path: '/quality/accreditations', permissions: ['PERM_QUALITY_REVIEW'], body: { programId: 0, accreditingBody: '', certificateReference: '', validFrom: '', validUntil: '', evidencePath: '' } },
    { label: 'Review accreditation', method: 'POST', path: '/quality/accreditations/{id}/review', permissions: ['PERM_QUALITY_REVIEW'], body: { status: 'ACCREDITED' }, fieldOptions: { status: ['ACCREDITED', 'CONDITIONAL'] } },
  ] },
  { name: 'Institution directory', description: 'Use shared campus, faculty, department, programme, and term data.', roles: ['ADMIN', 'TEACHER', 'STUDENT'], endpoints: [
    { label: 'Campuses', method: 'GET', path: '/institution/campuses' },
    { label: 'Faculties', method: 'GET', path: '/institution/faculties', query: { campusId: 0 } },
    { label: 'Departments', method: 'GET', path: '/institution/departments', query: { facultyId: 0 } },
    { label: 'Programmes', method: 'GET', path: '/institution/programs', query: { departmentId: 0 } },
    { label: 'Current academic year', method: 'GET', path: '/institution/academic-years/current' },
    { label: 'Open academic terms', method: 'GET', path: '/institution/academic-terms/open' },
  ] },
  { name: 'Academic records', description: 'Retrieve transcripts and degree audits for authorized students.', roles: ['ADMIN', 'TEACHER', 'STUDENT'], endpoints: [
    { label: 'Transcript', method: 'GET', path: '/academic-records/{studentId}/transcript' },
    { label: 'Degree audit', method: 'GET', path: '/academic-records/{studentId}/degree-audit' },
    { label: 'Download transcript', method: 'GET', path: '/academic-records/{studentId}/transcript/download' },
  ] },
  { name: 'Grade approvals', description: 'Administrators review pending teacher-submitted grades.', roles: ['ADMIN'], endpoints: [
    { label: 'Pending grade approvals', method: 'GET', path: '/admin/grades/pending', roles: ['ADMIN'] },
    { label: 'Approve grade', method: 'POST', path: '/admin/grades/{gradeId}/approve', roles: ['ADMIN'] },
    { label: 'Reject grade', method: 'POST', path: '/admin/grades/{gradeId}/reject', roles: ['ADMIN'], body: { reason: '' } },
  ] },
  { name: 'Payments', description: 'Start and confirm student payment attempts.', roles: ['ADMIN', 'STUDENT'], endpoints: [
    { label: 'My payment attempts', method: 'GET', path: '/finance/payments/attempts/mine', roles: ['STUDENT'] },
    { label: 'Start payment attempt', method: 'POST', path: '/finance/payments/attempts', roles: ['STUDENT'], body: { feeId: 0, amount: 0.01, paymentChannel: 'BANK_TRANSFER', providerReference: '' } },
    { label: 'Confirm payment attempt', method: 'POST', path: '/finance/payments/attempts/{id}/confirm', roles: ['ADMIN'], permissions: ['PERM_FINANCE_PAYMENT_CONFIRM'] },
  ] },
  { name: 'Communication and requests', description: 'Publish announcements, send messages, and manage service requests.', roles: ['ADMIN', 'TEACHER', 'STUDENT'], endpoints: [
    { label: 'Announcements', method: 'GET', path: '/announcements' },
    { label: 'Publish announcement', method: 'POST', path: '/announcements', roles: ['ADMIN', 'TEACHER'], roleAccess: ['ADMIN', 'TEACHER'], permissions: ['PERM_ANNOUNCEMENT_MANAGE'], body: { title: '', content: '' } },
    { label: 'Received messages', method: 'GET', path: '/messages/received' },
    { label: 'Sent messages', method: 'GET', path: '/messages/sent' },
    { label: 'Send message', method: 'POST', path: '/messages', body: { receiverUsername: '', subject: '', content: '' } },
    { label: 'My service requests', method: 'GET', path: '/student/requests', roles: ['STUDENT', 'TEACHER'] },
    { label: 'Create service request', method: 'POST', path: '/student/requests', roles: ['STUDENT', 'TEACHER'], body: { type: '', subject: '', details: '' } },
    { label: 'Admin service requests', method: 'GET', path: '/student/requests/admin', roles: ['ADMIN'], permissions: ['PERM_SERVICE_REQUEST_MANAGE'] },
    { label: 'Update request status', method: 'PATCH', path: '/student/requests/admin/{id}/status', roles: ['ADMIN'], permissions: ['PERM_SERVICE_REQUEST_MANAGE'], body: { status: 'IN_PROGRESS' }, fieldOptions: { status: ['OPEN', 'IN_PROGRESS', 'APPROVED', 'REJECTED', 'COMPLETED'] } },
  ] },
];

const state = { role: getRole(), authorities: new Set(), authoritiesLoaded: false, visible: operationDomains, activeRequest: null };
const grid = document.getElementById('workflowGrid');
const statusBox = document.getElementById('status');
const dialog = document.getElementById('requestDialog');

function visibleDomains() { return operationDomains.filter(domain => visibleEndpoints(domain).length > 0); }
function visibleEndpoints(domain) {
  if (!state.authoritiesLoaded) return [];
  return domain.endpoints.filter(endpoint => {
    if (endpoint.permissions) {
      return state.authorities.has('ADMIN')
        || (endpoint.roleAccess || []).some(role => state.authorities.has(role))
        || endpoint.permissions.some(permission => state.authorities.has(permission));
    }
    return (endpoint.roles || domain.roles).some(role => state.authorities.has(role));
  });
}
function setStatus(message, error = false) { statusBox.hidden = !message; statusBox.classList.toggle('error', error); statusBox.textContent = message; }
function fieldType(key, value) {
  if (typeof value === 'boolean') return 'checkbox';
  if (key.toLowerCase().includes('date')) return 'date';
  if (typeof value === 'number' || key.toLowerCase().endsWith('id') || key.toLowerCase() === 'capacity') return 'number';
  return ['reason', 'notes', 'details', 'content', 'abstractText', 'reviewerNotes', 'description'].includes(key) ? 'textarea' : 'text';
}
function renderFields(body, fieldOptions = {}, pathParams = [], kind = 'body', optionalFields = []) {
  const grid = document.getElementById('fieldGrid');
  const fields = Object.entries(body || {}).map(([key, value]) => {
    const type = fieldType(key, value);
    const options = fieldOptions[key];
    const required = !optionalFields.includes(key)
      && (value === '' || value === 0 || key.toLowerCase().endsWith('id') || Boolean(options));
    const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, char => char.toUpperCase());
    const fieldAttribute = kind === 'query' ? 'data-query' : 'data-field';
    if (options) return `<label>${escapeHtml(label)} *<select ${fieldAttribute}="${escapeHtml(key)}" required>${options.map(option => `<option value="${escapeHtml(option)}" ${option === value ? 'selected' : ''}>${escapeHtml(option)}</option>`).join('')}</select></label>`;
    if (type === 'checkbox') return `<label class="field-check"><input ${fieldAttribute}="${escapeHtml(key)}" type="checkbox" ${value ? 'checked' : ''}> <span>${escapeHtml(label)}</span></label>`;
    if (type === 'textarea') return `<label>${escapeHtml(label)}${required ? ' *' : ''}<textarea ${fieldAttribute}="${escapeHtml(key)}" rows="3" ${required ? 'required' : ''}>${escapeHtml(value)}</textarea></label>`;
    const numericStep = type === 'number' ? (Number.isInteger(value) ? '1' : 'any') : null;
    return `<label>${escapeHtml(label)}${required ? ' *' : ''}<input ${fieldAttribute}="${escapeHtml(key)}" type="${type}" value="${escapeHtml(value)}" ${numericStep ? `step="${numericStep}"` : ''} ${required ? 'required' : ''}></label>`;
  });
  const routeFields = pathParams.map(key => {
    const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, char => char.toUpperCase());
    const type = key.toLowerCase().includes('id') ? 'number' : 'text';
    return `<label>${escapeHtml(label)} *<input data-path-param="${escapeHtml(key)}" type="${type}" ${type === 'number' ? 'min="1" step="1"' : ''} required></label>`;
  });
  grid.innerHTML = [...routeFields, ...fields].join('');
}
function renderResult(title, text, path) {
  document.getElementById('resultPanel').hidden = false;
  document.getElementById('resultTitle').textContent = title;
  const content = document.getElementById('resultContent');
  if (path === '/research/theses/pending') {
    try {
      const theses = JSON.parse(text);
      if (Array.isArray(theses)) {
        content.innerHTML = theses.length ? theses.map(thesis => `
          <article class="workflow-card">
            <strong>Thesis submission #${escapeHtml(thesis.id)}</strong>
            <p>Research project #${escapeHtml(thesis.researchProjectId)} · ${escapeHtml(thesis.status)}</p>
            <p>${escapeHtml(thesis.notes || 'No reviewer notes provided.')}</p>
            <button class="button secondary" type="button" data-download-thesis="${escapeHtml(thesis.id)}">Download thesis PDF</button>
          </article>`).join('') : '<p>No thesis submissions are awaiting review.</p>';
        return;
      }
    } catch (error) {
      console.error('Research thesis response could not be rendered', error);
    }
  }
  content.textContent = formatResponse(text);
}
function renderDomains() {
  const search = document.getElementById('searchInput').value.trim().toLowerCase();
  const selected = document.getElementById('domainFilter').value;
  state.visible = visibleDomains().filter(domain => {
    const haystack = `${domain.name} ${domain.description} ${visibleEndpoints(domain).map(item => `${item.label} ${item.path}`).join(' ')}`.toLowerCase();
    return (!selected || domain.name === selected) && (!search || haystack.includes(search));
  });
  grid.innerHTML = state.visible.map((domain, domainIndex) => `<article class="workflow-card"><div class="domain-label">${escapeHtml(domain.name)}</div><h2>${escapeHtml(domain.name)}</h2><p>${escapeHtml(domain.description)}</p><div class="endpoint-list">${visibleEndpoints(domain).map((endpoint, endpointIndex) => `<div class="endpoint"><span class="endpoint-label"><span class="method ${endpoint.method.toLowerCase()}">${endpoint.method}</span> ${escapeHtml(endpoint.label)}<span class="endpoint-path">${escapeHtml(endpoint.path)}</span></span><button class="button secondary" type="button" data-domain="${domainIndex}" data-endpoint="${endpointIndex}">${endpoint.method === 'GET' ? 'Load' : 'Open'}</button></div>`).join('')}</div></article>`).join('') || '<p>No workflows match this filter.</p>';
  document.getElementById('summary').textContent = `${state.visible.length} domains available for ${state.role || 'guest'}.`;
  grid.querySelectorAll('[data-domain]').forEach(button => button.addEventListener('click', () => runEndpoint(visibleEndpoints(state.visible[Number(button.dataset.domain)])[Number(button.dataset.endpoint)])));
}
function openRequest(endpoint) {
  state.activeRequest = endpoint;
  document.getElementById('requestMethod').textContent = endpoint.method;
  document.getElementById('requestTitle').textContent = endpoint.label;
  document.getElementById('requestPath').textContent = endpoint.path;
  document.getElementById('pathInput').value = endpoint.path;
  const pathParams = [...endpoint.path.matchAll(/\{([^}]+)\}/g)].map(match => match[1]);
  renderFields(endpoint.body || endpoint.query, endpoint.fieldOptions, pathParams, endpoint.query ? 'query' : 'body', endpoint.optionalFields);
  dialog.showModal();
}
async function runEndpoint(endpoint) {
  if (endpoint.method === 'GET' && !endpoint.query && !endpoint.path.includes('{')) {
    await executeEndpoint(endpoint);
    return;
  }
  openRequest(endpoint);
}
function formatResponse(text) { try { return JSON.stringify(JSON.parse(text), null, 2); } catch (_) { return text || '(empty response)'; } }
document.getElementById('requestForm').addEventListener('submit', async event => {
  event.preventDefault();
  const endpoint = state.activeRequest;
  if (!endpoint) return;
  const pathParams = {};
  document.querySelectorAll('#fieldGrid [data-path-param]').forEach(field => {
    pathParams[field.dataset.pathParam] = field.value.trim();
  });
  const query = {};
  document.querySelectorAll('#fieldGrid [data-query]').forEach(field => {
    query[field.dataset.query] = field.type === 'checkbox' ? field.checked : field.value;
  });
  const body = {};
  document.querySelectorAll('#fieldGrid [data-field]').forEach(field => {
    body[field.dataset.field] = field.type === 'checkbox'
      ? field.checked
      : field.type === 'number' ? (field.value === '' ? null : Number(field.value)) : field.value;
  });

  const requestPath = endpoint.path.replace(/\{([^}]+)\}/g, (placeholder, key) => {
    const value = pathParams[key];
    if (!value) throw new Error(`Enter a value for ${key}.`);
    return encodeURIComponent(value);
  });
  if (Object.keys(query).length) {
    const search = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => search.set(key, String(value)));
    await executeEndpoint(endpoint, `${requestPath}?${search.toString()}`, body);
  } else {
    await executeEndpoint(endpoint, requestPath, body);
  }
});

async function executeEndpoint(endpoint, requestPath = endpoint.path, body = {}) {
  const options = { method: endpoint.method };
  if (endpoint.body && Object.keys(body).length) options.body = JSON.stringify(body);
  const sendButton = document.getElementById('sendButton');
  sendButton.disabled = true;
  try {
    const response = await apiFetch(requestPath, options);
    const text = await response.text();
    if (dialog.open) dialog.close();
    setStatus(`${endpoint.label} completed (${response.status}).`);
    renderResult(endpoint.label, text, endpoint.path);
  } catch (error) {
    setStatus(error.message || `${endpoint.label} failed.`, true);
  } finally {
    sendButton.disabled = false;
  }
}
document.getElementById('resultContent').addEventListener('click', async event => {
  const button = event.target.closest('[data-download-thesis]');
  if (!button) return;
  const id = button.dataset.downloadThesis;
  try {
    const response = await apiFetch(`/research/theses/${encodeURIComponent(id)}/download`);
    await downloadResponse(response, `thesis-submission-${id}.pdf`);
  } catch (error) {
    setStatus(error.message || 'Thesis download failed.', true);
  }
});
document.getElementById('searchInput').addEventListener('input', renderDomains);
document.getElementById('domainFilter').addEventListener('change', renderDomains);
document.getElementById('logoutButton').addEventListener('click', () => logout(getLoginPageForRole(state.role)));
document.getElementById('clearResult').addEventListener('click', () => { document.getElementById('resultPanel').hidden = true; document.getElementById('resultContent').textContent = ''; });
document.getElementById('roleBadge').textContent = state.role || 'Guest';
function renderDomainFilter() {
  const select = document.getElementById('domainFilter');
  const selected = select.value;
  select.replaceChildren(new Option('All domains', ''));
  visibleDomains().forEach(domain => select.add(new Option(domain.name, domain.name)));
  if (visibleDomains().some(domain => domain.name === selected)) select.value = selected;
}
async function loadAuthorities() {
  try {
    const response = await apiFetch('/auth/authorities');
    const authorities = await response.json();
    if (!Array.isArray(authorities) || authorities.some(authority => typeof authority !== 'string')) {
      throw new Error('The server returned an invalid authorities response.');
    }
    state.authorities = new Set(authorities);
    state.authoritiesLoaded = true;
    state.role = ['ADMIN', 'TEACHER', 'STUDENT'].find(role => state.authorities.has(role)) || 'USER';
    document.getElementById('roleBadge').textContent = state.role;
    document.getElementById('homeLink').href = getPortalPageForRole(state.role);
    renderDomainFilter();
    renderDomains();
  } catch (error) {
    setStatus(`Could not load delegated workflow access: ${error.message}`, true);
  }
}
if (!state.role) window.location.href = 'index.html';
else {
  document.getElementById('roleBadge').textContent = 'Checking access…';
  loadAuthorities();
}
