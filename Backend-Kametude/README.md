# Project KamEtude - Backend Architecture (Team of 12)

## Résumé global du backend — prêt GitHub

Le backend réel du dépôt se trouve dans `Backend-Kametude`. Il contient sept applications Spring Boot `4.1.0` séparées, chacune avec sa base PostgreSQL dédiée. Le frontend ne doit appeler que l'API Gateway.

| Microservice | Port HTTP | Base locale | Rôle |
| --- | ---: | --- | --- |
| `api-gateway` | `8080` | — | Entrée unique du frontend, CORS, validation JWT, contrôle de premier niveau et routage. |
| `identity-service` | `8081` | `identity_db` | Authentification, JWT, profils, rôles, bannissement et KYC étudiant. |
| `requestservice` | `8082` | `request_db` | Demandes clients et propositions étudiantes. |
| `catalog-service` | `8083` | `catalog_db` | Gigs, catégories, villes, publication et statistiques visibles sur `/services`. |
| `business-service` | `8084` | `business_db` | Commandes, missions, livrables, révisions, avis, litiges, statistiques et abus. |
| `payment-service` | `8085` | `payment_db` | Collecte, séquestre, vérification et libération des fonds. |
| `support-service` | `8086` | `support_db` | Chat, notifications, stockage public/privé et WebSocket. |

### Rôle de l'API Gateway

La Gateway reçoit toutes les requêtes publiques sur `http://localhost:8080`, vérifie le JWT, bloque les routes internes et reconstruit les en-têtes de confiance (`X-User-Id`, `X-User-Role`, `X-User-Email`) avant de router vers le service concerné.

### Communication entre microservices

La communication actuelle est REST synchrone avec Spring REST Client et URLs configurables par environnement. Les routes internes utilisent `INTERNAL_SERVICE_TOKEN` et ne sont pas exposées par la Gateway. Aucun message broker n'est requis pour le fonctionnement local actuel; le service `rabbitmq` présent dans `docker-compose.yml` reste prévu pour une évolution asynchrone ultérieure.

### Comptes de démonstration

Après démarrage local, créer ou restaurer les quatre comptes de test avec :

```powershell
.\scripts\seed-demo-users.ps1
```

à lancer depuis `Backend-Kametude`, ou bien :

```powershell
.\Backend-Kametude\scripts\seed-demo-users.ps1
```

depuis la racine du dépôt.

Comptes créés/mis à jour :

| Rôle | Email | Mot de passe |
| --- | --- | --- |
| Admin | `admin@kametud.com` | `123456789!` |
| Modérateur | `moderator@kametud.com` | `123456789!` |
| Étudiant vérifié | `student@kametud.com` | `123456789!` |
| Client | `client@kametud.com` | `123456789!` |

Le script est idempotent : il peut être relancé sans créer de doublons. Il remet ces comptes en état actif et marque l'étudiant comme vérifié pour faciliter les tests locaux.

### Ports PostgreSQL locaux

Le `docker-compose.yml` expose par défaut `5431` pour Identity puis `5632` à `5636` pour les cinq autres bases, afin d'éviter la plage souvent réservée par Windows et une installation PostgreSQL locale occupant déjà `5432`. Le script racine `start-local.ps1` utilise les mêmes valeurs et transmet les bons `DB_URL` aux services. Chaque port reste surchargeable par `IDENTITY_DB_PORT`, `CATALOG_DB_PORT`, `REQUEST_DB_PORT`, `BUSINESS_DB_PORT`, `PAYMENT_DB_PORT` et `SUPPORT_DB_PORT`. Les conteneurs continuent d'écouter sur `5432` en interne.

## Synchronisation du parcours mission — 6 juillet 2026

- Apres confirmation du paiement, une commande `accepted` reste affichee dans
  **Demandes** chez l'etudiant. Elle ne rejoint **En cours** qu'apres son
  acceptation explicite, qui la fait passer a `in_progress`.
- Les commandes du client et les missions de l'etudiant sont resynchronisees
  toutes les cinq secondes afin de repercuter les actions effectuees depuis une
  autre session sans rechargement manuel.
