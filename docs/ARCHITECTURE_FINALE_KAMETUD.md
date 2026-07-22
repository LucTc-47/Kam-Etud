# Architecture finale - Kam'Etud

Document de reference pour l'architecture actuelle de Kam'Etud, plateforme camerounaise de freelance etudiant.

Le depot contient aujourd'hui :

- un frontend React + Vite + TypeScript a la racine du depot ;
- un backend microservices Spring Boot dans `Backend-Kametude` ;
- une API Gateway comme point d'entree unique du frontend ;
- six bases PostgreSQL separees, une par service metier qui persiste des donnees ;
- des workflows GitHub Actions pour verifier backend et frontend en CI.

---

## 1. Vue d'ensemble

Kam'Etud est organise autour de sept applications Spring Boot independantes :

| Service | Port HTTP local | Base locale | Role |
| --- | ---: | --- | --- |
| `api-gateway` | `8080` | aucune | Point d'entree unique, CORS, validation JWT, filtrage des routes internes et routage. |
| `identity-service` | `8081` | `identity_db` | Authentification, JWT, profils, roles, bannissement et verification KYC etudiante. |
| `requestservice` | `8082` | `request_db` | Demandes publiees par les clients et propositions envoyees par les etudiants. |
| `catalog-service` | `8083` | `catalog_db` | Gigs, categories, villes, publication et consultation du catalogue. |
| `business-service` | `8084` | `business_db` | Commandes, missions, livrables, revisions, avis, litiges, statistiques et abus. |
| `payment-service` | `8085` | `payment_db` | Paiements, sequestration des fonds, confirmation, echec et liberation. |
| `support-service` | `8086` | `support_db` | Chat, notifications, stockage public/prive et WebSocket. |

> Note port Gateway : le code actuel utilise `GATEWAY_PORT:8080` par defaut. Si l'equipe veut exposer la Gateway sur `8099`, il suffit de definir `GATEWAY_PORT=8099` au lancement.

---

## 2. Schema logique

```text
Frontend React/Vite
        |
        | HTTP/WS via VITE_API_URL
        v
api-gateway
        |
        | Routage REST + JWT + headers de confiance
        |
        +--> identity-service   --> identity_db
        +--> requestservice     --> request_db
        +--> catalog-service    --> catalog_db
        +--> business-service   --> business_db
        +--> payment-service    --> payment_db
        +--> support-service    --> support_db

RabbitMQ est present dans docker-compose.yml pour une evolution asynchrone,
mais le fonctionnement local actuel repose principalement sur REST synchrone.
```

Le navigateur ne doit jamais appeler directement les microservices internes. Il appelle uniquement la Gateway, qui route ensuite vers le service concerne.

---

## 3. Frontend

Le frontend est une application React + Vite + TypeScript situee a la racine du depot.

Fichiers principaux :

- `package.json` : scripts npm et dependances ;
- `src/` : pages, composants, hooks, contextes et appels API ;
- `src/lib/api.ts` : client HTTP principal vers la Gateway ;
- `src/hooks/useBackendData.ts` : hooks applicatifs pour les donnees backend ;
- `src/contexts/AuthContext.tsx` : session utilisateur, login, logout et restauration ;
- `vite.config.ts` : configuration Vite.

Scripts utiles :

```powershell
npm ci
npm run dev
npm run lint
npx tsc --noEmit
npm run build
npm run test
```

Variable importante :

```env
VITE_API_URL=http://localhost:8080
```

Si la Gateway est lancee sur `8099`, mettre :

```env
VITE_API_URL=http://localhost:8099
```

---

## 4. API Gateway

La Gateway est le seul point d'entree HTTP public du backend.

Responsabilites :

- recevoir les appels du frontend ;
- appliquer la configuration CORS ;
- valider les JWT emis par `identity-service` ;
- bloquer les routes internes ;
- reconstruire les headers de confiance pour les services :
  - `X-User-Id`
  - `X-User-Role`
  - `X-User-Email`
