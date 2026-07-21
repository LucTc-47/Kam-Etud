# Stockage de session frontend

Ce fichier documente le mode de stockage des tokens JWT du frontend Kam'Etud.

## Probleme traite

Avant ce changement, les tokens etaient toujours stockes dans `localStorage`.
Dans un meme navigateur, `localStorage` est partage entre tous les onglets du
meme domaine, par exemple `http://localhost:5173`.

Consequence en developpement :

- onglet 1 connecte en client ;
- onglet 2 connecte en admin ;
- la connexion admin remplace la session client ;
- les deux onglets finissent par utiliser le meme compte.

## Solution ajoutee

Le fichier `src/lib/api.ts` choisit maintenant le stockage des tokens selon la
variable `VITE_AUTH_STORAGE`.

Par defaut, le frontend garde le comportement historique :

```text
localStorage
```

En developpement, on peut activer un mode multi-session par onglet :

```env
VITE_AUTH_STORAGE=session
```

Dans ce mode, les tokens sont stockes dans `sessionStorage`.
Chaque onglet possede alors sa propre session.

## Comment tester plusieurs comptes

Dans le fichier `.env` local du frontend, ajouter :

```env
VITE_AUTH_STORAGE=session
```

Puis redemarrer Vite :

```powershell
npm run dev
```

Vous pouvez ensuite ouvrir plusieurs onglets dans le meme navigateur :

- onglet 1 : client ;
- onglet 2 : etudiant ;
- onglet 3 : admin ou moderateur.

Les sessions ne s'ecrasent plus entre onglets.

## Comportement en production

La variable n'est prise en compte que pendant le developpement Vite.
Sans configuration speciale, le stockage reste en `localStorage`.

Pour une vraie production sensible, la meilleure evolution serait de migrer les
tokens vers des cookies `HttpOnly`, `Secure` et `SameSite`, afin d'eviter que le
JavaScript du navigateur puisse lire les tokens.
