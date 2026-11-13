const verificationStatus = document.getElementById('verificationStatus');
const token = window.emailVerificationToken;
window.emailVerificationToken = null;

function setVerificationStatus(message, error = false) {
  verificationStatus.textContent = message;
  verificationStatus.style.color = error ? 'var(--danger, #b42318)' : '';
}

async function verifyEmail() {
  if (!token) {
    setVerificationStatus('Enter your email below to request a verification link.');
    return;
  }
  try {
    const validResponse = await fetch(`${API}/auth/validate-email-token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!validResponse.ok || !await validResponse.json()) throw new Error('This verification link is invalid or expired.');
    const response = await fetch(`${API}/auth/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Email verification failed. Please try again shortly.'));
    }
    setVerificationStatus('Email verified successfully. You can now sign in.');
  } catch (error) { setVerificationStatus(error.message, true); }
}

document.getElementById('resendVerificationForm').addEventListener('submit', async event => {
  event.preventDefault();
  const email = document.getElementById('verificationEmail').value.trim();
  try {
    const response = await fetch(`${API}/auth/resend-email-verification`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Unable to resend verification email. Please try again shortly.'));
    }
    setVerificationStatus('If the account exists, a verification email has been sent.');
  } catch (error) { setVerificationStatus(error.message, true); }
});

verifyEmail();