- Un livrable associe a la commande reste visible et consultable par le client,
  y compris dans l'historique. L'ouverture du fichier prive est preparee au
  moment du clic pour ne pas etre bloquee par le navigateur.
- La contestation reste disponible pour les statuts `in_progress`, `delivered`
  et `revision_requested`, conformement aux controles de Business Service.

## État de l'intégration — 4 juillet 2026

Le frontend React utilise maintenant exclusivement l'API Gateway `http://localhost:8080` pour l'authentification, les profils/KYC, le catalogue, les demandes/propositions, les commandes, les paiements, les avis/litiges, le chat, les notifications et les fichiers. Supabase n'est plus une dépendance d'exécution de ces parcours.

Tous les services utilisent Java `21` et Spring Boot `4.1.0`. Gateway et Support utilisent Spring Cloud `2025.1.2`.

Deux secrets doivent être cohérents entre processus :

- `JWT_SECRET` : Gateway, Identity et Support.
- `INTERNAL_SERVICE_TOKEN` : Request, Business, Payment et Support.

Les ports internes ne doivent pas être appelés par le navigateur. Les communications synchrones utilisent Spring REST Client et des URLs configurables jusqu'à l'ajout éventuel d'un serveur Eureka.

## Démarrage tout-Docker (démo)

Les sept microservices ont désormais un `Dockerfile` multi-stage et sont déclarés dans `docker-compose.yml`. Depuis la racine du dépôt, une seule commande construit et démarre la pile complète :

```powershell
.\start-demo.ps1 -SeedDemo
```

Le script vérifie Docker, lance `docker compose up -d --build`, attend que les bases soient `healthy` puis que les sept applications Spring Boot aient terminé leur démarrage, contrôle `http://localhost:8080/actuator/health`, recrée les comptes de démonstration avec `-SeedDemo`, valide le login via la Gateway et exécute enfin le smoke test bout-en-bout. Le délai d'attente est réglable avec `-TimeoutSeconds`.

Dans ce mode, `payment-service` tourne avec `PAYMENT_MODE=mock` : aucune transaction réelle n'est effectuée. Le frontend reste à lancer séparément avec `.\start-frontend.ps1` sur `http://localhost:5173`.

Arrêt de la pile :

```powershell
cd Backend-Kametude
docker compose down
```

Les valeurs par défaut de ce fichier Compose (`password`, `change-this-internal-token`, `JWT_SECRET` de test) sont réservées au développement local.

## Déploiement production (VPS)

`docker-compose.prod.yml` reprend la même topologie mais impose tous les secrets via la syntaxe `${VAR:?}` : le démarrage échoue explicitement si une variable manque. Les bases et RabbitMQ n'exposent aucun port hôte, le stockage de Support est persisté dans le volume `support_storage` et les origines CORS pointent sur `https://kametud.com`.

```powershell
Copy-Item .env.prod.example .env.prod   # puis renseigner chaque valeur
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

`Backend-Kametude/.env.prod` est ignoré par `.gitignore` et ne doit jamais être committé. Toutes les variables attendues sont listées dans `.env.prod.example` : `JWT_SECRET`, `INTERNAL_SERVICE_TOKEN`, les identifiants des six bases, ceux de RabbitMQ, `PAYMENT_MODE` et `STORAGE_LOCATION`.

## Démarrage local complet

Ce parcours reste utile pour développer sur un seul microservice sans reconstruire d'image. Le lanceur recommandé se trouve à la racine du dépôt :

```powershell
.\start-local.ps1
```

Il automatise les étapes ci-dessous, vérifie Java 21, Node.js et Docker, conserve les logs dans `logs/local` et permet un arrêt propre avec `.\start-local.ps1 -Stop`. Les commandes manuelles restent documentées pour le diagnostic ou le travail sur un seul microservice.

1. Utiliser `.env.example` comme modèle, puis charger au minimum `JWT_SECRET`, `INTERNAL_SERVICE_TOKEN` et les trois clés `MESOMB_*` dans les configurations d'exécution de l'IDE ou dans chaque terminal concerné. Spring ne charge pas automatiquement un fichier `.env` brut.
2. Démarrer PostgreSQL : `docker compose up -d identity-db catalog-db request-db business-db payment-db support-db`.

Les ports hôtes par défaut sont `5431` pour Identity et `5632`–`5636` pour Catalog, Request, Business, Payment et Support. En cas de conflit résiduel, surcharger `IDENTITY_DB_PORT`, `CATALOG_DB_PORT`, `REQUEST_DB_PORT`, `BUSINESS_DB_PORT`, `PAYMENT_DB_PORT` ou `SUPPORT_DB_PORT`, puis reporter les mêmes ports dans les `DB_URL` des services concernés. Les conteneurs continuent d'écouter sur `5432` en interne.
3. Depuis sept terminaux ouverts dans `Backend-Kametude`, lancer :

```powershell
mvn -f identity-service/pom.xml spring-boot:run
mvn -f catalog-service/pom.xml spring-boot:run
mvn -f requestservice/pom.xml spring-boot:run
mvn -f business-service/pom.xml spring-boot:run
mvn -f support-service/pom.xml spring-boot:run
mvn -f payment-service/pom.xml spring-boot:run
mvn -f api-gateway/pom.xml spring-boot:run
```

4. Dans la racine frontend : `npm run dev`. Le navigateur utilise `VITE_API_URL=http://localhost:8080`.

