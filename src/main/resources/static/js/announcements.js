requireRole(['STUDENT']);

const announcementList = document.getElementById('announcementList');
const announcementSummary = document.getElementById('announcementSummary');

async function loadAnnouncements() {
  try {
    const announcements = await apiGet('/announcements');
    if (announcementSummary) {
      announcementSummary.textContent = announcements.length
        ? `${announcements.length} announcement${announcements.length === 1 ? '' : 's'} posted.`
        : 'No announcements have been published yet.';
    }

    if (!announcementList) return;
    announcementList.innerHTML = announcements.length
      ? announcements.map(item => `
        <article class="announcement-item">
          <div class="notification-top-row">
            <div>
              <div class="notification-title">${escapeHtml(item.title)}</div>
              <div class="notification-meta">${escapeHtml(item.authorName || 'Portal team')} • ${escapeHtml((item.createdAt || '').slice(0, 10))}</div>
            </div>
          </div>
          <div class="notification-message">${escapeHtml(item.content || '')}</div>
        </article>
      `).join('')
      : '<p class="document-empty">No announcements are available right now.</p>';
  } catch (err) {
    console.error('Failed to load announcements', err);
    if (announcementSummary) announcementSummary.textContent = 'Announcements are unavailable right now.';
    if (announcementList) announcementList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load announcements.')}</p>`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadAnnouncements();
});
