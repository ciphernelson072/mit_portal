(() => {
  if (!('serviceWorker' in navigator) || window.__mitPortalPwaUpdatesStarted) return;
  window.__mitPortalPwaUpdatesStarted = true;

  const isInstalled = window.matchMedia('(display-mode: standalone)').matches
    || window.navigator.standalone === true;
  let updateNotice;
  let dismissed = false;
  let applyingUpdate = false;

  function showUpdateNotice(registration) {
    if (!isInstalled || dismissed || !registration.waiting || !navigator.serviceWorker.controller) return;
    if (updateNotice) return;

    const notice = document.createElement('aside');
    notice.setAttribute('role', 'status');
    notice.setAttribute('aria-live', 'polite');
    Object.assign(notice.style, {
      position: 'fixed',
      zIndex: '10000',
      left: '16px',
      right: '16px',
      bottom: 'calc(16px + env(safe-area-inset-bottom))',
      maxWidth: '520px',
      margin: '0 auto',
      padding: '16px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: '12px',
      flexWrap: 'wrap',
      color: '#fff',
      background: '#173b68',
      borderRadius: '12px',
      boxShadow: '0 8px 28px rgba(0, 0, 0, .28)',
      font: '500 15px/1.4 system-ui, sans-serif',
    });

    const message = document.createElement('span');
    message.textContent = 'A new version of the MIT Portal is ready.';

    const actions = document.createElement('span');
    Object.assign(actions.style, { display: 'flex', gap: '8px', marginLeft: 'auto' });

    const updateButton = document.createElement('button');
    updateButton.type = 'button';
    updateButton.textContent = 'Update';
    Object.assign(updateButton.style, {
      border: '0',
      borderRadius: '8px',
      padding: '9px 16px',
      minHeight: '44px',
      color: '#173b68',
      background: '#fff',
      font: '700 14px system-ui, sans-serif',
      cursor: 'pointer',
    });
    updateButton.addEventListener('click', () => {
      const waitingWorker = registration.waiting;
      if (!waitingWorker) return;
      applyingUpdate = true;
      updateButton.disabled = true;
      updateButton.textContent = 'Updating…';
      waitingWorker.postMessage({ type: 'SKIP_WAITING' });
    });

    const laterButton = document.createElement('button');
    laterButton.type = 'button';
    laterButton.textContent = 'Later';
    laterButton.setAttribute('aria-label', 'Dismiss update notice');
    Object.assign(laterButton.style, {
      border: '1px solid rgba(255,255,255,.7)',
      borderRadius: '8px',
      padding: '9px 12px',
      minHeight: '44px',
      color: '#fff',
      background: 'transparent',
      font: '600 14px system-ui, sans-serif',
      cursor: 'pointer',
    });
    laterButton.addEventListener('click', () => {
      dismissed = true;
      notice.remove();
      updateNotice = undefined;
    });

    actions.append(updateButton, laterButton);
    notice.append(message, actions);
    document.body.appendChild(notice);
    updateNotice = notice;
  }

  async function registerAndCheckForUpdates() {
    try {
      const registration = await navigator.serviceWorker.register('/service-worker.js', {
        updateViaCache: 'none',
      });

      const showIfInstalled = () => showUpdateNotice(registration);
      const checkForUpdates = () => registration.update()
        .then(showIfInstalled)
        .catch(error => console.error('Unable to check for MIT Portal updates.', error));
      const watchInstallingWorker = () => {
        const installingWorker = registration.installing;
        if (!installingWorker) return;
        installingWorker.addEventListener('statechange', () => {
          if (installingWorker.state === 'installed') showIfInstalled();
        });
      };

      showIfInstalled();
      registration.addEventListener('updatefound', () => {
        dismissed = false;
        watchInstallingWorker();
      });
      watchInstallingWorker();

      navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (applyingUpdate) window.location.reload();
      });
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') checkForUpdates();
      });

      await checkForUpdates();
    } catch (error) {
      console.error('Unable to check for MIT Portal updates.', error);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', registerAndCheckForUpdates, { once: true });
  } else {
    registerAndCheckForUpdates();
  }
})();
