    const params = new URLSearchParams(window.location.search);
    const login = document.getElementById('loginLink');
    const message = document.getElementById('accessMessage');
    if (params.get('reason') === 'role') {
      message.textContent = 'Your account is signed in, but its role does not allow this page.';
      login.textContent = 'Return to portal';
      login.href = 'admin-login.html';
    }
