# API Gateway — Kam'Etud

## Synthèse GitHub — 6 juillet 2026

### Description

API Gateway est le point d'entrée HTTP unique du frontend Kam'Etud. Elle écoute sur `8080`, applique CORS, valide les JWT, bloque les routes internes et route les requêtes vers les microservices Spring Boot.

### Changements

- Migration vers Java `21`, Spring Boot `4.1.0` et Spring Cloud `2025.1.2`.
- Routage unifié de l'authentification, des profils, du catalogue, des demandes, des commandes, des paiements, du support, des litiges et des abus.
- Vérification de révocation des tokens auprès d'Identity Service avant propagation.
- Protection par rôle des routes sensibles : admin, modérateur, client et étudiant.

### Ajouts

- Filtre JWT qui supprime les faux en-têtes `X-User-*` du navigateur et reconstruit l'identité depuis le token.
- Routes sécurisées `/api/abuse-reports/**` et `/api/admin/abuse-reports/**`.
- Tests de routage et d'autorisation pour éviter une exposition accidentelle des routes internes.

### Fonctionnement

Le frontend appelle uniquement `http://localhost:8080`. La Gateway transfère ensuite vers Identity `8081`, Request `8082`, Catalog `8083`, Business `8084`, Payment `8085` et Support `8086`. Les destinations sont configurables avec `IDENTITY_SERVICE_URL`, `REQUEST_SERVICE_URL`, `CATALOG_SERVICE_URL`, `BUSINESS_SERVICE_URL`, `PAYMENT_SERVICE_URL`, `SUPPORT_SERVICE_URL` et `SUPPORT_SERVICE_WS_URL`.

### Problèmes rencontrés

- Les anciens appels directs aux microservices provoquaient CORS et `Failed to fetch`; la Gateway centralise maintenant les origines autorisées.
- Un token signé mais émis avant bannissement restait valide; la Gateway vérifie maintenant l'accès courant auprès d'Identity.
- Les routes `/internal/**` ne doivent jamais être accessibles depuis le navigateur; elles sont bloquées au niveau Gateway.

## Sécurisation des dossiers d'abus — 6 juillet 2026

- `/api/abuse-reports/**` est routé vers Business Service et exige un JWT de
  modérateur pour la création.
- `/api/admin/abuse-reports/**` est routé vers Business Service et exige un JWT
  administrateur pour la consultation et la décision.
- La Gateway reconstruit toujours `X-User-Id` et `X-User-Role` depuis le JWT;
  Business Service vérifie une seconde fois le rôle.
- Des tests couvrent le refus des étudiants, l'accès de création du modérateur
  et la file strictement réservée à l'administrateur.

Pourquoi : le dossier contient des accusations et des preuves privées. Il ne
doit être ni public, ni accessible à un simple participant au litige.

L'API Gateway est le point d'entrée HTTP unique du frontend. Le navigateur appelle uniquement `http://localhost:8080`; la Gateway transmet ensuite la requête au bon microservice.

## Révocation, statistiques et modération — 5 juillet 2026

- Chaque JWT est confronté à l'état courant `enabled` dans Identity avant propagation des en-têtes `X-User-*`.
- Le contrôle est limité à 2 secondes et fonctionne en mode fermé : Identity indisponible produit `503`, jamais un accès implicite.
- `/api/student-stats/**` est routé vers Business.
- Les signalements sont réservés à l'étudiant concerné; modération et exécution manuelle de l'auto-validation sont réservées au staff.
- Les routes `/internal/**` restent bloquées depuis la Gateway.

Pourquoi : vérifier uniquement la signature du JWT ne révoque pas un token émis avant le bannissement.

## Port et stack

- Port : `8080`
- Java : `21`
- Spring Boot : `4.1.0`
- Spring Cloud : `2025.1.2`
- Gateway : Spring Cloud Gateway Server WebFlux

L'ancienne combinaison Boot `3.5.15` / Cloud `2025.0.3` est conservée en commentaire dans `pom.xml`. Spring Cloud `2025.1.2` apporte la compatibilité avec Spring Boot `4.1.0`.

## Routage local

| Chemin public | Destination par défaut |
| --- | --- |
| `/api/auth/**`, `/api/profiles/**`, `/api/verifications/**`, `/api/admin/profiles/**`, `/api/admin/verifications/**`, `/api/students/**` | Identity — `http://localhost:8081` |
| `/api/v1/requests/**`, `/api/v1/proposals/**` | Request — `http://localhost:8082` |
| `/api/gigs/**`, `/api/categories/**`, `/api/cities/**` | Catalog — `http://localhost:8083` |
| `/api/orders/**`, `/api/deliverables/**`, `/api/revisions/**`, `/api/disputes/**`, `/api/abuse-reports/**`, `/api/admin/abuse-reports/**` | Business — `http://localhost:8084` |
| `/api/payments/**` | Payment — `http://localhost:8085` |
| `/api/chat/**`, `/api/notifications/**`, `/api/storage/**`, `/ws/**` | Support — `http://localhost:8086` |

