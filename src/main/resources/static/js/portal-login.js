const loginForm = document.getElementById('portalLoginForm');
const loginMessage = document.getElementById('loginMessage');
const loginButton = document.getElementById('loginSubmitButton');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const passwordToggle = document.getElementById('passwordToggle');
const otpGroup = document.getElementById('otpGroup');
const otpInput = document.getElementById('otp');
const resetToggle = document.getElementById('resetToggle');
const resetForm = document.getElementById('passwordResetForm');
const resetEmail = document.getElementById('resetEmail');
const resetButton = document.getElementById('resetSubmitButton');

const portalPages = {
  STUDENT: '/dashboard.html',
  TEACHER: '/teacher-dashboard.html',
  ADMIN: '/admin-dashboard.html',
};

function showLoginMessage(message, isError = false) {
  loginMessage.textContent = message;
  loginMessage.classList.toggle('is-error', isError);
  loginMessage.hidden = false;
}

function parseJsonResponse(response) {
  if (!/(?:application\/json|[+]json)\b/i.test(response.headers.get('content-type') || '')) {
    return Promise.resolve(null);
  }
  return response.json().catch(() => null);
}

function isSupportedRole(role) {
  return Object.prototype.hasOwnProperty.call(portalPages, role);
}

async function handlePortalLogin(event) {
  event.preventDefault();
  if (loginButton.disabled) return;

  const username = usernameInput.value.trim();
  const password = passwordInput.value;
  if (!username || !password) {
    showLoginMessage('Enter your username or email and password to continue.', true);
    return;
  }

  loginMessage.hidden = true;
  loginButton.disabled = true;
  loginButton.textContent = 'Signing in…';
  try {
    const response = await fetch(`${API}/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, ...(otpInput.value.trim() ? { otp: otpInput.value.trim() } : {}) }),
    });
    const data = await parseJsonResponse(response);

    if (!response.ok && data?.mfaEnrollmentRequired === true) {
      if (!isSupportedRole(data.role) || typeof data.username !== 'string') {
        await logout('/portal-login.html');
        return;
      }
      setSession(data.role, data.username, data.passwordChangeRequired);
      setMfaEnrollmentRequired(true);
      window.location.assign(data.passwordChangeRequired ? '/force-password-change.html' : '/account-security.html');
      return;
    }

    if (!response.ok && data?.mfaRequired === true) {
      otpGroup.hidden = false;
      otpInput.required = true;
      otpInput.focus();
      showLoginMessage('Enter the current code from your authenticator app, then sign in again.', true);
      return;
    }

    if (!response.ok) {
      showLoginMessage('We could not sign you in with those details. Check them and try again.', true);
      return;
    }
    if (!data || !isSupportedRole(data.role) || typeof data.username !== 'string') {
      await logout('/portal-login.html');
      return;
    }

    setSession(data.role, data.username, data.passwordChangeRequired);
    showLoginMessage('Sign-in successful. Opening your portal.');
    window.location.assign(data.passwordChangeRequired ? '/force-password-change.html' : portalPages[data.role]);
  } catch (error) {
    showLoginMessage(error instanceof TypeError
      ? 'The portal service could not be reached. Check your connection and try again.'
      : 'Sign-in could not be completed. Please try again.', true);
  } finally {
    loginButton.disabled = false;
    loginButton.textContent = 'Sign in';
  }
}

async function handlePasswordReset(event) {
  event.preventDefault();
  if (resetButton.disabled) return;

  resetButton.disabled = true;
  resetButton.textContent = 'Sending request…';
  try {
    const response = await fetch(`${API}/auth/forgot-password`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: resetEmail.value.trim() }),
    });
    if (!response.ok) throw new Error('Password reset request failed.');
    showLoginMessage('If an account matches that email, reset instructions will be sent.');
    resetForm.reset();
  } catch (error) {
    showLoginMessage(error instanceof TypeError
      ? 'The portal service could not be reached. Please try again.'
      : 'The reset request could not be submitted. Please try again.', true);
  } finally {
    resetButton.disabled = false;
    resetButton.textContent = 'Request a reset link';
  }
}

loginForm.addEventListener('submit', handlePortalLogin);
passwordToggle.addEventListener('click', () => {
  const isVisible = passwordInput.type === 'text';
  passwordInput.type = isVisible ? 'password' : 'text';
  passwordToggle.textContent = isVisible ? 'Show' : 'Hide';
  passwordToggle.setAttribute('aria-label', isVisible ? 'Show password' : 'Hide password');
  passwordToggle.setAttribute('aria-pressed', String(!isVisible));
});
resetToggle.addEventListener('click', () => {
  const isExpanded = resetToggle.getAttribute('aria-expanded') === 'true';
  resetToggle.setAttribute('aria-expanded', String(!isExpanded));
  resetForm.hidden = isExpanded;
  if (!isExpanded) resetEmail.focus();
});
resetForm.addEventListener('submit', handlePasswordReset);
usernameInput.focus();
document.querySelector('[data-current-year-number]').textContent = String(new Date().getFullYear());
