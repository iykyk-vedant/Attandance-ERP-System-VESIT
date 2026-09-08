// OpenAttend Service Worker — offline-first app shell for classroom attendance marking.
//
// Strategy:
//   navigations  -> network-first, falling back to the cached shell so the PWA
//                   launches with zero connectivity (basement labs, dead Wi-Fi).
//   static assets-> stale-while-revalidate (instant paint, refreshed in background).
//   /api/**      -> always bypassed. Attendance data must never be served stale;
//                   offline reads come from IndexedDB, offline writes are queued
//                   by the page in the `pending_submissions` store.
const CACHE_NAME = 'openattend-shell-v2';
const SHELL_URL = '/index.html';

const ASSETS_TO_CACHE = [
  '/',
  SHELL_URL,
  '/manifest.json',
  '/assets/icon.png',
  '/assets/logo-light.png',
  '/assets/logo-dark.png',
  '/assets/banner-dark.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      // addAll() is atomic — one 404 asset would abort the whole install and
      // leave the app with no offline shell, so each entry is cached independently.
      .then((cache) => Promise.all(
        ASSETS_TO_CACHE.map((url) => cache.add(new Request(url, { cache: 'reload' })).catch(() => {}))
      ))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstShell(request));
    return;
  }

  event.respondWith(staleWhileRevalidate(request));
});

/** Serves the freshest shell online; falls back to the cached shell when the network is gone. */
async function networkFirstShell(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const networkResponse = await fetch(request);
    if (networkResponse && networkResponse.ok) {
      cache.put(SHELL_URL, networkResponse.clone());
    }
    return networkResponse;
  } catch (err) {
    return (await cache.match(request)) || (await cache.match(SHELL_URL)) || Response.error();
  }
}

/** Returns the cached copy immediately and refreshes it in the background. */
async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);

  const networkFetch = fetch(request)
    .then((response) => {
      // Opaque/partial responses are not safe to replay from cache.
      if (response && response.ok && response.type === 'basic') {
        cache.put(request, response.clone());
      }
      return response;
    })
    .catch(() => null);

  if (cached) return cached;

  const fresh = await networkFetch;
  return fresh || Response.error();
}

// Background Sync: when the browser regains connectivity it fires this even if the
// tab was backgrounded. The queue and the auth token live in the page, so the worker
// wakes any open client and lets it drain `pending_submissions`.
self.addEventListener('sync', (event) => {
  if (event.tag !== 'openattend-attendance-sync') return;
  event.waitUntil(notifyClientsToSync());
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

async function notifyClientsToSync() {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  clients.forEach((client) => client.postMessage({ type: 'SYNC_PENDING_ATTENDANCE' }));
}
