// Keeps the page itself available offline. Pages are fetched from the network first, so a new
// version shows up as soon as it is online; fonts and icons come from the cache first.
const CACHE = 'jelly-share-v2';
const SHELL = [
  './',
  'index.html',
  'app.js',
  'box.js',
  'store.js',
  'config.js',
  'style.css',
  'vendor/firebase.js',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'fonts/bagel-fat-one.woff2',
  'fonts/pretendard/pretendardvariable-dynamic-subset.css',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== location.origin) return;
  const stable = /\.(woff2|png)$/.test(url.pathname);
  event.respondWith(stable ? cacheFirst(request) : networkFirst(request));
});

async function cacheFirst(request) {
  const hit = await caches.match(request);
  if (hit) return hit;
  const response = await fetch(request);
  if (response.ok) (await caches.open(CACHE)).put(request, response.clone());
  return response;
}

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) (await caches.open(CACHE)).put(request, response.clone());
    return response;
  } catch (e) {
    const hit = await caches.match(request, { ignoreSearch: true });
    if (hit) return hit;
    throw e;
  }
}
