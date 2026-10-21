/* MIT Student Portal - login flow.
 * Wired to the real backend: POST /api/auth/login, real BCrypt-checked
 * credentials, with HttpOnly cookies used for subsequent requests.
 *
 * The optional OTP prompt is shown only when the backend challenges an
 * enrolled account. The original fake CAPTCHA and hardcoded OTP were removed.
 */

// API, ROLE_KEY, USERNAME_KEY, setSession(), clearSession() come from auth.js,
// loaded before this file - see that file for why the token itself never
// touches this script at all.

// ===== PASSWORD VISIBILITY =====
function togglePasswordVisibility() {
  const passwordInput = document.getElementById('password');
  const btn = document.querySelector('.toggle-password');

  if (passwordInput.type === 'password') {
    passwordInput.type = 'text';
    btn.textContent = '🙈';
  } else {
    passwordInput.type = 'password';
    btn.textContent = '👁️';
  }
}

// ===== REAL LOGIN =====
async function handleLogin(event) {
  event.preventDefault();

  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;
  if (username.length < 3) {
    showAlert('Please enter your student ID or email', 'Input Required');
    return;
  }
  if (!password) {
    showAlert('Please enter your password', 'Password Required');
    return;
  }

  const submitBtn = document.getElementById('loginSubmitBtn');
  if (submitBtn.disabled) return;
  submitBtn.disabled = true;
  submitBtn.textContent = 'Signing in…';

  try {
    const otp = document.getElementById('otp').value.trim();
    const res = await fetch(`${API}/auth/login`, {
      method: 'POST',
      credentials: 'include', // required to receive+store the HttpOnly auth cookies
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, ...(otp ? { otp } : {}) }),
    });

    if (!res.ok) {
      const errorBody = await res.clone().json().catch(() => ({}));
      if (errorBody.mfaRequired) {
        const otpGroup = document.getElementById('otpGroup');
        otpGroup.hidden = false;
        document.getElementById('otp').focus();
        showAlert(errorBody.message || 'Enter your authenticator code.', 'MFA Required');
        if (errorBody.message === 'MFA verification required') {
          submitBtn.disabled = false;
          submitBtn.innerHTML = 'SIGN IN <span class="arrow">›</span>';
          return;
        }
      }
      throw new Error(await getResponseErrorMessage(
        res,
        'Incorrect student ID/email or password. If the portal is unavailable, refresh and try again shortly.',
      ));
    }

    if (!isJsonResponse(res)) {
      throw new Error('The portal returned an unexpected response. Refresh the page and try again.');
    }
    const data = await res.json(); // { role, username } - never the raw token

    if (data.role !== 'STUDENT') {
      // This entry point is the student portal. Staff accounts have their own
      // login (admin-login.html) - the role comes back from the server, never
      // from anything the client claimed, so this check can't be bypassed by
      // picking a different button. The session cookies are already set at this
      // point (login succeeded), so also sign them out to avoid leaving a
      // half-logged-in cookie sitting around for the wrong portal.
      await logout('index.html');
      showAlert('This is the student portal. Staff should sign in at the admin portal.', 'Wrong Portal');
      submitBtn.disabled = false;
      submitBtn.innerHTML = 'SIGN IN <span class="arrow">›</span>';
      return;
    }

    setSession(data.role, data.username, data.passwordChangeRequired);

    if (data.passwordChangeRequired) {
      window.location.href = 'force-password-change.html';
      return;
    }

    showAlert(`Welcome back! Redirecting to your dashboard...`, 'Login Successful');
    setTimeout(() => { window.location.href = 'dashboard.html'; }, 900);
  } catch (err) {
    const message = err instanceof TypeError
      ? 'The portal service could not be reached. Refresh the page and try again shortly.'
      : (err.message || 'Sign-in failed. Please try again.');
    showAlert(message, 'Login Failed');
    submitBtn.disabled = false;
    submitBtn.innerHTML = 'SIGN IN <span class="arrow">›</span>';
  }
}

// ===== ALERTS =====
function showAlert(message, title = 'Error') {
  const alert = document.getElementById('alert');
  document.getElementById('alertTitle').textContent = title;
  document.getElementById('alertText').textContent = message;
  alert.classList.add('visible');
}

function dismissAlert() {
  document.getElementById('alert').classList.remove('visible');
}

async function requestPasswordReset(event) {
  event.preventDefault();
  const email = prompt('Enter the email address on your account:');
  if (!email) return;
  try {
    const response = await fetch(`${API}/auth/forgot-password`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) {
      throw new Error(await getResponseErrorMessage(response, 'Password reset request failed. Please try again shortly.'));
    }
    showAlert('If that email is registered, a password reset link has been sent.', 'Reset Requested');
  } catch (error) {
    showAlert(error instanceof TypeError
      ? 'The portal service could not be reached. Please try again shortly.'
      : (error.message || 'Password reset request failed.'), 'Reset Failed');
  }
}

// ===== INITIALIZATION =====
document.addEventListener('DOMContentLoaded', function () {
  // Already signed in? Skip straight to the dashboard. This is a UX shortcut
  // only (see auth.js) - if the underlying cookie is actually gone or expired,
  // the dashboard's own apiFetch calls will redirect back here regardless.
  if (getRole() === 'STUDENT') {
    window.location.href = 'dashboard.html';
    return;
  }
  document.getElementById('username').focus();
});