- router les appels vers le microservice cible.

Routes principales :

| Prefixe public | Service cible |
| --- | --- |
| `/api/auth/**` | `identity-service` |
| `/api/profiles/**` | `identity-service` |
| `/api/verifications/**` | `identity-service` |
| `/api/admin/verifications/**` | `identity-service` |
| `/api/admin/profiles/**` | `identity-service` |
| `/api/students/**` | `identity-service` |
| `/api/gigs/**` | `catalog-service` |
| `/api/categories/**` | `catalog-service` |
| `/api/cities/**` | `catalog-service` |
| `/api/v1/requests/**` | `requestservice` |
| `/api/v1/proposals/**` | `requestservice` |
| `/api/orders/**` | `business-service` |
| `/api/deliverables/**` | `business-service` |
| `/api/revisions/**` | `business-service` |
| `/api/reviews/**` | `business-service` |
| `/api/disputes/**` | `business-service` |
| `/api/abuse-reports/**` | `business-service` |
| `/api/admin/abuse-reports/**` | `business-service` |
| `/api/student-stats/**` | `business-service` |
| `/api/payments/**` | `payment-service` |
| `/api/chat/**` | `support-service` |
| `/api/notifications/**` | `support-service` |
| `/api/storage/**` | `support-service` |
| `/ws/**` | `support-service` WebSocket |

---

## 5. Services backend

### 5.1 identity-service

Responsabilites :

- inscription et connexion ;
- generation et validation logique des tokens JWT ;
- refresh token ;
- gestion des profils ;
- roles `admin`, `moderator`, `student`, `client` ;
- bannissement et reactivation ;
- verification KYC etudiante ;
- endpoints internes pour verifier l'acces ou resumer un profil.

Donnees principales :

- `users`
- `profiles`
- `profile_skills`
- `verification_requests`

Le service partage le meme `JWT_SECRET` avec la Gateway et le Support Service.

### 5.2 requestservice

Responsabilites :

- creation de demandes par les clients ;
- consultation des demandes ouvertes ;
- creation de propositions par les etudiants ;
- acceptation ou rejet des propositions ;
- creation d'une commande metier via `business-service` lorsqu'une proposition est acceptee.

Donnees principales :

- `gig_requests`
- `request_proposals`

### 5.3 catalog-service

Responsabilites :

- creation, modification et publication des gigs ;
- gestion des trois paliers de prix ;
- recherche et filtrage des services ;
- gestion admin des categories et villes ;
- mise a jour des notes et statistiques de publication.

Donnees principales :

- `gigs`
- `categories`
- `cities`

Regle critique : un etudiant ne peut publier un gig que si `identity-service` confirme qu'il est verifie et non bloque.

### 5.4 business-service

Responsabilites :

- cycle de vie des commandes ;
- missions et livrables ;
- demandes de revision ;
- validation client ;
- auto-validation apres delai ;
- avis ;
- litiges ;
- signalements d'abus ;
- statistiques etudiant.

Donnees principales :

- `orders`
- `deliverables`
- `revisions`
- `reviews`
- `disputes`
- `abuse_reports`

Etats importants d'une commande :

```text
pending -> accepted -> in_progress -> delivered -> completed
```

Etats alternatifs :

```text
revision_requested
disputed
cancelled
refunded
```

Apres confirmation du paiement, la commande passe a `accepted`. Elle reste dans les demandes de l'etudiant jusqu'a acceptation explicite par l'etudiant, puis passe a `in_progress`.

### 5.5 payment-service

Responsabilites :

- initier un paiement ;
- gerer le fournisseur de paiement configure ;
- simuler ou verifier un paiement ;
- conserver les transactions ;
- notifier `business-service` quand les fonds sont detenus ;
- liberer ou rembourser les fonds selon le workflow.

Donnees principales :

