(() => {
  if (document.body.dataset.accountSecurity === 'true'
      && !requireRole(['ADMIN', 'TEACHER', 'STUDENT'], 'index.html')) return;

  const status = document.getElementById('mfaStatus');
  if (!status) return;

  const mfaSection = document.getElementById('mfaSecuritySection');
  const sessionsSection = document.getElementById('activeSessionsSection');
  const passwordGroup = document.getElementById('mfaPasswordGroup');
  const password = document.getElementById('mfaCurrentPassword');
  const otp = document.getElementById('mfaOtp');
  const otpGroup = document.getElementById('mfaOtpGroup');
  const enrollment = document.getElementById('mfaEnrollment');
  const secret = document.getElementById('mfaSecret');
  const otpUri = document.getElementById('mfaOtpUri');
  const message = document.getElementById('mfaMessage');
  const setupButton = document.getElementById('mfaSetupButton');
  const verifyButton = document.getElementById('mfaVerifyButton');
  const disableButton = document.getElementById('mfaDisableButton');
  const sessionsTableBody = document.getElementById('sessionsTableBody');
  const revokeAllSessionsButton = document.getElementById('revokeAllSessionsButton');
  const enrollmentOnly = isMfaEnrollmentRequired();
  let enabled = false;

  if (enrollmentOnly && sessionsSection) sessionsSection.hidden = true;

  function showMessage(text, error = false) {
    message.textContent = text;
    message.setAttribute('aria-live', error ? 'assertive' : 'polite');
  }

  async function loadMfaStatus() {
    try {
      const response = await apiFetch('/auth/mfa/status');
      enabled = await response.json();
      status.textContent = enabled
        ? 'Two-factor authentication is enabled.'
        : (enrollmentOnly
          ? 'MFA enrollment is required before you can access the portal.'
          : 'Two-factor authentication is not enabled.');
      setupButton.hidden = enabled;
      disableButton.hidden = !enabled;
    } catch (error) {
      status.textContent = error.message.includes('(501)')
        ? 'Two-factor authentication is not enabled for this deployment.'
        : 'Unable to load two-factor authentication status.';
      if (error.message.includes('(501)')) {
        if (mfaSection) mfaSection.hidden = true;
        passwordGroup.hidden = true;
      }
      showMessage(error.message || 'Please try again later.', true);
    }
  }

  function renderSessions(sessions) {
    sessionsTableBody.replaceChildren();
    if (!Array.isArray(sessions) || sessions.length === 0) {
      const row = sessionsTableBody.insertRow();
      const cell = row.insertCell();
      cell.colSpan = 4;
      cell.textContent = 'No active refresh sessions.';
      return;
    }

    sessions.forEach(session => {
      const row = sessionsTableBody.insertRow();
      row.insertCell().textContent = `Session ${session.id}`;
      const expiresAt = new Date(session.expiresAt);
      row.insertCell().textContent = Number.isNaN(expiresAt.getTime())
        ? 'Expiry unavailable'
        : expiresAt.toLocaleString();
      row.insertCell().textContent = session.active ? 'Refresh active' : 'Revoked';

      const actionCell = row.insertCell();
      const revokeButton = document.createElement('button');
      revokeButton.type = 'button';
      revokeButton.className = 'btn-secondary';
      revokeButton.textContent = 'Revoke refresh';
      revokeButton.addEventListener('click', async () => {
        revokeButton.disabled = true;
        try {
          await apiFetch(`/auth/sessions/${encodeURIComponent(session.id)}/revoke`, { method: 'POST' });
          await loadSessions();
        } catch (error) {
          showMessage(error.message || 'Could not revoke this session.', true);
          revokeButton.disabled = false;
        }
      });
      actionCell.appendChild(revokeButton);
    });
  }

  async function loadSessions() {
    try {
      const response = await apiFetch('/auth/sessions');
      renderSessions(await response.json());
    } catch (error) {
      sessionsTableBody.replaceChildren();
      const row = sessionsTableBody.insertRow();
      const cell = row.insertCell();
      cell.colSpan = 4;
      cell.textContent = `Unable to load sessions: ${error.message}`;
    }
  }

  setupButton.addEventListener('click', async () => {
    if (!password.value) {
      showMessage('Enter your current password before setting up MFA.', true);
      password.focus();
      return;
    }
    setupButton.disabled = true;
    try {
      const response = await apiFetch('/auth/mfa/setup', {
        method: 'POST',
        body: JSON.stringify({ currentPassword: password.value }),
      });
      const setup = await response.json();
      if (!/^[A-Z2-7]{32}$/.test(setup.secret) || !setup.otpUri.startsWith('otpauth://')) {
        throw new Error('The server returned invalid authenticator setup data.');
      }
      secret.textContent = setup.secret;
      otpUri.href = setup.otpUri;
      otpUri.hidden = false;
      enrollment.hidden = false;
      otpGroup.hidden = false;
      setupButton.hidden = true;
      verifyButton.hidden = false;
      otp.focus();
      showMessage('Scan or enter the secret, then enter a current code to finish setup.');
    } catch (error) {
      showMessage(error.message || 'Could not set up an authenticator.', true);
    } finally {
      setupButton.disabled = false;
    }
  });

  verifyButton.addEventListener('click', async () => {
    if (!password.value || !/^[0-9]{6}$/.test(otp.value.trim())) {
      showMessage('Enter your current password and the six-digit authenticator code.', true);
      return;
    }
    verifyButton.disabled = true;
    try {
      await apiFetch('/auth/mfa/verify', {
        method: 'POST',
        body: JSON.stringify({ currentPassword: password.value, otp: otp.value.trim() }),
      });
      showMessage('Two-factor authentication enabled. Sign in again to continue.');
      await logout();
    } catch (error) {
      showMessage(error.message || 'The authenticator code could not be verified.', true);
    } finally {
      verifyButton.disabled = false;
    }
  });

  disableButton.addEventListener('click', async () => {
    if (!password.value || !/^[0-9]{6}$/.test(otp.value.trim())) {
      showMessage('Enter your current password and the six-digit authenticator code.', true);
      return;
    }
    disableButton.disabled = true;
    try {
      await apiFetch('/auth/mfa/disable', {
        method: 'POST',
        body: JSON.stringify({ currentPassword: password.value, otp: otp.value.trim() }),
      });
      showMessage('Two-factor authentication disabled. Sign in again to continue.');
      await logout();
    } catch (error) {
      showMessage(error.message || 'Two-factor authentication could not be disabled.', true);
    } finally {
      disableButton.disabled = false;
    }
  });

  revokeAllSessionsButton.addEventListener('click', async () => {
    if (!window.confirm('Sign out every active session, including this one? You will need to sign in again.')) return;
    revokeAllSessionsButton.disabled = true;
    try {
      await apiFetch('/auth/sessions/revoke-all', { method: 'POST' });
      await logout();
    } catch (error) {
      showMessage(error.message || 'Could not revoke sessions.', true);
      revokeAllSessionsButton.disabled = false;
    }
  });

  loadMfaStatus();
  if (!enrollmentOnly) loadSessions();
})();
