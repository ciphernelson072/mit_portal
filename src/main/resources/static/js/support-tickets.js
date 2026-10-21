requireRole(['STUDENT']);

const ticketsList = document.getElementById('ticketsList');
const ticketSummary = document.getElementById('ticketSummary');
const ticketForm = document.getElementById('ticketForm');

async function loadTickets() {
  try {
    const requests = await apiGet('/student/requests');
    if (ticketSummary) {
      ticketSummary.textContent = requests.length
        ? `${requests.length} active request${requests.length === 1 ? '' : 's'} in your help desk history.`
        : 'No help desk requests yet. Submit your first ticket below.';
    }

    if (!ticketsList) return;
    ticketsList.innerHTML = requests.length
      ? requests.map(request => `
        <article class="ticket-item">
          <div class="notification-top-row">
            <div>
              <div class="notification-title">${escapeHtml(request.subject)}</div>
              <div class="notification-meta">${escapeHtml(request.type)} • ${escapeHtml((request.createdAt || '').slice(0, 10))}</div>
            </div>
            <span class="status-badge">${escapeHtml(request.status || 'OPEN')}</span>
          </div>
          <div class="notification-message">${escapeHtml(request.details || 'No details provided.')}</div>
        </article>
      `).join('')
      : '<p class="document-empty">No support tickets have been submitted yet.</p>';
  } catch (err) {
    console.error('Failed to load support tickets', err);
    if (ticketSummary) ticketSummary.textContent = 'Support tickets are unavailable right now.';
    if (ticketsList) ticketsList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load your requests.')}</p>`;
  }
}

if (ticketForm) {
  ticketForm.addEventListener('submit', async (event) => {
    event.preventDefault();

    const formData = new FormData(ticketForm);
    const type = String(formData.get('type') || 'SUPPORT_TICKET');
    const subject = String(formData.get('subject') || '').trim();
    const details = String(formData.get('details') || '').trim();

    if (!subject) {
      alert('Please add a short subject before submitting.');
      return;
    }

    try {
      await apiFetch('/student/requests', {
        method: 'POST',
        body: JSON.stringify({ type, subject, details }),
      });
      ticketForm.reset();
      await loadTickets();
    } catch (err) {
      alert(err.message || 'Your request could not be submitted.');
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  loadTickets();
});
