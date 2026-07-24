import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./index.css";

createRoot(document.getElementById("root")!).render(<App />);

// Register Service Worker in production mode
if ("serviceWorker" in navigator && import.meta.env.PROD) {
  // Un onglet deja ouvert continue d'executer l'ancien service worker apres un
  // deploiement : le routage cote client ne provoque aucun rechargement
  // complet, donc le navigateur ne revient pas verifier /sw.js de lui-meme.
  // C'est ainsi qu'une ancienne version qui interceptait les appels /api/
  // pouvait continuer a fabriquer des reponses (ex. un 403 sur la creation de
  // prestation) longtemps apres que le correctif etait en ligne.
  //
  // On force donc une verification de mise a jour au chargement puis
  // periodiquement, et on recharge une seule fois lorsque le nouveau service
  // worker prend le controle de la page.

  // La page n'etait deja controlee par un SW que s'il y en avait un actif :
  // dans ce cas seulement, un changement de controleur signifie une mise a
  // jour (et non la toute premiere installation), et justifie un rechargement.
  const alreadyControlled = !!navigator.serviceWorker.controller;
  let reloading = false;
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (!alreadyControlled || reloading) return;
    reloading = true;
    window.location.reload();
  });

  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("/sw.js")
      .then((registration) => {
        // Verifie immediatement s'il existe une version plus recente du SW,
        // sans attendre une navigation complete.
        registration.update();
        // Onglets restes ouverts longtemps : nouvelle verification chaque heure.
        setInterval(() => registration.update(), 60 * 60 * 1000);
      })
      .catch((error) => {
        console.error("Service Worker registration failed: ", error);
      });
  });
}
