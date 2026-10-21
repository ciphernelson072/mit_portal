requireRole(['STUDENT', 'TEACHER', 'ADMIN']);

const dashboardLink = document.querySelector('.header-actions a');
if (dashboardLink) {
  const role = String(getRole() || '').toUpperCase();
  dashboardLink.href = role === 'ADMIN'
    ? 'admin-dashboard.html'
    : role === 'TEACHER' ? 'teacher-dashboard.html' : 'dashboard.html';
}

const inboxList = document.getElementById('inboxList');
const sentList = document.getElementById('sentList');
const summary = document.getElementById('messageSummary');
const composeCard = document.getElementById('composeCard');
const composeToggle = document.getElementById('composeToggle');
const emailCampaignCard = document.getElementById('adminEmailCampaign');
let emailRecipients = [];
const selectedEmailRecipientIds = new Set();

async function loadEmailRecipients() {
  const list = document.getElementById('emailRecipientList');
  const summary = document.getElementById('emailRecipientSummary');
  if (!list || !summary) return;

  try {
    emailRecipients = await apiGet('/admin/email/recipients');
    selectedEmailRecipientIds.clear();
    renderEmailRecipients();
  } catch (err) {
    summary.textContent = err.message || 'Unable to load email recipients.';
    list.textContent = '';
    setEmailCampaignStatus(err.message || 'Unable to load email recipients.', true);
  }
}

function getFilteredEmailRecipients() {
  const search = String(document.getElementById('emailRecipientSearch')?.value || '').trim().toLowerCase();
  const role = String(document.getElementById('emailRecipientRole')?.value || '').toUpperCase();
  return emailRecipients.filter(recipient => {
    const matchesRole = !role || String(recipient.role || '').toUpperCase() === role;
    const matchesSearch = !search
      || String(recipient.username || '').toLowerCase().includes(search)
      || String(recipient.email || '').toLowerCase().includes(search);
    return matchesRole && matchesSearch;
  });
}

function renderEmailRecipients() {
  const list = document.getElementById('emailRecipientList');
  const summary = document.getElementById('emailRecipientSummary');
  if (!list || !summary) return;

  const shownRecipients = getFilteredEmailRecipients();
  summary.textContent = `${selectedEmailRecipientIds.size} selected · ${emailRecipients.length} eligible accounts · showing ${shownRecipients.length}`;
  list.innerHTML = shownRecipients.length ? shownRecipients.map(recipient => `
    <label class="email-recipient">
      <input type="checkbox" data-email-recipient-id="${Number(recipient.id)}"
        ${selectedEmailRecipientIds.has(Number(recipient.id)) ? 'checked' : ''}>
      <span class="email-recipient-identity">
        <strong>${escapeHtml(recipient.username)}</strong>
        <span>${escapeHtml(recipient.email)}</span>
      </span>
      <span class="email-recipient-role">${escapeHtml(recipient.role)}</span>
    </label>
  `).join('') : '<p class="message-empty">No eligible accounts match this filter.</p>';
}

function setEmailCampaignStatus(message, isError = false) {
  const status = document.getElementById('emailCampaignStatus');
  if (!status) return;
  status.textContent = message;
  status.classList.toggle('error', isError);
  status.classList.toggle('success', Boolean(message) && !isError);
}

async function loadMessages() {
  try {
    const [received, sent] = await Promise.all([
      apiGet('/messages/received'),
      apiGet('/messages/sent'),
    ]);

    if (summary) summary.textContent = `${received.length} unread in inbox · ${sent.length} sent`;

    if (inboxList) {
      inboxList.innerHTML = received.length ? received.map(message => `
        <div class="message-item ${message.read ? '' : 'unread'}" data-message-id="${message.id}">
          <span class="message-avatar">👤</span>
          <span class="message-content">
            <strong class="message-from">${escapeHtml(message.senderUsername)}</strong>
            <span class="message-preview"><strong>${escapeHtml(message.subject)}</strong> — ${escapeHtml(message.content)}</span>
            <span class="notification-time">${escapeHtml((message.sentAt || '').slice(0, 10))} · ${message.read ? 'Read' : 'Unread'}</span>
          </span>
        </div>
      `).join('') : '<p class="message-empty">No messages in your inbox.</p>';

      inboxList.querySelectorAll('.message-item').forEach(item => {
        item.addEventListener('click', async () => {
          const id = Number(item.dataset.messageId);
          if (!id || item.classList.contains('read')) return;
          try {
            await apiFetch(`/messages/${id}/read`, { method: 'POST' });
            item.classList.remove('unread');
            const meta = item.querySelector('.notification-time');
            if (meta) meta.textContent = `${meta.textContent.replace(/·\s*Unread$/, '')} · Read`;
            await loadMessages();
          } catch (err) {
            console.error('Failed to mark message read', err);
          }
        });
      });
    }

    if (sentList) {
      sentList.innerHTML = sent.length ? sent.map(message => `
        <div class="message-item" data-sent-message-id="${message.id}">
          <span class="message-avatar">📤</span>
          <span class="message-content">
            <strong class="message-from">To: ${escapeHtml(message.receiverUsername || 'Unknown')}</strong>
            <span class="message-preview"><strong>${escapeHtml(message.subject)}</strong> — ${escapeHtml(message.content)}</span>
            <span class="notification-time">${escapeHtml((message.sentAt || '').slice(0, 10))}</span>
          </span>
        </div>
      `).join('') : '<p class="message-empty">No sent messages yet.</p>';
    }
  } catch (err) {
    console.error('Failed to load messages', err);
    if (inboxList) inboxList.innerHTML = `<p class="message-empty">${escapeHtml(err.message || 'Unable to load messages.')}</p>`;
    if (sentList) sentList.innerHTML = '<p class="message-empty">Unable to load sent messages.</p>';
    if (summary) summary.textContent = 'Messages unavailable.';
  }
}

