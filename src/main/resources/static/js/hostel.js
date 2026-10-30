requireRole(['STUDENT']);

const hostelList = document.getElementById('hostelList');
const hostelAllocationList = document.getElementById('hostelAllocationList');
const campusSummary = document.getElementById('campusSummary');
const transportPass = document.getElementById('transportPass');
const transportDetails = document.getElementById('transportDetails');
const hostelForm = document.getElementById('hostelForm');
const renewPassBtn = document.getElementById('renewPassBtn');

async function loadCampusServices() {
  try {
    const [hostels, pass, allocations] = await Promise.all([
      apiGet('/campus/student/hostel'),
      apiGet('/campus/student/transport-pass').catch(() => null),
      apiGet('/accommodation/allocations/mine').catch(error => ({ error })),
    ]);

    if (campusSummary) {
      campusSummary.textContent = hostels.length
        ? `${hostels.length} accommodation request${hostels.length === 1 ? '' : 's'} on file.`
        : 'No hostel requests yet. Submit one below to begin.';
    }

    if (transportPass) {
      if (pass) {
        transportPass.textContent = pass.status || 'ACTIVE';
        transportDetails.textContent = pass.validUntil ? `Valid until ${pass.validUntil}` : 'Transport pass is active.';
      } else {
        transportPass.textContent = 'NO PASS';
        transportDetails.textContent = 'No active transport pass is on file.';
      }
    }

    if (hostelList) {
      hostelList.innerHTML = hostels.length
        ? hostels.map(item => `
          <article class="hostel-item">
            <div class="notification-top-row">
              <div>
                <div class="notification-title">Request #${item.id}</div>
                <div class="notification-meta">${escapeHtml((item.createdAt || '').slice(0, 10))}</div>
              </div>
              <span class="status-badge">${escapeHtml(item.status || 'OPEN')}</span>
            </div>
            <div class="notification-message">${escapeHtml(item.details || 'No request details were provided.')}</div>
          </article>
        `).join('')
        : '<p class="document-empty">No accommodation applications have been submitted yet.</p>';
    }

    if (hostelAllocationList) {
      if (!Array.isArray(allocations)) {
        hostelAllocationList.textContent = allocations.error?.message || 'Unable to load your room allocation.';
      } else {
        hostelAllocationList.innerHTML = allocations.length
          ? allocations.map(item => `
            <article class="hostel-item">
              <div class="notification-top-row">
                <div>
                  <div class="notification-title">${escapeHtml(item.building || 'Building not available')} · ${escapeHtml(item.roomCode || 'Room not available')}</div>
                  <div class="notification-meta">Allocated ${(item.allocatedAt || '').replace('T', ' ').slice(0, 16)}</div>
                </div>
                <span class="status-badge">${escapeHtml(item.status || 'ACTIVE')}</span>
              </div>
            </article>
          `).join('')
          : '<p class="document-empty">No room has been allocated yet.</p>';
      }
    }
  } catch (err) {
    console.error('Failed to load campus services', err);
    if (campusSummary) campusSummary.textContent = 'Accommodation data is currently unavailable.';
    if (transportPass) {
      transportPass.textContent = 'UNAVAILABLE';
      transportDetails.textContent = 'Unable to load your transport pass details.';
    }
    if (hostelList) hostelList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load accommodation records.')}</p>`;
    if (hostelAllocationList) hostelAllocationList.textContent = 'Unable to load your room allocation.';
  }
}

if (hostelForm) {
  hostelForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const details = new FormData(hostelForm).get('details');
    if (!details || !String(details).trim()) {
      alert('Please describe your accommodation need before submitting.');
      return;
    }
    if (String(details).length > 2000) {
      alert('Accommodation details must be 2,000 characters or fewer.');
      return;
    }
    try {
      await apiFetch('/campus/student/hostel', {
        method: 'POST',
        body: JSON.stringify({ details: String(details).trim() }),
      });
      hostelForm.reset();
      await loadCampusServices();
    } catch (err) {
      alert(err.message || 'Accommodation request failed.');
    }
  });
}

if (renewPassBtn) {
  renewPassBtn.addEventListener('click', async () => {
    try {
      await apiFetch('/campus/student/transport-pass/renew', { method: 'POST' });
      await loadCampusServices();
    } catch (err) {
      alert(err.message || 'Transport pass renewal failed.');
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  loadCampusServices();
});
