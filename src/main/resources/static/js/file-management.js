document.addEventListener('DOMContentLoaded', async () => {
  try {
    const authorities = await apiGet('/auth/authorities');
    const isAuthorized = Array.isArray(authorities)
      && authorities.some(authority => authority === 'ADMIN' || authority === 'PERM_PORTAL_SETTINGS_MANAGE');
    if (!isAuthorized) {
      window.location.href = 'admin-login.html';
      return;
    }
  } catch (error) {
    window.location.href = 'admin-login.html';
    return;
  }

  document.getElementById('fileLookupForm').addEventListener('submit', inspectFile);
  document.getElementById('fileSearchForm').addEventListener('submit', event => event.preventDefault());
  document.getElementById('fileSearch').addEventListener('input', renderFileList);
  document.getElementById('refreshFiles').addEventListener('click', loadFiles);
  document.getElementById('fileList').addEventListener('click', handleFileAction);
  loadFiles();
});

let selectedFilePath = '';
let storedFiles = [];

async function loadFiles() {
  const status = document.getElementById('fileListStatus');
  status.textContent = 'Loading stored files…';
  try {
    const result = await apiGet('/files/admin/list');
    if (!result || !Array.isArray(result.files) || typeof result.truncated !== 'boolean') {
      throw new Error('The server returned an invalid stored-file listing.');
    }
    storedFiles = result.files;
    renderFileList();
    status.textContent = `${storedFiles.length} stored file(s) loaded.${result.truncated ? ' Showing the first 500; narrow the search or clean up files to find older entries.' : ''}`;
  } catch (error) {
    storedFiles = [];
    renderFileList();
    status.textContent = error.message || 'Unable to load stored files.';
  }
}

function renderFileList() {
  const search = document.getElementById('fileSearch').value.trim().toLowerCase();
  const matches = storedFiles.filter(file => String(file.path || '').toLowerCase().includes(search));
  const body = document.getElementById('fileList');
  body.innerHTML = matches.length ? matches.map(file => `
    <tr>
      <td class="file-path">${escapeHtml(file.path)}</td>
      <td>${Number(file.sizeBytes).toLocaleString()} bytes</td>
      <td>${escapeHtml(file.lastModified ? new Date(file.lastModified).toLocaleString() : 'Unknown')}</td>
      <td class="file-actions">
        <button class="btn-secondary" type="button" data-file-action="inspect" data-file-path="${escapeHtml(file.path)}">Inspect</button>
        <button class="btn-secondary" type="button" data-file-action="download" data-file-path="${escapeHtml(file.path)}">Download</button>
        <button class="btn-small delete" type="button" data-file-action="delete" data-file-path="${escapeHtml(file.path)}">Delete</button>
      </td>
    </tr>
  `).join('') : `<tr><td colspan="4">${storedFiles.length ? 'No files match this search.' : 'No stored files found.'}</td></tr>`;
}

async function handleFileAction(event) {
  const button = event.target.closest('button[data-file-action]');
  if (!button) return;
  const filePath = button.dataset.filePath;
  if (button.dataset.fileAction === 'inspect') {
    document.getElementById('filePath').value = filePath;
    await inspectFile({ preventDefault() {} });
  } else if (button.dataset.fileAction === 'download') {
    await downloadFile(filePath);
  } else if (button.dataset.fileAction === 'delete') {
    await deleteFile(filePath);
    await loadFiles();
  }
}

async function inspectFile(event) {
  event.preventDefault();
  selectedFilePath = document.getElementById('filePath').value.trim();
  if (!selectedFilePath) return;
  try {
    const query = `?filePath=${encodeURIComponent(selectedFilePath)}`;
    const [exists, size] = await Promise.all([
      apiGet(`/files/exists${query}`),
      apiGet(`/files/info${query}`),
    ]);
    document.getElementById('fileExists').textContent = exists ? 'Yes' : 'No';
    document.getElementById('fileSize').textContent = `${Number(size).toLocaleString()} bytes`;
    document.getElementById('fileDetails').hidden = false;
    document.getElementById('fileStatus').textContent = 'File details loaded.';
    document.getElementById('downloadFile').onclick = () => downloadFile(selectedFilePath);
    document.getElementById('deleteFile').onclick = () => deleteFile(selectedFilePath);
  } catch (error) {
    document.getElementById('fileDetails').hidden = true;
    document.getElementById('fileStatus').textContent = error.message || 'Unable to inspect file.';
  }
}

async function downloadFile(filePath) {
  try {
    const response = await apiFetch(`/files/download?filePath=${encodeURIComponent(filePath)}`);
    await downloadResponse(response, filePath.split(/[\\/]/).pop() || 'download');
    document.getElementById('fileStatus').textContent = `Download started for ${filePath}.`;
  } catch (error) { document.getElementById('fileStatus').textContent = error.message || 'Unable to download file.'; }
}

async function deleteFile(filePath) {
  if (!confirm(`Delete ${filePath}?`)) return;
  try {
    await apiFetch(`/files/delete?filePath=${encodeURIComponent(filePath)}`, { method: 'DELETE' });
    document.getElementById('fileStatus').textContent = `File deleted: ${filePath}.`;
    document.getElementById('fileDetails').hidden = true;
  } catch (error) { document.getElementById('fileStatus').textContent = error.message || 'Unable to delete file.'; }
}