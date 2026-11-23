    const verificationLinkParams = new URLSearchParams(window.location.hash.slice(1));
    const legacyVerificationParams = new URLSearchParams(window.location.search);
    window.emailVerificationToken = verificationLinkParams.get('token')
      || legacyVerificationParams.get('token')
      || legacyVerificationParams.get('code');
    if (window.location.search || window.location.hash) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }
