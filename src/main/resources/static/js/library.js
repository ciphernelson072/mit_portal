requireRole(['STUDENT']);

const librarySummary = document.getElementById('librarySummary');
const loanList = document.getElementById('loanList');
const catalogList = document.getElementById('catalogList');

async function loadLibrary() {
  try {
    const [catalog, loans] = await Promise.all([
      apiGet('/library/catalog'),
      apiGet('/library/loans/mine'),
    ]);

    if (librarySummary) {
      const loanCount = loans.length || 0;
      librarySummary.textContent = `${catalog.length} titles in the catalogue • ${loanCount} active loan${loanCount === 1 ? '' : 's'}`;
    }

    if (loanList) {
      loanList.innerHTML = loans.length
        ? loans.map(loan => {
            const item = catalog.find(book => Number(book.id) === Number(loan.libraryItemId));
            const title = item ? item.title : `Item #${loan.libraryItemId}`;
            return `
              <article class="loan-item">
                <div class="notification-top-row">
                  <div>
                    <div class="notification-title">${escapeHtml(title)}</div>
                    <div class="notification-meta">Due ${escapeHtml((loan.dueDate || '').toString())}</div>
                  </div>
                  <span class="status-badge">${escapeHtml(loan.status || 'BORROWED')}</span>
                </div>
              </article>
            `;
          }).join('')
        : '<p class="document-empty">No active library loans.</p>';
    }

    if (catalogList) {
      catalogList.innerHTML = catalog.length
        ? catalog.map(item => `
          <article class="catalog-item">
            <div class="notification-top-row">
              <div>
                <div class="notification-title">${escapeHtml(item.title)}</div>
                <div class="notification-meta">${escapeHtml(item.author)} • ISBN ${escapeHtml(item.isbn)}</div>
              </div>
              <span class="status-badge">${Number(item.availableCopies || 0)} available</span>
            </div>
          </article>
        `).join('')
        : '<p class="document-empty">No library items are currently available.</p>';
    }
  } catch (err) {
    console.error('Failed to load library data', err);
    if (librarySummary) librarySummary.textContent = 'Library data is unavailable right now.';
    if (loanList) loanList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load loans.')}</p>`;
    if (catalogList) catalogList.innerHTML = `<p class="document-empty">${escapeHtml(err.message || 'Unable to load the catalogue.')}</p>`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadLibrary();
});
