# Difficultés rencontrées et solutions apportées - Kam'Étud

Ce document résume les principaux problèmes rencontrés pendant l'évolution de Kam'Étud et les solutions mises en place. Il est organisé par composant afin d'aider l'équipe à comprendre les choix techniques, les corrections réalisées et les points de vigilance restants.

## Vue d'ensemble

Kam'Étud est passé d'un frontend fortement couplé à d'anciens contrats de données vers une architecture microservices Spring Boot derrière une API Gateway. La difficulté principale a été de faire communiquer proprement plusieurs domaines séparés : identité, catalogue, demandes, commandes, paiements, support et interface React.

Solutions globales apportées :

- le frontend appelle uniquement l'API Gateway ;
- les microservices exposent des contrats REST dédiés ;
- les données sensibles sont déduites du JWT côté backend autant que possible ;
- les ports locaux ont été harmonisés ;
- les bases PostgreSQL sont séparées par service ;
- les scripts `start-local.ps1`, `start-backend.ps1` et `start-frontend.ps1` automatisent le démarrage local ;
- les tests Maven, TypeScript et build Vite sont utilisés pour valider les changements.

## API Gateway

Service : `Backend-Kametude/api-gateway`

### Difficultés rencontrées

- Le frontend devait avoir une seule porte d'entrée au lieu d'appeler directement chaque microservice.
- Les routes internes ne devaient pas être accessibles depuis le navigateur.
- Les rôles devaient être contrôlés avant d'atteindre les services métier.
- Les en-têtes utilisateur ne devaient pas pouvoir être falsifiés par le client.
- Les origines Vite locales pouvaient varier entre `localhost`, `127.0.0.1` et l'adresse réseau locale.

### Solutions apportées

- Mise en place de la Gateway comme point d'entrée unique sur le port `8080`.
- Routage des chemins `/api/auth`, `/api/gigs`, `/api/orders`, `/api/payments`, `/api/chat`, etc. vers les bons services.
- Validation du JWT et reconstruction des en-têtes de confiance `X-User-Id`, `X-User-Email` et `X-User-Role`.
- Blocage des routes contenant `/internal/`.
- Contrôle des rôles pour les routes client, étudiant, admin et modérateur.
- Configuration CORS adaptée au développement local.

### Points de vigilance

- En production, remplacer les origines CORS larges par l'URL exacte du frontend.
- Fournir un `JWT_SECRET` fort via variable d'environnement.
- Ne jamais exposer directement les ports internes des microservices.

## Identity Service

Service : `Backend-Kametude/identity-service`

### Difficultés rencontrées

- Il fallait remplacer l'ancien parcours d'authentification tout en gardant les champs attendus par le frontend.
- Les profils devaient rester compatibles avec les rôles `client`, `student`, `admin` et `moderator`.
- Le KYC étudiant devait être vérifiable par l'administration.
- Les services métier devaient pouvoir vérifier qu'un utilisateur est actif, vérifié ou banni.

### Solutions apportées

- Authentification centralisée avec émission de JWT.
- Gestion des profils avec les informations utilisées par React : nom, téléphone, ville, université, rôle et statut de vérification.
- Routes admin pour la vérification KYC et la gestion des comptes.
- Exposition d'informations de profil consommées par les autres microservices.
- Script de seed des comptes de démonstration pour créer rapidement admin, modérateur, étudiant et client.

### Points de vigilance

- Les mots de passe et secrets ne doivent jamais être versionnés.
- `JWT_SECRET` doit être identique entre Identity, Gateway et Support.
- Les valeurs par défaut locales doivent être remplacées en production.

## Catalog Service

Service : `Backend-Kametude/catalog-service`

### Difficultés rencontrées

- Les catégories et villes étaient historiquement côté frontend ou dans des données statiques.
- Le catalogue devait gérer les gigs, leurs paliers, leur publication et leur activation.
- Un étudiant ne devait publier un gig que s'il est vérifié et non banni.
- Les filtres PostgreSQL pouvaient échouer lorsque les paramètres étaient `null`.
- La note d'un service devait être synchronisée avec les avis provenant du parcours commande.

### Solutions apportées

