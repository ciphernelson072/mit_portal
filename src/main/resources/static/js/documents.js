requireRole(['STUDENT']);

const documentsList = document.getElementById('documentsList');
const summary = document.getElementById('documentSummary');
const uploadCard = document.getElementById('uploadCard');
const toggleUpload = document.getElementById('toggleUpload');

async function loadDocuments() {
  try {
    const documents = await apiGet('/student/documents');
    if (summary) summary.textContent = `${documents.length} document${documents.length === 1 ? '' : 's'} on file`;

    if (!documentsList) return;
    documentsList.innerHTML = documents.length ? documents.map(document => `
      <div class="document-item">
        <div class="document-icon">📄</div>
        <div class="document-info">
          <p class="document-name">${escapeHtml(document.name)}</p>
          <p class="document-date">Uploaded: ${escapeHtml((document.uploadedAt || '').slice(0, 10))}</p>
          <p class="document-status">${escapeHtml(document.status || 'PENDING')}</p>
        </div>
        <div style="display:flex; gap:8px;">
          <button class="btn-secondary small" type="button" data-document-id="${document.id}" data-action="download">📥</button>
          <button class="btn-secondary small" type="button" data-document-id="${document.id}" data-action="delete">🗑</button>
        </div>
      </div>
    `).join('') : '<p class="document-empty">No documents uploaded yet.</p>';

    documentsList.querySelectorAll('[data-action]').forEach(button => {
      button.addEventListener('click', async () => {
        const id = Number(button.dataset.documentId);
        const action = button.dataset.action;
        if (!id) return;
        try {
          if (action === 'download') {
            const response = await apiFetch(`/student/documents/${id}/download`);
            await downloadResponse(response, `document-${id}`);
          } else {
            if (!confirm('Delete this document?')) return;
            await apiFetch(`/student/documents/${id}`, { method: 'DELETE' });
            await loadDocuments();
          }
        } catch (err) {
          alert(err.message || 'Document action failed.');
        }
      });
    });
  } catch (err) {
    console.error('Failed to load documents', err);
    if (documentsList) documentsList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load documents.')}</p>`;
    if (summary) summary.textContent = 'Documents unavailable.';
  }
}

toggleUpload?.addEventListener('click', () => {
  if (!uploadCard) return;
  uploadCard.hidden = !uploadCard.hidden;
});

document.getElementById('uploadForm')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  let file = form.get('file');
  const name = String(form.get('name') || '').trim();
  try {
    file = validateUploadFile(file, ['pdf', 'jpg', 'jpeg', 'png', 'gif']);
    if (!name) throw new Error('Enter a name for this document.');
  } catch (err) {
    alert(err.message);
    return;
  }
  try {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('file', file);
    await apiFetch('/student/documents', { method: 'POST', body: formData });
    event.target.reset();
    if (uploadCard) uploadCard.hidden = true;
    await loadDocuments();
  } catch (err) {
    alert(err.message || 'Document upload failed.');
  }
});

document.addEventListener('DOMContentLoaded', () => {
  loadDocuments();
});
