/* Shared theme controller for student, teacher, and administrator workspaces. */
(function () {
  const storageKey = 'theme';

  function systemTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    const resolved = theme === 'system' ? systemTheme() : theme;
    document.documentElement.dataset.theme = resolved;
    document.body.classList.toggle('dark-mode', resolved === 'dark');
    document.querySelectorAll('[data-theme-choice]').forEach(control => {
      control.setAttribute('aria-pressed', String(control.dataset.themeChoice === theme));
    });
    const button = document.querySelector('[data-theme-toggle]');
    if (button) {
      button.textContent = resolved === 'dark' ? '☀️' : '🌙';
      button.setAttribute('aria-label', `Theme: ${theme}`);
      button.title = `Theme: ${theme}`;
    }
  }

  function setSharedTheme(theme) {
    const value = ['light', 'dark', 'system'].includes(theme) ? theme : 'light';
    localStorage.setItem(storageKey, value);
    applyTheme(value);
    const dropdown = document.getElementById('themeBtn')?.closest('.dropdown');
    dropdown?.classList.remove('active');
    dropdown?.querySelector('button')?.setAttribute('aria-expanded', 'false');
  }

  function initTheme() {
    const saved = localStorage.getItem(storageKey) || 'system';
    applyTheme(saved);
    document.querySelectorAll('[data-theme-choice]').forEach(control => {
      control.addEventListener('click', () => setSharedTheme(control.dataset.themeChoice));
    });
    const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    media?.addEventListener?.('change', () => {
      if ((localStorage.getItem(storageKey) || 'system') === 'system') applyTheme('system');
    });
    if (document.body.classList.contains('teacher-dashboard')) {
      document.getElementById('themeBtn')?.addEventListener('click', event => {
        const button = event.currentTarget;
        const dropdown = button.closest('.dropdown');
        const opening = !dropdown.classList.contains('active');
        document.querySelectorAll('.header-actions .dropdown.active').forEach(item => {
          item.classList.remove('active');
          item.querySelector('button')?.setAttribute('aria-expanded', 'false');
        });
        dropdown.classList.toggle('active', opening);
        button.setAttribute('aria-expanded', String(opening));
      });
      document.addEventListener('click', event => {
        if (event.target.closest('.header-actions .dropdown')) return;
        document.querySelectorAll('.header-actions .dropdown.active').forEach(dropdown => {
          dropdown.classList.remove('active');
          dropdown.querySelector('button')?.setAttribute('aria-expanded', 'false');
        });
      });
      document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        document.querySelectorAll('.header-actions .dropdown.active').forEach(dropdown => {
          dropdown.classList.remove('active');
          dropdown.querySelector('button')?.setAttribute('aria-expanded', 'false');
        });
      });
    }
  }

  window.setSharedTheme = setSharedTheme;
  window.addEventListener('DOMContentLoaded', initTheme);
})();
