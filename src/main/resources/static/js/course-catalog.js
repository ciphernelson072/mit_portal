(() => {
  const backLink = document.getElementById('catalogBackLink');
  const role = typeof getRole === 'function' ? getRole() : null;
  if (backLink && role) backLink.href = getPortalPageForRole(role);
  document.getElementById('catalogThemeToggle').addEventListener('click', () => {
    const currentTheme = document.documentElement.dataset.theme;
    setSharedTheme(currentTheme === 'dark' ? 'light' : 'dark');
  });

  const status = document.getElementById('courseCatalogStatus');
  const grid = document.getElementById('courseCatalogGrid');
  const count = document.getElementById('courseCatalogCount');
  const search = document.getElementById('courseCatalogSearch');
  let courses = [];

  function renderCourses() {
    const query = search.value.trim().toLocaleLowerCase();
    const filtered = courses.filter(course => [
      course.courseCode,
      course.name,
      course.category,
      course.teacherFullName,
      course.semester,
    ].some(value => String(value || '').toLocaleLowerCase().includes(query)));

    count.textContent = query
      ? `Showing ${filtered.length} of ${courses.length} courses`
      : `${courses.length} ${courses.length === 1 ? 'course' : 'courses'}`;
    grid.innerHTML = filtered.length ? filtered.map(course => `
      <article class="catalog-course-card">
        <h3>${escapeHtml(course.courseCode || 'Course')} · ${escapeHtml(course.name || 'Untitled course')}</h3>
        <p><strong>Category:</strong> ${escapeHtml(course.category || 'Not specified')}</p>
        <p><strong>Instructor:</strong> ${escapeHtml(course.teacherFullName || 'Not assigned')}</p>
        <p><strong>Semester:</strong> ${escapeHtml(course.semester || 'Not specified')}</p>
        <p><strong>Credits:</strong> ${escapeHtml(course.credits ?? 'Not specified')}</p>
      </article>`).join('') : `<p class="catalog-status">${query ? 'No courses match your search.' : 'No courses are currently available.'}</p>`;
  }

  search.addEventListener('input', renderCourses);
  apiGet('/public/courses').then(result => {
    if (!Array.isArray(result)) throw new Error('The course catalogue response was not valid.');
    courses = result;
    status.hidden = true;
    renderCourses();
  }).catch(error => {
    status.textContent = `Courses could not be loaded. ${error.message || 'Please try again later.'}`;
  });
})();