L'ordre limite les erreurs temporaires : Identity/Catalog d'abord, domaines métier ensuite, Gateway en dernier. Les services restent toutefois tolérants aux notifications indisponibles pendant leur démarrage.

### Smoke test local complet

Lorsque les services sont démarrés et que les quatre comptes de démonstration existent, exécuter depuis `Backend-Kametude` :

```powershell
.\scripts\seed-demo-users.ps1
.\scripts\local-smoke-test.ps1
```

Le script vérifie les rôles, le KYC, les protections Gateway, le catalogue, les commandes, le stockage privé, le chat, les notifications, les demandes/propositions, les avis et les litiges.

Il suppose que `payment-service` tourne en mode mock (`PAYMENT_MODE=mock` ou `PAYMENT_PROVIDER=mock`) : il attend un paiement accepté avec le statut `HELD` et une référence `MOCK-COLLECT-*`, puis simule la confirmation du séquestre via la route interne locale. Aucun paiement réel n'est effectué. Avec `PAYMENT_PROVIDER=mesomb`, cette assertion échoue volontairement.

Cette architecture est optimisée pour notre équipe de 12 étudiants, répartis en binômes stratégiques.

## Répartition des Binômes

| Binôme | Microservices | Responsables | Responsabilités Clés |
| :--- | :--- | :--- | :--- |
| **Binôme 1** | `api-gateway` | **varol** | Sécurité JWT, Routage, Discovery, Config. |
| **Binôme 2** | `identity-service` | **Darwin & Ericka** | Auth, Profils Étudiants/Clients, Vérification KYC. |
| **Binôme 3** | `catalog-service` | **Noel & Steve** | Gigs (paliers), Catégories, Recherche, Villes. |
| **Binôme 4** | `request-service` | **Prisca & Melista** | Appels d'offres clients, Propositions étudiants. |
| **Binôme 5** | `business-service` | **Elvira & Veronique** | Workflow Commandes (Orders), Délais, Livrables. |
| **Binôme 6** | `payment-service` | **varol & Dilane** | Intégration Campay, Escrow, Portefeuille. |
| **Binôme 7** | `support-service` | **Derrick & Franckline** | Chat, Notifications, Dashboards Admin, Storage. |


## Infrastructure
- **Port Gateway :** 8080
- **Service Discovery :** non actif en local; la Gateway utilise des URLs directes configurables.
- **Base de données :** PostgreSQL (1 instance par service via Docker Compose)
- **Communication :** REST synchrone via Spring REST Client; RabbitMQ est présent dans Compose mais pas requis par le workflow actuel.

## Ports locaux harmonisés — 4 juillet 2026

| Composant | Port HTTP | Base PostgreSQL |
| --- | ---: | --- |
| API Gateway | 8080 | — |
| Identity Service | 8081 | `identity_db` — 5431 |
| Request Service | 8082 | `request_db` — 5633 |
| Catalog Service | 8083 | `catalog_db` — 5632 |
| Business Service | 8084 | `business_db` — 5634 |
| Payment Service | 8085 | `payment_db` — 5635 |
| Support Service | 8086 | `support_db` — 5636 |

Ces ports sont ceux vus depuis l'hôte. À l'intérieur du réseau Docker, chaque base écoute sur `5432` et les services se joignent par nom (`jdbc:postgresql://identity-db:5432/identity_db`).

Le frontend doit appeler uniquement la Gateway sur `http://localhost:8080`. Les ports internes restent configurables par variables d'environnement.

## Migration du frontend

Les parcours actifs utilisent maintenant la Gateway et les microservices : identité/KYC, catalogue, demandes/propositions, commandes, paiement, chat, notifications et stockage. Les anciens fichiers de migrations, fonctions Edge, variables d'environnement et types générés du fournisseur précédent ont été retirés du frontend le 5 juillet 2026. Les README propres à chaque service conservent l'historique des décisions pour l'équipe.

## Socle Spring validé — 4 juillet 2026

- Les sept applications utilisent Java 21 et Spring Boot `4.1.0`.
- `api-gateway` et `support-service` utilisent Spring Cloud `2025.1.2`.
- Les anciens parents et starters Boot 3 sont conservés en commentaires dans les POM.
- Les sept suites Maven passent après migration.
- La Gateway valide les JWT émis par Identity et partage `JWT_SECRET` avec lui.

Pourquoi : une version unique réduit les incompatibilités entre starters, imports de tests et Spring Security. Spring Cloud `2025.1.2` est retenu car il apporte la compatibilité Boot `4.1.0`.

Copier `.env.example` vers un fichier local non versionné et fournir le même `JWT_SECRET` à la Gateway, à Identity et à Support.

## Frontend connecté dans l'étape Auth/KYC

- `AuthContext` n'utilise plus Supabase pour login, inscription, restauration de session et logout.
- Les profils et écrans admin KYC passent par la Gateway.
- Les documents KYC et avatars passent par `support-service`.
- Google OAuth et OTP téléphone sont temporairement désactivés tant que leurs endpoints backend n'existent pas.

## Frontend connecté dans l'étape Catalogue

- Recherche, détail et liste personnelle des gigs via `/api/gigs`.
- Création, publication, activation et suppression via JWT.
- Catégories et villes publiques chargées depuis PostgreSQL.
- Gestion admin des catégories et villes via la Gateway.

## Frontend connecté dans l'étape Demandes

- Liste publique, détail, création, espace « mes demandes » et annulation via `/api/v1/requests`.
- Création et liste personnelle des propositions via `/api/v1/proposals`.
- Acceptation atomique d'une proposition par le client propriétaire ; les autres sont automatiquement refusées.
- Identités client/étudiant déduites du JWT et noms chargés depuis Identity Service.
- Étudiant vérifié et non banni obligatoire pour proposer.
- Les anciens appels Supabase concernés sont conservés en commentaires explicatifs dans le frontend et le backend.
- La création d'une commande après acceptation est reportée à l'étape Business Service pour éliminer proprement l'ancien insert Supabase.

## Migration frontend/backend finalisée

- L'acceptation d'une proposition crée désormais une commande Business de façon idempotente.
- Business est la source de vérité des commandes, livrables, révisions, avis et litiges.
- Payment lit le montant et les participants depuis Business, puis gère collecte, vérification, séquestre et libération MeSomb.
- Support sécurise le chat par participant, les notifications personnelles/inter-services et les fichiers privés par propriétaire ou commande.
- Les événements Request, Business et Payment créent des notifications via une route interne protégée.
- Les anciens contrats dangereux restent commentés dans les contrôleurs concernés afin de rendre les changements visibles aux auteurs.
- Chaque microservice modifié contient son propre README détaillant le contrat, les changements et leurs raisons.

Le paragraphe historique ci-dessus sur le report de la commande décrit l'étape précédente ; il est conservé pour retracer la migration et est remplacé fonctionnellement par cette finalisation.
