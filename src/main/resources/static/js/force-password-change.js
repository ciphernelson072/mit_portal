document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('passwordChangeForm');
  const message = document.getElementById('passwordChangeMessage');
  const button = document.getElementById('passwordChangeSubmit');

  if (!getRole() || !getPasswordChangeRequired()) {
    if (getRole() === 'STUDENT' && !getPasswordChangeRequired()) {
      window.location.href = 'dashboard.html';
      return;
    }
    window.location.href = 'index.html';
    return;
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const values = new FormData(form);
    const currentPassword = values.get('currentPassword');
    const newPassword = values.get('newPassword');
    const confirmPassword = values.get('confirmPassword');
    if (newPassword !== confirmPassword) {
      message.setAttribute('role', 'alert');
      message.textContent = 'The new password and confirmation do not match.';
      return;
    }
    if (newPassword === currentPassword) {
      message.setAttribute('role', 'alert');
      message.textContent = 'Choose a password different from the temporary password.';
      return;
    }

    button.disabled = true;
    message.setAttribute('role', 'status');
    message.textContent = 'Updating password…';
    try {
      await apiFetch('/auth/change-password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      localStorage.setItem('mitPasswordChangeRequired', 'false');
      message.textContent = 'Password changed. Please sign in with your new password.';
      await logout('index.html');
    } catch (error) {
      message.setAttribute('role', 'alert');
      message.textContent = error.message || 'Unable to change your password.';
      button.disabled = false;
    }
  });
});
