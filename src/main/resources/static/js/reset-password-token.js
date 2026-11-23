    const resetLinkParams = new URLSearchParams(window.location.hash.slice(1));
    const legacyResetParams = new URLSearchParams(window.location.search);
    window.passwordResetToken = resetLinkParams.get('token') || legacyResetParams.get('token');
    if (window.location.search || window.location.hash) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }
