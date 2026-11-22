const CACHE_NAME = 'voidtv-remote-v3';
const SHELL_ASSETS = [
  '/',
  '/index.html',
  '/remote.css',
  '/remote.js',
  '/manifest.webmanifest',
  '/icon.svg',
  '/icon-192.png',
  '/icon-512.png'
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(SHELL_ASSETS);
    })
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Dynamic API calls are always live and never cached
  if (
    url.pathname.startsWith('/api') ||
    url.pathname === '/state' ||
    url.pathname === '/key' ||
    url.pathname === '/text' ||
    url.pathname === '/apps' ||
    url.pathname === '/mouse' ||
    url.pathname === '/volume' ||
    url.pathname === '/tap' ||
    url.pathname === '/launch' ||
    url.pathname === '/open' ||
    url.pathname === '/inputs' ||
    url.pathname === '/input' ||
    url.pathname === '/eval' ||
    url.pathname === '/ping' ||
    url.pathname === '/ip' ||
    url.pathname === '/browser/config' ||
    url.pathname === '/browser/clear'
  ) {
    event.respondWith(fetch(event.request));
    return;
  }

  // Shell assets: Network-First with Cache Fallback for instant updates
  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseToCache);
          });
        }
        return networkResponse;
      })
      .catch(() => caches.match(event.request))
  );
});