composeToggle?.addEventListener('click', () => {
  if (!composeCard) return;
  composeCard.hidden = !composeCard.hidden;
});

if (emailCampaignCard) {
  apiGet('/auth/authorities').then(authorities => {
    if (!Array.isArray(authorities) || authorities.some(authority => typeof authority !== 'string')) {
      throw new Error('The server returned an invalid authorities response.');
    }
    if (authorities.includes('ADMIN') || authorities.includes('PERM_EMAIL_CAMPAIGN_MANAGE')) {
      emailCampaignCard.hidden = false;
      loadEmailRecipients();
    }
  }).catch(err => {
    console.error('Unable to verify email campaign access', err);
  });
}

document.getElementById('emailRecipientSearch')?.addEventListener('input', renderEmailRecipients);
document.getElementById('emailRecipientRole')?.addEventListener('change', renderEmailRecipients);

document.getElementById('emailRecipientList')?.addEventListener('change', event => {
  const checkbox = event.target.closest('input[data-email-recipient-id]');
  if (!checkbox) return;
  const id = Number(checkbox.dataset.emailRecipientId);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  if (checkbox.checked) selectedEmailRecipientIds.add(id);
  else selectedEmailRecipientIds.delete(id);
  renderEmailRecipients();
});

document.getElementById('selectFilteredRecipients')?.addEventListener('click', () => {
  getFilteredEmailRecipients().forEach(recipient => selectedEmailRecipientIds.add(Number(recipient.id)));
  renderEmailRecipients();
});

document.getElementById('clearEmailRecipients')?.addEventListener('click', () => {
  selectedEmailRecipientIds.clear();
  renderEmailRecipients();
});

document.getElementById('emailCampaignForm')?.addEventListener('submit', async event => {
  event.preventDefault();
  const recipientIds = Array.from(selectedEmailRecipientIds);
  const formElement = event.currentTarget;
  if (!recipientIds.length) {
    setEmailCampaignStatus('Select at least one recipient.', true);
    return;
  }
  if (!confirm(`Send this email individually to ${recipientIds.length} selected account(s)?`)) return;

  const submitButton = document.getElementById('sendEmailCampaign');
  const form = new FormData(formElement);
  submitButton.disabled = true;
  setEmailCampaignStatus('Sending emails…');
  try {
    const response = await apiFetch('/admin/email/campaign', {
      method: 'POST',
      body: JSON.stringify({
        recipientIds,
        subject: String(form.get('subject') || '').trim(),
        content: String(form.get('content') || '').trim(),
      }),
    });
    const result = await response.json();
    const failedNames = Array.isArray(result.failedUsernames) ? result.failedUsernames : [];
    if (result.failedCount > 0) {
      setEmailCampaignStatus(
        `Sent ${result.sentCount} of ${result.recipientCount}. Failed: ${failedNames.join(', ')}.`,
        result.sentCount === 0
      );
    } else {
      setEmailCampaignStatus(`Email sent successfully to ${result.sentCount} recipient(s).`);
      formElement.reset();
      selectedEmailRecipientIds.clear();
      renderEmailRecipients();
    }
  } catch (err) {
    setEmailCampaignStatus(err.message || 'Email campaign failed.', true);
  } finally {
    submitButton.disabled = false;
  }
});

document.getElementById('composeForm')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  try {
    await apiFetch('/messages', {
      method: 'POST',
      body: JSON.stringify({
        receiverUsername: String(form.get('receiverUsername') || '').trim(),
        subject: String(form.get('subject') || '').trim(),
        content: String(form.get('content') || '').trim(),
      }),
    });
    event.target.reset();
    composeCard.hidden = true;
    await loadMessages();
  } catch (err) {
    alert(err.message || 'Message delivery failed.');
  }
});

document.addEventListener('DOMContentLoaded', () => {
  loadMessages();
});