- Création des tables et endpoints publics `/api/categories` et `/api/cities`.
- Seed initial de villes et catégories, avec ajout de `Dschang`.
- Gestion admin des catégories et villes.
- Gigs rattachés à l'étudiant connecté via le JWT et non à une valeur envoyée librement par React.
- Vérification du statut étudiant via Identity avant publication.
- Correction des requêtes de recherche pour éviter les erreurs PostgreSQL liées aux paramètres nuls.
- Remplacement des icônes monétaires `$` par l'icône `Coins` côté frontend sur les écrans concernés.

### Points de vigilance

- L'ajout libre de villes par tous les utilisateurs doit idéalement passer par un système de suggestion puis validation admin.
- Les commandes créées depuis une proposition n'ont pas toujours de `gigId`, donc elles ne peuvent pas toujours mettre à jour la note d'un gig catalogue.

## Request Service

Service : `Backend-Kametude/requestservice`

### Difficultés rencontrées

- Les demandes clients et propositions étudiantes devaient sortir de l'ancien flux frontend.
- L'acceptation d'une proposition devait créer une commande métier sans doublon.
- Le package Java historique était incohérent avec la nouvelle structure.
- Le port initial entrait en conflit avec `payment-service`.
- Les dates passées ne devaient plus être acceptées pour créer une demande.

### Solutions apportées

- Harmonisation du package actif sous `cm.kametud.requestservice`.
- Port configuré sur `8082`.
- Création des endpoints de demandes et propositions.
- Acceptation atomique d'une proposition : la proposition acceptée est conservée, les autres sont refusées.
- Création de commande via `business-service` après acceptation.
- Validation backend pour refuser une date antérieure à aujourd'hui.
- Tests d'intégration avec H2 pour valider le workflow principal.

### Points de vigilance

- Les dates doivent rester validées côté frontend et backend.
- Les appels à Business, Identity et Support doivent rester tolérants aux indisponibilités temporaires.

## Business Service

Service : `Backend-Kametude/business-service`

### Difficultés rencontrées

- Les commandes, missions, livrables, révisions, avis, litiges et abus étaient fortement liés à l'ancien modèle frontend.
- Le backend ne devait plus faire confiance aux montants, participants ou identifiants envoyés par React.
- Le client devait voir les livrables soumis et pouvoir les télécharger.
- Les notes devaient alimenter les statistiques étudiant et service.
- Les litiges devaient pouvoir être escaladés à l'administration.

### Solutions apportées

- Business Service est devenu la source de vérité pour les commandes.
- Les participants, montants et informations importantes sont récupérés depuis les services de confiance.
- Ajout du workflow mission : acceptation, livraison, révision, validation et finalisation.
- Gestion des livrables et de leur affichage côté client.
- Gestion des avis, statistiques, signalements, litiges et abus.
- Notifications interservices vers Support lors des événements importants.
- Auto-validation possible des commandes livrées selon les règles métier.

### Points de vigilance

- Les commandes issues de propositions doivent être enrichies avec un `gigId` si l'on veut synchroniser automatiquement la note du gig catalogue.
- Les règles de paiement et de libération des fonds doivent rester alignées avec Payment Service.

## Payment Service

Service : `Backend-Kametude/payment-service`

### Difficultés rencontrées

- Le frontend envoyait historiquement des informations sensibles comme le montant ou le vendeur.
- Le paiement devait gérer collecte, séquestre, confirmation et libération.
- Les clés MeSomb/Campay ne devaient pas être codées en dur.
- En local, l'équipe devait pouvoir tester sans effectuer de paiement réel.

### Solutions apportées

- Payment Service récupère les informations de commande depuis Business Service.
- Les clés fournisseur sont fournies par variables d'environnement.
- Support du mode mock local via `PAYMENT_PROVIDER=mock`, doublé de l'alias `PAYMENT_MODE=mock` utilisé par Docker Compose (`payment.provider=${PAYMENT_PROVIDER:${PAYMENT_MODE:mesomb}}`, `PAYMENT_PROVIDER` restant prioritaire).
- Gestion de la confirmation de paiement et notification de Business Service.
- Conservation des références fournisseur et des informations utiles au frontend.

### Points de vigilance

