// Wize AI Studio — Service Worker (PWA offline cache)
const CACHE_NAME = "jarvis-v1";

const STATIC_ASSETS = [
  "/",
  "/manifest.json",
  "/index.html",
  "/src/main.jsx",
  "/src/App.jsx",
  "/src/components/JarvisVisualizer.jsx",
  "/src/components/ChatPanel.jsx",
  "/src/components/MicButton.jsx",
  "/src/components/SettingsPanel.jsx",
  "/src/components/DeviceDashboard.jsx",
  "/src/services/api.js",
  "/src/services/stt.js",
  "/src/services/tts.js",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  // Network-first for API calls
  if (req.url.includes("/api/")) {
    event.respondWith(
      fetch(req).catch(() => new Response(JSON.stringify({ error: "offline" }), { status: 503, headers: { "content-type": "application/json" } }))
    );
    return;
  }
  // Cache-first for static assets
  event.respondWith(
    caches.match(req).then((cached) => cached || fetch(req).then((resp) => {
      const clone = resp.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put(req, clone));
      return resp;
    }))
  );
});