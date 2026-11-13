requireRole(['STUDENT']);

const researchSummary = document.getElementById('researchSummary');
const projectList = document.getElementById('projectList');
const projectForm = document.getElementById('projectForm');
const thesisForm = document.getElementById('thesisForm');
const thesisProjectSelect = document.getElementById('thesisProjectSelect');
let researchProjects = [];
let researchSupervisors = [];
let thesisSubmissions = [];

const supervisorSelect = document.getElementById('supervisorUserSelect');
const supervisorLoadStatus = document.getElementById('supervisorLoadStatus');
const thesisList = document.getElementById('thesisList');
const thesisStatus = document.getElementById('thesisStatus');

function renderProjectOptions() {
  if (!thesisProjectSelect) return;
  const approvedProjects = researchProjects.filter(project => project.status === 'APPROVED');
  thesisProjectSelect.innerHTML = approvedProjects.length
    ? approvedProjects.map(project => `<option value="${project.id}">${escapeHtml(project.title)}</option>`).join('')
    : '<option value="">No project available</option>';
  thesisProjectSelect.disabled = approvedProjects.length === 0;
}

function renderProjects() {
  if (!projectList) return;
  const supervisorsById = new Map(researchSupervisors.map(supervisor => [Number(supervisor.userId), supervisor]));
  projectList.innerHTML = researchProjects.length
    ? researchProjects.map(project => `
      <article class="project-item">
        <div class="notification-top-row">
          <div>
            <div class="notification-title">${escapeHtml(project.title)}</div>
            <div class="notification-meta">Supervisor: ${escapeHtml(supervisorsById.get(Number(project.supervisorUserId))?.fullName || (project.supervisorUserId ? `#${project.supervisorUserId}` : 'Not assigned'))} • ${escapeHtml((project.createdAt || '').slice(0, 10))}</div>
          </div>
          <span class="status-badge">${escapeHtml(project.status || 'PROPOSED')}</span>
        </div>
        <div class="notification-message">${escapeHtml(project.abstractText || 'No abstract provided.')}</div>
      </article>
    `).join('')
    : '<p class="document-empty">No research projects have been submitted yet.</p>';
  renderProjectOptions();
}

function renderSupervisors() {
  if (!supervisorSelect) return;
  supervisorSelect.innerHTML = '<option value="">No supervisor selected</option>' + researchSupervisors
    .map(supervisor => `<option value="${escapeHtml(supervisor.userId)}">${escapeHtml(supervisor.fullName)}${supervisor.department ? ` · ${escapeHtml(supervisor.department)}` : ''}</option>`)
    .join('');
}

function renderTheses() {
  if (!thesisList) return;
  thesisList.innerHTML = thesisSubmissions.length
    ? thesisSubmissions.map(thesis => {
      const project = researchProjects.find(item => Number(item.id) === Number(thesis.researchProjectId));
      return `<article class="project-item">
        <div class="notification-top-row">
          <div>
            <div class="notification-title">${escapeHtml(project?.title || `Project #${thesis.researchProjectId}`)}</div>
            <div class="notification-meta">Submitted ${escapeHtml((thesis.submittedAt || '').slice(0, 10))}</div>
          </div>
          <span class="status-badge">${escapeHtml(thesis.status)}</span>
        </div>
        ${thesis.notes ? `<p>${escapeHtml(thesis.notes)}</p>` : ''}
        <button class="btn-secondary" type="button" data-download-thesis="${escapeHtml(thesis.id)}">Download submitted PDF</button>
      </article>`;
    }).join('')
    : '<p class="document-empty">No thesis submissions yet.</p>';
}

async function loadSupervisors() {
  if (!supervisorSelect) return;
  try {
    const supervisors = await apiGet('/research/supervisors');
    if (!Array.isArray(supervisors)) throw new Error('The server returned an invalid supervisor list.');
    researchSupervisors = supervisors;
    renderSupervisors();
    renderProjects();
    if (supervisorLoadStatus) supervisorLoadStatus.textContent = supervisors.length
      ? 'Select an active teacher as your supervisor, if assigned.'
      : 'No active teachers are available for supervisor assignment.';
  } catch (err) {
    console.error('Failed to load research supervisors', err);
    if (supervisorLoadStatus) supervisorLoadStatus.textContent = 'Supervisor options could not be loaded. You may submit without selecting a supervisor.';
  }
}

async function loadResearch() {
  try {
    const projects = await apiGet('/research/projects/mine');
    if (!Array.isArray(projects)) throw new Error('The server returned an invalid research project list.');
    researchProjects = projects;
    thesisSubmissions = await apiGet('/research/theses/mine');
    if (!Array.isArray(thesisSubmissions)) throw new Error('The server returned an invalid thesis submission list.');
    if (researchSummary) {
      researchSummary.textContent = projects.length
        ? `${projects.length} project${projects.length === 1 ? '' : 's'} tracked in your portfolio.`
        : 'No research proposals yet. Start by drafting a project below.';
    }
    renderProjects();
    renderTheses();
  } catch (err) {
    console.error('Failed to load research', err);
    if (researchSummary) researchSummary.textContent = 'Research data is unavailable right now.';
    if (projectList) projectList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load projects.')}</p>`;
  }
}

