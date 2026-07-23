// Version du cache. L'incrementer purge l'ancien cache chez tous les visiteurs :
// l'evenement "activate" supprime tout cache dont le nom differe.
// Passage en v2 : l'ancien cache contenait des reponses d'API, notamment un
// /api/profiles/me vide servi indefiniment.
const CACHE_NAME = "kam-etud-cache-v2";
const ASSETS_TO_CACHE = [
  "/",
  "/index.html",
  "/manifest.json",
  "/favicon.ico",
  "/favicon-128.png",
  "/favicon-180.png",
  "/favicon-192.png",
  "/favicon-256.png",
  "/logoFull.png"
];

// Install Event - Pre-cache core assets
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log("[Service Worker] Pre-caching offline shell");
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
  );
});

// Activate Event - Clean up old caches
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cache) => {
          if (cache !== CACHE_NAME) {
            console.log("[Service Worker] Clearing old cache", cache);
            return caches.delete(cache);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch Event - Serve with Stale-While-Revalidate and handle SPA routing
self.addEventListener("fetch", (event) => {
  // Handle HTTP/HTTPS requests only (ignore chrome-extension, etc.)
  if (!event.request.url.startsWith(self.location.origin)) {
    return;
  }

  // Ne jamais intercepter les appels a l'API ni les requetes qui modifient
  // des donnees.
  //
  // Sans ce garde-fou, la strategie stale-while-revalidate ci-dessous
  // s'appliquait aussi aux reponses de l'API : une reponse vide de
  // /api/profiles/me restait servie indefiniment depuis le cache, et les
  // requetes POST etaient relayees par le service worker au lieu de partir
  // directement au reseau, ce qui faisait echouer la creation d'une prestation.
  //
  // Un « return » sans respondWith laisse le navigateur traiter la requete
  // normalement, sans interception.
  const url = new URL(event.request.url);
  if (event.request.method !== "GET"
      || url.pathname.startsWith("/api/")
      || url.pathname.startsWith("/ws/")) {
    return;
  }

  // Handle SPA routing: if navigating to another page, return index.html when offline
  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request).catch(() => {
        return caches.match("/index.html") || Response.error();
      })
    );
    return;
  }

  // Stale-While-Revalidate Strategy
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      const fetchPromise = fetch(event.request)
        .then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200 && networkResponse.type === "basic") {
            const responseToCache = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseToCache);
            });
          }
          return networkResponse;
        })
        .catch((err) => {
          console.warn("[Service Worker] Background fetch failed for:", event.request.url, err);
          // respondWith n'accepte qu'une Response : sans ce retour, la promesse
          // se resolvait avec undefined des qu'une ressource absente du cache
          // echouait au reseau, ce qui produisait dans la console
          // « TypeError: Failed to convert value to 'Response' ».
          // On renvoie le cache s'il existe, sinon une erreur reseau explicite
          // que la page peut traiter normalement.
          return cachedResponse || Response.error();
        });

      return cachedResponse || fetchPromise;
    })
  );
});