Les destinations sont surchargeables avec `IDENTITY_SERVICE_URL`, `REQUEST_SERVICE_URL`, `CATALOG_SERVICE_URL`, `BUSINESS_SERVICE_URL`, `PAYMENT_SERVICE_URL`, `SUPPORT_SERVICE_URL` et `SUPPORT_SERVICE_WS_URL`.

## Lancement

```powershell
mvn spring-boot:run
```

Vérification :

```powershell
curl.exe http://localhost:8080/actuator/health
```

## Modifications du 4 juillet 2026

### Ce qui a été ajouté

- Projet Maven exécutable dans `pom.xml`.
- Classe principale `ApiGatewayApplication`.
- Routes HTTP et WebSocket dans `application.yml`.
- CORS pour `http://localhost:5173`, `http://127.0.0.1:5173` et les adresses IPv4 locales sur le port `5173`, car Vite peut annoncer l'une de ces origines en local.
- Deduplication de `Access-Control-Allow-Origin` et `Access-Control-Allow-Credentials`. Certains microservices renvoyaient aussi `*`, ce qui produisait un en-tete multiple invalide et le navigateur affichait `Failed to fetch` malgre une reponse backend `200`.
- En production, remplacez `FRONTEND_LAN_PATTERN` par l'origine exacte du frontend.
- Timeouts réseau et endpoint Actuator de santé.
- Test de chargement du contexte Spring.
- Test vérifiant l'enregistrement des huit routes vers les microservices.
- Validation de la signature, de l'expiration et du type des JWT d'accès.
- Protection des profils personnels, des demandes KYC, des uploads et des routes admin.
- Protection admin des fichiers sous `/api/storage/private/**`; les avatars publics restent lisibles sans token.
- Suppression des faux en-têtes `X-User-*` envoyés par le navigateur, puis reconstruction depuis le JWT valide.
- Tests des réponses `401`, `403` et de la propagation de l'identité.
- Protection des mutations `/api/gigs/**` pour le rôle `STUDENT`.
- Protection des mutations catégories/villes pour le rôle `ADMIN`; leurs lectures restent publiques.
- Lecture publique des demandes `/api/v1/requests/**`, sauf `/mine`.
- JWT et rôle `CLIENT` obligatoires pour créer, annuler, fermer ou supprimer une demande.
- JWT et rôle `STUDENT` obligatoires pour créer, retirer ou lister ses propositions.
- JWT et rôle `CLIENT` obligatoires pour accepter ou refuser une proposition.
- Toutes les lectures de propositions sont protégées ; Request Service limite ensuite les données au propriétaire concerné.
- Tests Gateway ajoutés pour les lectures publiques, les réponses `401`/`403` et la reconstruction des en-têtes fiables sur les propositions.

### Pourquoi

Le dossier ne contenait auparavant qu'un exemple de configuration. Le frontend ne pouvait donc pas réellement appeler les microservices via le port `8080`.

### Choix temporaire

Les routes utilisent des URL directes plutôt que `lb://...`, car aucun serveur Eureka n'est encore disponible. Le routage par découverte sera activé dans une étape ultérieure.

### Pourquoi le filtre JWT

La Gateway est la frontière publique. Elle refuse maintenant un refresh token utilisé comme access token et applique les rôles aux routes Identity, Storage, Catalog et Request. Les microservices conservent aussi leurs contrôles métier : la Gateway ne remplace pas l'autorisation dans chaque domaine.

`JWT_SECRET` doit avoir exactement la même valeur dans la Gateway et l'Identity Service.

## Finalisation Business, Payment et Support — 4 juillet 2026

- Ajout du routage des avis et litiges vers Business Service.
- Commandes et paiements protégés par JWT ; les rôles sont filtrés par opération, puis revérifiés par les services.
- Chat, notifications et stockage privé exigent un access token.
- Les fichiers privés ne sont plus réservés seulement à l'admin : Support Service décide selon le propriétaire, le staff et la commande liée.
- Les routes publiques restent limitées au catalogue, aux demandes ouvertes, aux avis étudiants et aux fichiers explicitement publics.
- Des tests couvrent les frontières `CLIENT`, `STUDENT`, `ADMIN`/`MODERATOR` et la suppression des faux en-têtes `X-User-*`.
- Les chemins `/internal/**` sont refusés par la Gateway et l'en-tête `X-Internal-Service-Token` venant du navigateur est supprimé. Ces routes ne sont accessibles que directement entre services.

Pourquoi : la Gateway constitue une première barrière, mais les autorisations liées aux données restent dans chaque microservice. Cela évite qu'une simple erreur de routage donne accès à une commande, un paiement ou un fichier étranger.