if (projectForm) {
  projectForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const formData = new FormData(projectForm);
    const title = String(formData.get('title') || '').trim();
    const abstractText = String(formData.get('abstractText') || '').trim();
    const supervisorUserId = formData.get('supervisorUserId');

    if (!title) {
      alert('Please add a project title before saving.');
      return;
    }

    try {
      await apiFetch('/research/projects', {
        method: 'POST',
        body: JSON.stringify({
          title,
          abstractText,
          supervisorUserId: supervisorUserId ? Number(supervisorUserId) : null,
        }),
      });
      projectForm.reset();
      await loadResearch();
    } catch (err) {
      alert(err.message || 'Project proposal could not be saved.');
    }
  });
}

if (thesisForm) {
  thesisForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(thesisForm);
    const researchProjectId = String(form.get('researchProjectId') || '');
    let file = form.get('file');
    if (!researchProjectId || !(file instanceof File) || !file.size) {
      if (thesisStatus) thesisStatus.textContent = 'Select an approved project and a thesis PDF.';
      return;
    }
    try {
      file = validateUploadFile(file, ['pdf']);
    } catch (error) {
      if (thesisStatus) thesisStatus.textContent = error.message;
      return;
    }
    const submitButton = thesisForm.querySelector('[type="submit"]');
    if (submitButton) submitButton.disabled = true;
    if (thesisStatus) thesisStatus.textContent = 'Uploading thesis PDF...';
    try {
      const upload = new FormData();
      upload.append('researchProjectId', researchProjectId);
      upload.append('notes', String(form.get('notes') || '').trim());
      upload.append('file', file);
      await apiFetch('/research/theses', {
        method: 'POST',
        body: upload,
      });
      thesisForm.reset();
      renderProjectOptions();
      if (thesisStatus) thesisStatus.textContent = 'Thesis submitted successfully.';
      thesisSubmissions = await apiGet('/research/theses/mine');
      renderTheses();
    } catch (err) {
      if (thesisStatus) thesisStatus.textContent = err.message || 'Thesis submission failed.';
    } finally {
      if (submitButton) submitButton.disabled = false;
    }
  });
}

thesisList?.addEventListener('click', async event => {
  const button = event.target.closest('[data-download-thesis]');
  if (!button) return;
  const id = button.dataset.downloadThesis;
  try {
    const response = await apiFetch(`/research/theses/${encodeURIComponent(id)}/download`);
    await downloadResponse(response, `thesis-submission-${id}.pdf`);
  } catch (err) {
    if (thesisStatus) thesisStatus.textContent = err.message || 'Thesis download failed.';
  }
});

document.addEventListener('DOMContentLoaded', () => {
  loadSupervisors();
  loadResearch();
});
