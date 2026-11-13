requireRole(['STUDENT']);

const scholarshipList = document.getElementById('scholarshipList');
const summary = document.getElementById('scholarshipSummary');

async function loadScholarships() {
  try {
    const [scholarships, applications] = await Promise.all([
      apiGet('/portal/scholarships'),
      apiGet('/portal/student/scholarship-applications'),
    ]);

    const appMap = new Map((applications || []).map(app => [app.scholarshipId, app]));
    if (summary) summary.textContent = `${scholarships.length} funding option${scholarships.length === 1 ? '' : 's'}`;

    if (!scholarshipList) return;
    scholarshipList.innerHTML = scholarships.length ? scholarships.map(item => {
      const application = appMap.get(item.id);
      const buttonLabel = application ? 'Applied' : (item.open ? 'Apply' : 'Closed');
      const disabled = Boolean(application) || !item.open;
      return `
        <article class="scholarship-card" data-scholarship-id="${item.id}">
          <div class="scholarship-header">
            <h3>${escapeHtml(item.name)}</h3>
            <span class="scholarship-badge">${application ? escapeHtml(application.status || 'PENDING') : (item.open ? 'Open' : 'Closed')}</span>
          </div>
          <div class="scholarship-body">
            <p>${escapeHtml(item.description || '')}</p>
            <p>Amount: UGX ${Number(item.amount || 0).toLocaleString()}</p>
            <button class="btn-primary" type="button" data-apply-id="${item.id}" ${disabled ? 'disabled' : ''}>${buttonLabel}</button>
          </div>
        </article>
      `;
    }).join('') : '<p class="document-empty">No scholarships are currently available.</p>';

    scholarshipList.querySelectorAll('[data-apply-id]').forEach(button => {
      button.addEventListener('click', async () => {
        const id = Number(button.dataset.applyId);
        if (!id) return;
        try {
          await apiFetch(`/portal/student/scholarships/${id}/apply`, { method: 'POST' });
          await loadScholarships();
        } catch (err) {
          alert(err.message || 'Scholarship application failed.');
        }
      });
    });
  } catch (err) {
    console.error('Failed to load scholarships', err);
    if (scholarshipList) scholarshipList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load scholarships.')}</p>`;
    if (summary) summary.textContent = 'Scholarships unavailable.';
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadScholarships();
});
