const CACHE_NAME = 'mit-portal-cache-v30';
const CORE_ASSETS = [
  './',
  './index.html',
  './admissions.html',
  './admin-login.html',
  './account-security.html',
  './course-catalog.html',
  './privacy.html',
  './reset-password.html',
  './support.html',
  './terms.html',
  './unauthorized.html',
  './verify-email.html',
  './css/style.css',
  './css/auth.css',
  './css/dashboard-style.css',
  './css/modules-style.css',
  './css/admin-style.css',
  './css/account-security.css',
  './css/course-catalog.css',
  './css/operations-style.css',
  './icons/background.webp',
  './icons/campus-login.png',
  './icons/logo.jpg',
  './icons/pwa-180.png',
  './icons/pwa-192.png',
  './icons/pwa-512.png',
  './manifest.json',
  './js/auth.js',
  './js/account-security.js',
  './js/course-catalog.js',
  './js/admin-script.js',
  './js/admissions.js',
  './js/announcements.js',
  './js/backend-only.js',
  './js/dashboard-script.js',
  './js/deployment-config.js',
  './js/documents.js',
  './js/file-management.js',
  './js/force-password-change.js',
  './js/hostel.js',
  './js/library.js',
  './js/messages.js',
  './js/modules-script.js',
  './js/notifications.js',
  './js/operations.js',
  './js/profile-security.js',
  './js/research.js',
  './js/reset-password.js',
  './js/scholarships.js',
  './js/theme.js',
  './js/script.js',
  './js/support-tickets.js',
  './js/teacher-script.js',
  './js/verify-email.js',
  './public/index.html',
  './public/about.html',
  './public/admissions.html',
  './public/contact.html',
  './public/programmes.html',
  './public/css/site.css',
  './public/images/campus-community.jpg',
  './public/js/site.js'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(CORE_ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
  )).then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
  const requestUrl = new URL(event.request.url);
  if (event.request.method !== 'GET' ||
      requestUrl.origin !== self.location.origin ||
      requestUrl.pathname === '/api' ||
      requestUrl.pathname.startsWith('/api/') ||
      requestUrl.pathname === '/actuator' ||
      requestUrl.pathname.startsWith('/actuator/')) return;
  event.respondWith(
    (async () => {
      const cached = await caches.match(event.request, {
        ignoreSearch: event.request.mode === 'navigate',
      });
      try {
        const response = await fetch(event.request);
        if (cached && response.ok) {
          const cache = await caches.open(CACHE_NAME);
          const cacheUrl = new URL(event.request.url);
          if (event.request.mode === 'navigate') {
            cacheUrl.search = '';
            cacheUrl.hash = '';
          }
          await cache.put(cacheUrl.toString(), response.clone());
        }
        return response;
      } catch (error) {
        if (cached) return cached;
        throw error;
      }
    })()
  );
});