- `payment_transactions`

Configuration locale recommandee :

```env
PAYMENT_PROVIDER=mock
```

Le service resout son fournisseur avec `payment.provider=${PAYMENT_PROVIDER:${PAYMENT_MODE:mesomb}}`. `PAYMENT_MODE` est l'alias utilise par les fichiers Docker Compose ; `PAYMENT_PROVIDER` reste prioritaire s'il est defini.

Pour MeSomb reel :

```env
PAYMENT_PROVIDER=mesomb
MESOMB_APPLICATION_KEY=...
MESOMB_ACCESS_KEY=...
MESOMB_SECRET_KEY=...
```

Les secrets de paiement ne doivent jamais etre committes.

### 5.6 support-service

Responsabilites :

- chat client/etudiant rattache a une commande ;
- notifications in-app ;
- stockage des fichiers publics et prives ;
- controle d'acces aux fichiers prives ;
- WebSocket pour les canaux temps reel.

Donnees principales :

- `chat_messages`
- `notifications`
- `stored_files`

Types de fichiers typiques :

- avatars ;
- documents KYC ;
- livrables ;
- preuves de litige ;
- fichiers de demonstration.

---

## 6. Communication entre services

La communication actuelle est principalement REST synchrone.

Exemples :

- `catalog-service` appelle `identity-service` pour verifier si un etudiant peut publier ;
- `requestservice` appelle `identity-service`, `business-service` et `support-service` ;
- `business-service` appelle `identity-service`, `catalog-service`, `payment-service` et `support-service` ;
- `payment-service` appelle `business-service`, `identity-service` et `support-service` ;
- `support-service` appelle `identity-service` et `business-service` pour verifier les droits.

Les routes internes utilisent :

```env
INTERNAL_SERVICE_TOKEN=...
```

Ces routes ne doivent pas etre exposees directement par la Gateway.

RabbitMQ est dans `Backend-Kametude/docker-compose.yml`, mais il reste reserve a une evolution vers des notifications asynchrones ou des evenements metier.

---

## 7. Bases de donnees

Chaque service persistant possede sa propre base PostgreSQL.

| Service Docker | Base | Port hote par defaut |
| --- | --- | ---: |
| `identity-db` | `identity_db` | `5431` |
| `catalog-db` | `catalog_db` | `5632` |
| `request-db` | `request_db` | `5633` |
| `business-db` | `business_db` | `5634` |
| `payment-db` | `payment_db` | `5635` |
| `support-db` | `support_db` | `5636` |

Ces valeurs sont celles vues depuis l'hote. Elles evitent la plage souvent reservee par Windows et une installation PostgreSQL locale occupant deja `5432`. A l'interieur du reseau Docker, chaque base ecoute sur `5432` et les services se joignent par nom de conteneur (`jdbc:postgresql://identity-db:5432/identity_db`).

Les ports peuvent etre surcharges :

```env
IDENTITY_DB_PORT=5431
CATALOG_DB_PORT=5632
REQUEST_DB_PORT=5633
BUSINESS_DB_PORT=5634
PAYMENT_DB_PORT=5635
SUPPORT_DB_PORT=5636
```

Chaque base declare un `healthcheck` `pg_isready` ; les microservices attendent la condition `service_healthy` avant de demarrer.

---

## 8. Securite

Principes retenus :

- le frontend appelle uniquement l'API Gateway ;
- les microservices internes ne sont pas appeles directement par le navigateur ;
- les JWT sont emis par `identity-service` ;
- `api-gateway`, `identity-service` et `support-service` partagent le meme `JWT_SECRET` ;
- les appels internes sensibles utilisent `INTERNAL_SERVICE_TOKEN` ;
- la Gateway ajoute les headers de confiance aux requetes autorisees ;
- les fichiers prives sont servis seulement aux participants autorises ou au staff ;
- les comptes bannis sont bloques dans les parcours sensibles.

Secrets minimums :

