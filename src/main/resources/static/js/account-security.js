(() => {
  const enrollmentRequired = isMfaEnrollmentRequired();
  if (!enrollmentRequired && !requireRole(['ADMIN', 'TEACHER', 'STUDENT'], 'index.html')) return;
  const dashboardLink = document.getElementById('dashboardLink');
  if (dashboardLink) {
    if (enrollmentRequired) {
      dashboardLink.hidden = true;
    } else {
      dashboardLink.href = getPortalPageForRole(getRole());
    }
  }
})();
