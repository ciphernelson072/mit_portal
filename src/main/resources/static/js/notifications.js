requireRole(['STUDENT']);

const notificationList = document.getElementById('notificationsList');
const summary = document.getElementById('notificationSummary');
const markAllReadButton = document.getElementById('markAllRead');

async function loadNotifications() {
  try {
    const notifications = await apiGet('/notifications');
    if (!notificationList) return;
    if (!Array.isArray(notifications)) {
      throw new Error('The notifications response has an invalid format.');
    }

    if (!notifications.length) {
      notificationList.innerHTML = '<p class="notification-empty">You have no notifications yet.</p>';
      if (summary) summary.textContent = 'No updates yet.';
      return;
    }

    const unreadCount = notifications.filter(item => !item.read).length;
    if (summary) summary.textContent = `${unreadCount} unread of ${notifications.length}`;

    notificationList.innerHTML = notifications.map(item => `
      <button type="button" class="notification-item ${item.read ? 'read' : 'unread'}" data-notification-id="${item.id}" ${item.read ? 'disabled' : ''}>
        <span class="notification-title">${escapeHtml(item.title)}</span>
        <span class="notification-text">${escapeHtml(item.message)}</span>
        <span class="notification-time">${escapeHtml((item.createdAt || '').slice(0, 10))} · ${item.read ? 'Read' : 'Unread'}</span>
      </button>
    `).join('');

    notificationList.querySelectorAll('.notification-item').forEach(item => {
      item.addEventListener('click', async () => {
        const id = Number(item.dataset.notificationId);
        if (!id || item.disabled || !item.classList.contains('unread')) return;
        item.disabled = true;
        try {
          await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
          item.classList.replace('unread', 'read');
          const time = item.querySelector('.notification-time');
          if (time) time.textContent = `${time.textContent.replace(/·\s*Unread$/, '')} · Read`;
          await loadNotifications();
        } catch (err) {
          console.error('Failed to mark notification read', err);
          item.disabled = false;
        }
      });
    });
  } catch (err) {
    console.error('Failed to load notifications', err);
    if (notificationList) {
      notificationList.innerHTML = `<p class="notification-empty">${escapeHtml(err.message || 'Unable to load notifications.')}</p>`;
    }
    if (summary) summary.textContent = 'Notifications unavailable.';
  }
}

markAllReadButton?.addEventListener('click', async () => {
  try {
    await apiFetch('/notifications/read-all', { method: 'PATCH' });
    await loadNotifications();
  } catch (err) {
    alert(err.message || 'Unable to mark notifications as read.');
  }
});

document.addEventListener('DOMContentLoaded', () => {
  loadNotifications();
});