```env
JWT_SECRET=<secret-base64-partage>
INTERNAL_SERVICE_TOKEN=<jeton-inter-service>
PAYMENT_PROVIDER=mock
```

En production, ces secrets sont fournis par `Backend-Kametude/.env.prod`, non versionne et charge par `docker-compose.prod.yml`. Ce fichier Compose declare chaque variable sensible avec la syntaxe `${VAR:?}` : le demarrage echoue explicitement si l'une d'elles manque, ce qui interdit tout repli silencieux sur une valeur de developpement.

---

## 9. Demarrage local

Prerequis :

- Java 21 ;
- Node.js 18+ ;
- npm ;
- Docker Desktop avec Docker Compose v2 ;
- PowerShell sur Windows.

Deux parcours coexistent.

**Tout-Docker (recommande pour une demo).** Les sept microservices ont un `Dockerfile` multi-stage et sont declares dans `Backend-Kametude/docker-compose.yml` :

```powershell
.\start-demo.ps1 -SeedDemo
```

Le script construit et demarre la pile, attend les healthchecks des bases puis le demarrage Spring Boot des sept applications, verifie `http://localhost:8080/actuator/health`, recree les comptes de demonstration, valide le login via la Gateway et lance le smoke test bout-en-bout. `payment-service` y tourne avec `PAYMENT_MODE=mock`. Le frontend reste a demarrer separement avec `.\start-frontend.ps1`.

Arret de la pile Docker :

```powershell
cd Backend-Kametude
docker compose down
```

**Processus locaux (recommande pour developper sur un seul service).** Les bases restent dans Docker, les applications tournent via Maven :

```powershell
.\start-local.ps1
```

Arret :

```powershell
.\start-local.ps1 -Stop
```

Arret avec bases PostgreSQL :

```powershell
.\start-local.ps1 -Stop -StopDatabases
```

Backend seulement :

```powershell
.\start-backend.ps1
```

Frontend seulement :

```powershell
.\start-frontend.ps1
```

Creation ou restauration des comptes de demonstration :

```powershell
.\Backend-Kametude\scripts\seed-demo-users.ps1
```

Comptes de demo :

| Role | Email | Mot de passe |
| --- | --- | --- |
| Admin | `admin@kametud.com` | `123456789!` |
| Moderateur | `moderator@kametud.com` | `123456789!` |
| Etudiant verifie | `student@kametud.com` | `123456789!` |
| Client | `client@kametud.com` | `123456789!` |

Smoke test local :

```powershell
cd Backend-Kametude
.\scripts\seed-demo-users.ps1
.\scripts\local-smoke-test.ps1
```

Le smoke test suppose `payment-service` en mode mock : il attend un paiement accepte avec le statut `HELD` et une reference `MOCK-COLLECT-*`, puis simule la confirmation du sequestre par la route interne. Aucun paiement reel n'est effectue.

### Deploiement sur le VPS

`Backend-Kametude/docker-compose.prod.yml` reprend la meme topologie sans exposer les bases ni RabbitMQ, persiste le stockage de Support dans le volume `support_storage` et fixe les origines CORS sur `https://kametud.com`. Tous les secrets proviennent de `.env.prod`, cree a partir de `.env.prod.example` et jamais committe :

