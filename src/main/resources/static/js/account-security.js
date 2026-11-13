(() => {
  if (!requireRole(['ADMIN', 'TEACHER', 'STUDENT'], 'index.html')) return;
  const dashboardLink = document.getElementById('dashboardLink');
  if (dashboardLink) dashboardLink.href = getPortalPageForRole(getRole());
})();