- En production, les variables `MESOMB_APPLICATION_KEY`, `MESOMB_ACCESS_KEY` et `MESOMB_SECRET_KEY` doivent être obligatoires.
- Les valeurs mock/locales ne doivent jamais être utilisées pour un vrai déploiement.
- Les callbacks fournisseur doivent être protégés et tracés.

## Support Service

Service : `Backend-Kametude/support-service`

### Difficultés rencontrées

- Le projet avait besoin d'un support transversal pour chat, notifications, stockage de fichiers et WebSocket.
- Les fichiers privés ne devaient pas être accessibles par n'importe quel utilisateur.
- Les notifications devaient être créées par les autres services sans exposer de routes internes au navigateur.
- Le frontend devait pouvoir ouvrir les livrables et pièces jointes sans contourner la sécurité.

### Solutions apportées

- Ajout des APIs de chat et notifications.
- Ajout du stockage public/privé.
- Protection des routes privées par l'identité issue du JWT.
- Routes internes protégées par `INTERNAL_SERVICE_TOKEN`.
- Utilisation par Business, Request et Payment pour notifier les utilisateurs.
- WebSocket disponible pour une évolution temps réel, avec REST comme base stable côté frontend.

### Points de vigilance

- Si le stockage reste local, il faudra prévoir un volume persistant en production.
- Sur le VPS de production, conserver un volume persistant pour les fichiers, ou basculer vers un stockage objet externe si le volume local ne suffit plus.
- Continuer à vérifier les droits d'accès aux fichiers liés aux commandes.

## Frontend React/Vite

Projet : racine du dépôt `Kam-Etud`

### Difficultés rencontrées

- Le frontend devait migrer d'anciens contrats vers l'API Gateway et les microservices.
- Les utilisateurs voulaient tester plusieurs rôles dans le même navigateur.
- Les listes de villes et catégories devaient venir du backend.
- Certaines dates passées pouvaient être sélectionnées dans les formulaires.
- Les icônes monétaires n'étaient pas homogènes.
- Certains textes/couleurs de l'interface devaient rester lisibles en mode sombre.

### Solutions apportées

- Création d'un client API centralisé dans `src/lib/api.ts`.
- Tous les appels passent par `VITE_API_URL`, qui pointe vers la Gateway.
- Mise en place d'un mode `sessionStorage` en développement avec `VITE_AUTH_STORAGE=session`.
- Ajout de `README_AUTH_STORAGE.md` pour documenter le fonctionnement multi-session.
- Nettoyage du stockage d'authentification via `authTokenStorage`.
- Chargement dynamique des villes et catégories depuis Catalog Service.
- Validation frontend pour empêcher les dates passées dans les demandes.
- Validation frontend pour empêcher les délais de livraison invalides dans les gigs.
- Remplacement des icônes `$` par `Coins` aux endroits concernés.
- Vérifications régulières avec `npx.cmd tsc --noEmit` et `npm.cmd run build`.

### Points de vigilance

- Le mode `sessionStorage` actuel dépend de la variable `VITE_AUTH_STORAGE=session`.
- En production, l'usage de cookies `HttpOnly`, `Secure` et `SameSite` resterait plus robuste pour les tokens sensibles.
- Les variables Vite doivent être définies avant le démarrage du serveur.
- Les pages qui permettent le choix d'une ville doivent prévoir le cas "ville absente de la liste".

## Dockerisation et déploiement

### Difficultés rencontrées

- Le `docker-compose.yml` ne couvrait que les bases PostgreSQL et RabbitMQ : les sept applications Spring Boot devaient être lancées à la main dans sept terminaux.
- Sans Dockerfiles, une démo sur une machine neuve dépendait d'une installation Java/Maven complète.
- Les services démarraient parfois avant que leur base soit réellement prête à accepter des connexions.
- Les ports hôtes PostgreSQL `5432` à `5436` entraient en conflit avec une installation PostgreSQL locale ou avec les plages réservées par Windows.
- Les secrets locaux ont des valeurs par défaut pratiques en développement, mais dangereuses en production.
- Le smoke test attendait une erreur MeSomb provoquée par des clés factices, ce qui devenait faux dès que le paiement passait en mode mock.

### Solutions apportées