```powershell
Copy-Item Backend-Kametude\.env.prod.example Backend-Kametude\.env.prod
cd Backend-Kametude
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

---

## 10. Tests et CI

### Backend

Chaque microservice Maven peut etre teste separement :

```powershell
mvn -f Backend-Kametude\identity-service\pom.xml clean install
mvn -f Backend-Kametude\catalog-service\pom.xml clean install
mvn -f Backend-Kametude\requestservice\pom.xml clean install
mvn -f Backend-Kametude\business-service\pom.xml clean install
mvn -f Backend-Kametude\payment-service\pom.xml clean install
mvn -f Backend-Kametude\support-service\pom.xml clean install
mvn -f Backend-Kametude\api-gateway\pom.xml clean install
```

Les tests backend utilisent H2 en memoire pour les services qui ont une base :

- `identity_test`
- `catalog_test`
- `request_test`
- `business_test`
- `testdb` pour Payment
- `support_test`

Il n'est donc pas necessaire de lancer PostgreSQL pour la CI actuelle.

### Frontend

Verification locale :

```powershell
npm ci
npm run lint
npx tsc --noEmit
npm run build
```

### GitHub Actions

Deux workflows sont prevus :

- `.github/workflows/ci-backend.yml`
- `.github/workflows/ci-frontend.yml`

Le workflow backend utilise une matrix sur les sept microservices. Le workflow frontend installe les dependances, lance ESLint, verifie TypeScript et compile Vite.

Badges README proposes :

```md
[![CI Backend](https://github.com/LucTc-47/Kam-Etud/actions/workflows/ci-backend.yml/badge.svg)](https://github.com/LucTc-47/Kam-Etud/actions/workflows/ci-backend.yml)
[![CI Frontend](https://github.com/LucTc-47/Kam-Etud/actions/workflows/ci-frontend.yml/badge.svg)](https://github.com/LucTc-47/Kam-Etud/actions/workflows/ci-frontend.yml)
```

---

## 11. Organisation Git

Branches protegees :

- `main`
- `dev`

Convention de travail :

```text
feat/prenom
fix/prenom-sujet
chore/prenom-sujet
```

Regle conseillee :

1. creer une branche depuis `dev` ;
2. faire des commits courts et lisibles ;
3. pousser la branche ;
4. ouvrir une pull request vers `dev` ;
5. attendre la validation CI ;
6. demander une revue avant merge.

---

## 12. Repartition fonctionnelle

| Binome | Service | Responsabilites |
| --- | --- | --- |
| Binome 1 | `api-gateway` | Securite JWT, routage, CORS, acces aux routes. |
| Binome 2 | `identity-service` | Authentification, profils, roles, KYC etudiant. |
| Binome 3 | `catalog-service` | Gigs, categories, villes, publication. |
| Binome 4 | `requestservice` | Demandes clients et propositions etudiantes. |
| Binome 5 | `business-service` | Commandes, missions, revisions, livrables, avis, litiges. |
| Binome 6 | `payment-service` | Paiement, escrow, confirmation, liberation, remboursement. |
| Binome 7 | `support-service` | Chat, notifications, stockage, WebSocket. |

---

## 13. Points de vigilance

- Garder la Gateway comme entree unique du frontend.
- Ne pas exposer les routes internes par la Gateway.
- Garder `JWT_SECRET` identique entre Gateway, Identity et Support.
- Garder `INTERNAL_SERVICE_TOKEN` identique entre les services qui utilisent des routes internes.
- Utiliser `PAYMENT_PROVIDER=mock` en local pour eviter tout paiement reel.
- Ajouter PostgreSQL dans la CI seulement si des tests cessent d'utiliser H2.
- Eviter les dependances directes entre bases : un service ne lit jamais la base d'un autre service.
- Documenter toute nouvelle route publique dans la Gateway.
- Preferer des appels REST simples aujourd'hui, puis migrer les notifications et evenements vers RabbitMQ si le besoin devient reel.

---

## 14. Etat cible

L'architecture cible conserve sept microservices reels plutot qu'un decoupage theorique en douze services. Les domaines KYC, reviews, litiges, notifications, stockage et moderation existent, mais ils sont regroupes dans les services deja implementes :

- KYC dans `identity-service` ;
- reviews, litiges, abus et statistiques dans `business-service` ;
- chat, notifications et stockage dans `support-service` ;
- categories, villes et gigs dans `catalog-service`.

Ce regroupement reduit la complexite d'exploitation pour le projet tout en gardant une separation claire des responsabilites.
