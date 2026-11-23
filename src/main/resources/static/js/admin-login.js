  // Real authentication against POST /api/auth/login - the previous version
  // accepted a hardcoded email/password pair client-side and let the caller pick
  // their own role from four buttons with zero server verification. The role
  // buttons are gone entirely now, not just hidden: keeping decorative buttons
  // that don't affect the real login would just be different, subtler deception.
  // The role that matters comes back from the server in the login response,
  // exactly like the student login (js/script.js) and exactly like the fix
  // applied to POST /api/auth/register earlier in this project.

  let alertTimer;

  function showAlert(message, type = 'error') {
    const alert = document.getElementById('alert');
    document.getElementById('alertText').textContent = message;
    alert.dataset.type = type;
    alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
    alert.setAttribute('aria-live', type === 'error' ? 'assertive' : 'polite');
    alert.hidden = false;
    alert.classList.add('show');
    clearTimeout(alertTimer);
    alertTimer = setTimeout(() => {
      alert.classList.remove('show');
      alert.hidden = true;
    }, 5000);
  }

  async function handleAdminLogin(event) {
    event.preventDefault();

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const submitBtn = document.querySelector('.btn-login');

    if (!username || !password) {
      showAlert('Please fill in all fields');
      return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = 'Signing in…';

    try {
      const otp = document.getElementById('otp').value.trim();
      const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, ...(otp ? { otp } : {}) }),
      });

      if (!res.ok) {
        const errorBody = await res.clone().json().catch(() => ({}));
        if (errorBody.mfaEnrollmentRequired) {
          if (errorBody.role !== 'ADMIN' && errorBody.role !== 'TEACHER') {
            await logout('admin-login.html');
            showAlert('This is the staff portal. Students should sign in at the student portal.');
            return;
          }
          setSession(errorBody.role, errorBody.username, errorBody.passwordChangeRequired);
          setMfaEnrollmentRequired(true);
          window.location.href = errorBody.passwordChangeRequired
            ? 'force-password-change.html'
            : 'account-security.html';
          return;
        }
        if (errorBody.mfaRequired) {
          document.getElementById('otpGroup').hidden = false;
          document.getElementById('otp').focus();
          showAlert(errorBody.message || 'Enter your authenticator code.');
          if (errorBody.message === 'MFA verification required') return;
        }
        throw new Error(await getResponseErrorMessage(
          res,
          'Invalid username or password. If the portal is unavailable, refresh and try again shortly.',
        ));
      }

      if (!isJsonResponse(res)) {
        throw new Error('The portal returned an unexpected response. Refresh the page and try again.');
      }
      const data = await res.json(); // { role, username } - never the raw token

      if (data.role !== 'ADMIN' && data.role !== 'TEACHER') {
        // This is the staff portal. A student account authenticated fine but
        // doesn't belong here - the role came back from the server, so this
        // can't be bypassed by anything the client sends.
        await logout('admin-login.html');
        showAlert('This is the staff portal. Students should sign in at the student portal.');
        return;
      }

      setSession(data.role, data.username, data.passwordChangeRequired);

      if (data.passwordChangeRequired) {
        window.location.href = 'force-password-change.html';
        return;
      }

      if (data.role === 'ADMIN') {
        window.location.href = 'admin-dashboard.html';
      } else {
        window.location.href = 'teacher-dashboard.html';
      }
    } catch (err) {
      showAlert(err instanceof TypeError
        ? 'The portal service could not be reached. Refresh the page and try again shortly.'
        : (err.message || 'Sign-in failed. Please try again.'));
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Sign In';
    }
  }

  async function requestAdminPasswordReset(event) {
    event.preventDefault();
    const email = prompt('Enter the email address on your account:');
    if (!email) return;
    try {
      const response = await fetch(`${API}/auth/forgot-password`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
      });
      if (!response.ok) {
        throw new Error(await getResponseErrorMessage(response, 'Password reset request failed. Please try again shortly.'));
      }
      showAlert('If that email is registered, a reset link has been sent.', 'success');
    } catch (error) {
      showAlert(error instanceof TypeError
        ? 'The portal service could not be reached. Please try again shortly.'
        : (error.message || 'Password reset request failed.'));
    }
  }

  // Do not redirect based only on the localStorage role hint. It can outlive
  // the authentication cookies and otherwise send users away from this form.
  window.addEventListener('load', function() {
    document.getElementById('username').focus();
  });
