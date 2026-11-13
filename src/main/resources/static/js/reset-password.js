const resetToken = window.passwordResetToken;
delete window.passwordResetToken;
const resetStatus = document.getElementById('resetStatus');
const resetForm = document.getElementById('resetPasswordForm');
const resendForm = document.getElementById('resendResetForm');

function setResetStatus(message, isError = false) {
  resetStatus.textContent = message;
  resetStatus.style.color = isError ? 'var(--danger, #b42318)' : '';
}

async function validateResetToken() {
  if (!resetToken) {
    setResetStatus('This reset link is missing its token.', true);
    resetForm.querySelector('button').disabled = true;
    resendForm.hidden = false;
    return;
  }
  try {
    const response = await fetch(`${API}/auth/validate-reset-token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: resetToken }),
    });
    const valid = response.ok && await response.json();
    if (!valid) throw new Error('This reset link is invalid or expired.');
  } catch (error) {
    setResetStatus(error.message, true);
    resetForm.querySelector('button').disabled = true;
    resendForm.hidden = false;
  }
}

resetForm.addEventListener('submit', async event => {
  event.preventDefault();
  const newPassword = document.getElementById('newPassword').value;
  const confirmPassword = document.getElementById('confirmPassword').value;
  if (newPassword !== confirmPassword) {
    setResetStatus('The passwords do not match.', true);
    return;
  }
  const button = document.getElementById('resetButton');
  button.disabled = true;
  try {
    const response = await fetch(`${API}/auth/reset-password`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: resetToken, newPassword }),
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Password reset failed. Please try again shortly.'));
    }
    setResetStatus('Password reset successfully. Redirecting to sign in...');
    setTimeout(() => { window.location.href = 'index.html'; }, 1200);
  } catch (error) {
    setResetStatus(error.message || 'Password reset failed.', true);
    button.disabled = false;
  }
});

validateResetToken();

resendForm.addEventListener('submit', async event => {
  event.preventDefault();
  const email = document.getElementById('resetEmail').value.trim();
  if (!email) return;
  try {
    const response = await fetch(`${API}/auth/resend-password-reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Unable to resend reset email. Please try again shortly.'));
    }
    setResetStatus('If that account exists, a new reset email has been sent.');
  } catch (error) { setResetStatus(error.message || 'Unable to resend reset email.', true); }
});