- Ajout d'un `Dockerfile` multi-stage pour chacun des sept services : build `maven:3.9-eclipse-temurin-21-alpine`, exécution sur `eclipse-temurin:21-jre-alpine` avec un utilisateur non-root `spring`.
- Ajout d'un `.dockerignore` dans chaque projet pour exclure `target/`, les fichiers d'IDE, les logs et les `.env`.
- `docker-compose.yml` étendu aux sept applications : elles rejoignent le réseau `backend-kametude_default` et se joignent par nom de service (`http://identity-service:8081`, etc.).
- Ajout d'un `healthcheck` (`pg_isready`, `rabbitmq-diagnostics ping`) sur chaque dépendance, et de `depends_on: condition: service_healthy` sur les applications.
- Ports hôtes PostgreSQL déplacés vers `5431` puis `5632`–`5636` pour éviter les conflits, alignés sur ce que `start-local.ps1` utilisait déjà. Les conteneurs continuent d'écouter sur `5432` en interne.
- Ajout de `docker-compose.prod.yml` pour le VPS : tous les secrets sont obligatoires via la syntaxe `${VAR:?}`, RabbitMQ et les bases n'exposent plus de port hôte, le stockage Support est persisté dans le volume `support_storage` et les origines CORS pointent sur `https://kametud.com`.
- Ajout de `.env.prod.example` comme modèle du fichier `.env.prod` non versionné (ignoré par `.gitignore`).
- Ajout du script racine `start-demo.ps1` : il construit et démarre toute la pile, attend les healthchecks puis le message de démarrage Spring Boot de chaque application, vérifie `/actuator/health` de la Gateway et le login démo, puis exécute le smoke test bout-en-bout.
- Le smoke test vérifie désormais le comportement attendu en mode mock : paiement accepté, statut `HELD` et référence `MOCK-COLLECT-*`.
- Le script de seed accepte l'utilisateur PostgreSQL via `IDENTITY_DB_USERNAME` au lieu d'un `user` codé en dur.

### Points de vigilance

- Le `docker-compose.yml` de développement contient volontairement des valeurs par défaut lisibles (`password`, `change-this-internal-token`, un `JWT_SECRET` de test). Elles ne doivent jamais servir en production : `docker-compose.prod.yml` refuse de démarrer si la variable correspondante est absente du `.env.prod`.
- Le frontend n'est pas encore conteneurisé : il reste lancé par Vite en local et peut être déployé comme site statique, ou recevoir un Dockerfile Node/Nginx par la suite.
- `docker-compose.prod.yml` expose encore les ports `8081`–`8086` des microservices sur l'hôte ; derrière un reverse proxy, seul `8080` devrait rester accessible.

## CI et qualité

### Difficultés rencontrées

- Les erreurs Java, TypeScript ou Lombok devaient être détectées avant merge.
- Chaque microservice doit pouvoir échouer clairement sans bloquer le diagnostic des autres.
- Le frontend doit vérifier lint, typage et build.

### Solutions apportées

- Workflows GitHub Actions backend/frontend prévus dans `.github/workflows`.
- Matrix Maven sur les microservices backend.
- Cache Maven pour accélérer les builds.
- Vérifications frontend avec installation npm, TypeScript et build Vite.

## Résumé final

Le projet a gagné en structure : le frontend est maintenant orienté Gateway, les responsabilités métier sont mieux séparées et les principaux flux sont portés par les microservices. Les plus gros efforts ont porté sur la sécurisation des contrats, la migration depuis les anciens flux frontend, la cohérence des ports, la gestion des rôles et la validation des données côté backend.

La dockerisation applicative est désormais terminée : les sept microservices ont leur Dockerfile, la pile complète démarre avec `.\start-demo.ps1` et un fichier Compose de production distinct impose la présence des secrets réels.

Les prochaines priorités recommandées sont :

- conteneuriser ou publier le frontend (Dockerfile Node/Nginx, ou déploiement en site statique) ;
- placer un reverse proxy TLS devant la Gateway et cesser d'exposer les ports `8081`–`8086` sur le VPS ;
- ajouter un workflow propre pour les suggestions de villes ;
- enrichir les tests d'intégration interservices ;
- finaliser le déploiement sur le VPS (`kametud.com`) avec les variables d'environnement réelles du `.env.prod`.
