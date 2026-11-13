/* Clear demo markup before page scripts render API-backed data. */
(function () {
  const page = window.location.pathname.split('/').pop();
  const selectors = {
    'dashboard.html': ['.notification-list', '.message-list', '.course-item', '.deadline-item', '.announcement-item', '.payment-item', '.program-info p'],
    'academics.html': ['.courses-grid', '.timetable-container', '.assignments-list', '.grades-table tbody', '#transcriptTable tbody', '#missingCoursesTable tbody', '.exams-container', '.analytics-grid'],
    'attendance.html': ['.attendance-courses', '.attendance-table tbody', '.chart-placeholder', '.summary-card .summary-value', '.summary-card .summary-detail', '.summary-badge', '.alerts-container', '.table-pagination'],
    'finance.html': ['.fees-table tbody', '.pending-payments', '.invoices-list', '.receipts-table tbody', '.scholarships-container'],
    'campus-support.html': ['.events-container', '.clubs-container', '.hostel-container', '.routes-grid', '.tickets-table tbody', '#chatMessages'],
    'library.html': ['#catalogList', '#loanList'],
    'research.html': ['#projectList'],
    'profile.html': ['#documentsList', '.requests-table tbody'],
    'admin-dashboard.html': ['#dashboardModule .table tbody', '#coursesModule .courses-grid', '#studentsModule table tbody', '#staffModule table tbody', '#requestsModule table tbody', '#gradesModule table tbody', '#financeModule table tbody', '#dashboardModule .task-list'],
  };
  (selectors[page] || []).forEach(selector => document.querySelectorAll(selector).forEach(element => {
    if (element.matches('tbody, .courses-grid, .assignments-list, .exams-container, .events-container, .routes-grid, .notification-list, .message-list, .pending-payments, .invoices-list, .scholarships-container, #documentsList, .task-list')) element.replaceChildren();
    else element.replaceChildren();
  }));
  if (page === 'profile.html') {
    document.querySelectorAll('#personalTab input, #personalTab select').forEach(input => { input.value = ''; });
    document.querySelectorAll('#academicTab .info-value, #academicTab .academic-table td:nth-child(2), #personalTab .contact-item span:not(.contact-label)').forEach(element => { element.textContent = 'Loading...'; });
  }
  if (page === 'dashboard.html') {
    document.querySelectorAll('.profile-name, .profile-email').forEach(element => { element.textContent = 'Loading...'; });
  }
  if (page === 'admin-dashboard.html') document.querySelectorAll('#gradesModule .filter-select, #coursesModule .filter-select, #studentsModule .filter-select').forEach(select => select.replaceChildren());
  if (page === 'admin-login.html') document.querySelector('.info-box')?.remove();
})();
