# KAM'ETUD

## Documentation : Microservice d'identité et d'authentification

**« auth-service »**

Université de Dschang — Département Informatique
*Projet Kam'Étud : Plateforme freelance étudiant*

Auteurs : Tiagno Foko Darwin & Meche Domkam Jeanne E.

---

## Sommaire

1. [Présentation du microservice](#1-présentation-du-microservice)
2. [Architecture](#2-architecture)
   - 2.1 [Structure du projet](#21-structure-du-projet)
   - 2.2 [Technologies utilisées](#22-technologies-utilisées)
3. [Modèle et schéma de base de données](#3-modèle-et-schéma-de-base-de-données)
   - 3.1 [Table users (interne)](#31-table-users-interne)
   - 3.2 [Table profiles (compatible frontend)](#32-table-profiles-compatible-frontend)
4. [Sécurité et flux d'authentification](#4-sécurité-et-flux-dauthentification)
   - 4.1 [Mécanisme JWT](#41-mécanisme-jwt)
   - 4.2 [Inscription — POST /api/auth/register](#42-inscription--post-apiauthregister)
   - 4.3 [Connexion — POST /api/auth/login](#43-connexion--post-apiauthlogin)
   - 4.4 [Validation du token sur les requêtes suivantes (JwtAuthFilter)](#44-validation-du-token-sur-les-requêtes-suivantes-jwtauthfilter)
   - 4.5 [Rafraîchissement — POST /api/auth/refresh](#45-rafraîchissement--post-apiauthrefresh)
   - 4.6 [Le bug des 401 systématiques — post-mortem](#46-le-bug-des-401-systématiques--post-mortem)
   - 4.7 [Routes et niveaux d'accès](#47-routes-et-niveaux-daccès)
   - 4.8 [Rôles disponibles](#48-rôles-disponibles)
5. [API REST](#5-api-rest)
   - 5.1 [Authentification — /api/auth/**](#51-authentification--apiauth)
   - 5.2 [Profils — /profiles/**](#52-profils--profiles)
6. [Tests](#6-tests)
   - 6.1 [Structure des tests](#61-structure-des-tests)
   - 6.2 [Lancer les tests](#62-lancer-les-tests)
7. [Déploiement](#7-déploiement)
   - 7.1 [Variables d'environnement](#71-variables-denvironnement)
   - 7.2 [Docker](#72-docker)
   - 7.3 [Build Maven](#73-build-maven)
   - 7.4 [Démarrage en environnement de développement](#74-démarrage-en-environnement-de-développement)
8. [Compatibilité frontend](#8-compatibilité-frontend)

---

## 1. Présentation du microservice

Auth-service est un microservice Spring Boot responsable de l'authentification des utilisateurs et de la gestion de leurs profils au sein de la plateforme Kam'Étud. Il opère de manière autonome sur le port 8081 et expose une API REST consommée par les autres microservices (via OpenFeign), par l'API Gateway, et par le frontend React.

| **Propriété** | **Valeur** |
|---|---|
| Nom | auth-service |
| Port | 8081 |
| Framework | Spring Boot 3.5.x |
| Java | 26 |
| Base de données | PostgreSQL |
| Authentification | JWT (JJWT 0.12.3) |
| Sécurité | Spring Security + BCrypt |

---

## 2. Architecture

### 2.1 Structure du projet

```
com.darwin.authservice/
├── controller/
│   ├── AuthController.java      (routes /api/auth/**)
│   └── ProfileController.java   (routes /profiles/**)
├── service/
│   └── AuthService.java
├── security/
│   ├── JwtAuthFilter.java
│   ├── JwtUtils.java
│   └── SecurityConfig.java
├── entity/
│   ├── User.java
│   ├── Profile.java
│   └── Role.java                (enum)
├── dto/
│   ├── AuthResponse.java
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   └── ProfileResponse.java
└── repository/
    ├── UserRepository.java
    └── ProfileRepository.java
```

### 2.2 Technologies utilisées

| **Technologie** | **Version** | **Usage** |
|---|---|---|
| Spring Boot | 3.5.x | Framework principal |
| Java | 26 | Langage et plateforme d'exécution |
| Spring Security | 6.x | Sécurité et filtres |
| Spring Data JPA | 3.x | Accès base de données |
| JJWT | 0.12.3 | Génération et validation des JWT |
| BCrypt | — | Hachage des mots de passe |
| Lombok | latest | Réduction du boilerplate |
| PostgreSQL (driver) | 42.7.3 | Driver JDBC |
| JUnit 5 + Mockito | — | Tests unitaires et d'intégration |

---

## 3. Modèle et schéma de base de données

Le service gère deux entités JPA, stockées dans deux tables distinctes d'une base PostgreSQL dédiée (principe d'isolation des données par microservice).

### 3.1 Table users (interne)

Stocke les informations d'authentification. Cette table est interne et n'est jamais exposée directement au frontend. L'entité `User` implémente directement l'interface `UserDetails` de Spring Security ; aucune classe intermédiaire de type `CustomUserDetails` n'est nécessaire.

| **Colonne** | **Type** | **Contraintes** | **Description** |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Identifiant unique auto-généré |
| email | VARCHAR | UNIQUE, NOT NULL | Adresse email — sert d'identifiant de connexion |
| password | VARCHAR | NOT NULL | Mot de passe haché (BCrypt) — jamais stocké en clair |
| role | VARCHAR | NOT NULL | Rôle : admin, moderator, user, student, client |

### 3.2 Table profiles (compatible frontend)

Stocke les données de profil public, pensées pour rester compatibles avec le schéma Supabase utilisé historiquement par le frontend React : tous les noms de colonnes respectent le snake_case attendu côté client.

| **Colonne** | **Type** | **Contraintes** | **Description** |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Identifiant unique auto-généré |
| user_id | UUID | FK users.id, UNIQUE | Référence 1-1 vers la table users |
| first_name | VARCHAR | — | Prénom |
| last_name | VARCHAR | — | Nom de famille |
| email | VARCHAR | — | Email (dupliqué pour compatibilité d'affichage) |
| phone | VARCHAR | — | Numéro de téléphone |
| avatar_url | VARCHAR | — | URL de l'avatar |
| bio | TEXT | — | Biographie / description libre |
| city | VARCHAR | — | Ville (utile pour le matching local à Dschang) |
| skills | VARCHAR[] | — | Liste de compétences (table associée profile_skills) |
| rating | FLOAT | — | Note moyenne |
| role | VARCHAR | — | Rôle copié en minuscules (ex. "student") |
| verified | BOOLEAN | DEFAULT false | Badge de vérification du profil |
| created_at | TIMESTAMP | NOT NULL | Date de création (auto, @CreationTimestamp) |

---

## 4. Sécurité et flux d'authentification

C'est la partie la plus importante du service : elle conditionne la sécurité de toute la plateforme.

### 4.1 Mécanisme JWT

Le service utilise des JSON Web Tokens pour une authentification sans état (stateless). Chaque requête authentifiée doit porter le token dans l'en-tête `Authorization`.

| **Élément** | **Description** |
|---|---|
| Algorithme | HS256 (HMAC SHA-256) |
| Access token | Durée courte — utilisé pour les appels API (24h) |
| Refresh token | Durée longue — utilisé pour renouveler l'access token (7 jours) |
| Header | `Authorization: Bearer {token}` |
| Hachage mot de passe | BCryptPasswordEncoder |

### 4.2 Inscription — POST /api/auth/register

1. Le client envoie `email`, `password` et `role` (optionnel, USER par défaut).
2. Vérification qu'aucun compte n'existe déjà avec cet email.
3. Le mot de passe est haché avec BCrypt — jamais stocké en clair.
4. L'entité `User` est créée et sauvegardée.
5. Un `Profile` est automatiquement créé en parallèle, lié via `user_id`, avec le rôle copié en minuscules.
6. Un access token et un refresh token sont générés et renvoyés avec le profil complet.

### 4.3 Connexion — POST /api/auth/login

1. Le client envoie `email` et `password`.
2. `AuthenticationManager.authenticate()` délègue au `DaoAuthenticationProvider`, qui charge le `UserDetails` via `UserDetailsService` et compare le mot de passe haché.
3. En cas d'échec, Spring Security lève automatiquement une exception — aucune vérification manuelle du mot de passe dans le code applicatif.
4. En cas de succès, un nouveau couple access/refresh token est généré et renvoyé avec le profil.

### 4.4 Validation du token sur les requêtes suivantes (JwtAuthFilter)

Pour chaque requête entrante (hors routes publiques), `JwtAuthFilter` — un `OncePerRequestFilter` — s'exécute avant le filtre d'authentification standard :

1. Lecture de l'en-tête `Authorization`. Absent ou sans préfixe "Bearer " → la requête continue sans authentification.
2. Sinon, extraction du JWT et décodage de l'email (sujet du token) via `JwtUtils`.
3. Si l'email est valide et qu'aucune authentification n'est déjà présente, rechargement de l'utilisateur via `UserDetailsService`.
4. Validation du token : signature + expiration.
5. Si valide, un `UsernamePasswordAuthenticationToken` est placé dans le `SecurityContextHolder`.

### 4.5 Rafraîchissement — POST /api/auth/refresh

Le client envoie son refresh token ; le service en extrait l'email, recharge le `User`, vérifie la validité du token, puis génère une nouvelle paire de tokens.

### 4.6 Le bug des 401 systématiques — post-mortem

Lors du développement initial, toutes les requêtes — y compris vers des routes censées être publiques — renvoyaient `401 Unauthorized`.

| **Aspect** | **Détails** |
|---|---|
| Symptôme | 401 sur toutes les routes, y compris /api/auth/register et /api/auth/login |
| Cause | SecurityConfig se trouvait hors du périmètre du @ComponentScan implicite (mauvais package) |
| Conséquence | Spring démarrait avec sa configuration de sécurité par défaut, ignorant silencieusement le SecurityConfig personnalisé |
| Résolution | Déplacement de SecurityConfig sous com.darwin.authservice.security, sous-package du package racine |

### 4.7 Routes et niveaux d'accès

| **Route** | **Accès** | **Description** |
|---|---|---|
| /api/auth/** | Public | register, login, validate, refresh |
| /profiles/** | Public | GET et PUT profil |
| /internal/** | Public | Endpoints internes inter-services |
| Tout le reste | Authentifié | JWT requis |

### 4.8 Rôles disponibles

| **Rôle** | **Description** |
|---|---|
| admin | Administrateur de la plateforme |
| moderator | Modérateur de contenu |
| user | Utilisateur standard |
| student | Étudiant |
| client | Client (particulier ou entreprise) |

---

## 5. API REST

### 5.1 Authentification — /api/auth/**

Ces endpoints sont publics (aucun token requis).

#### POST /api/auth/register

Crée un nouvel utilisateur et son profil associé. Retourne un JWT et le profil complet.

| **Champ** | **Valeur** |
|---|---|
| URL | POST http://localhost:8081/api/auth/register |
| Auth | Aucune |
| Content-Type | application/json |
| Statut succès | 201 Created |

#### POST /api/auth/login

Authentifie un utilisateur existant. Retourne un JWT et le profil complet.

| **Champ** | **Valeur** |
|---|---|
| URL | POST http://localhost:8081/api/auth/login |
| Auth | Aucune |
| Content-Type | application/json |
| Statut succès | 200 OK |

#### GET /api/auth/validate

Valide un token JWT. Retourne `true` ou `false` (jamais d'erreur 500).

| **Champ** | **Valeur** |
|---|---|
| URL | GET http://localhost:8081/api/auth/validate?token={jwt} |
| Auth | Aucune |
| Statut succès | 200 OK |
| Réponse | true / false |

#### POST /api/auth/refresh

Génère un nouveau couple de tokens à partir d'un refresh token valide.

| **Champ** | **Valeur** |
|---|---|
| URL | POST http://localhost:8081/api/auth/refresh?refreshToken={token} |
| Auth | Aucune |
| Statut succès | 200 OK |

### 5.2 Profils — /profiles/**

Ces endpoints sont publics (accès libre — voir la remarque de sécurité au §4.7).

#### GET /profiles/{id}

Récupère le profil d'un utilisateur par son identifiant de profil.

| **Champ** | **Valeur** |
|---|---|
| URL | GET http://localhost:8081/profiles/{profile_id} |
| Auth | Aucune |
| Statut succès | 200 OK |

#### PUT /profiles/{id}

Met à jour les champs modifiables d'un profil.

| **Champ** | **Valeur** |
|---|---|
| URL | PUT http://localhost:8081/profiles/{profile_id} |
| Auth | Aucune (cf. remarque de sécurité §4.7) |
| Content-Type | application/json |
| Statut succès | 200 OK |

---

## 6. Tests

### 6.1 Structure des tests

| **Fichier** | **Type** | **Ce qui est testé** |
|---|---|---|
| AuthServiceTest.java | Unitaire (Mockito) | register, login, validate, refresh |
| AuthControllerTest.java | Intégration (MockMvc) | Routes HTTP et codes de réponse |
| AuthServiceApplicationTests.java | Contexte Spring | Chargement correct du contexte applicatif |

### 6.2 Lancer les tests

Depuis le terminal, à la racine du projet :

```bash
./mvnw test
```

---

## 7. Déploiement

### 7.1 Variables d'environnement

| **Variable** | **Description** | **Exemple** |
|---|---|---|
| SPRING_DATASOURCE_URL | URL de connexion PostgreSQL | jdbc:postgresql://localhost:5433/auth_db |
| SPRING_DATASOURCE_USERNAME | Utilisateur de la base | postgres |
| SPRING_DATASOURCE_PASSWORD | Mot de passe de la base | secret |
| JWT_SECRET | Clé secrète de signature JWT | ma-cle-secrete-256bits |
| SERVER_PORT | Port d'écoute du service | 8081 |

### 7.2 Docker

Le projet contient un `docker-compose.yml` permettant de lancer le service avec sa base de données PostgreSQL dédiée (et pgAdmin pour l'inspection en développement) :

```bash
docker compose up -d
```

### 7.3 Build Maven

```bash
./mvnw clean package -DskipTests
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

### 7.4 Démarrage en environnement de développement

1. Lancer la base : `docker compose up -d` (depuis le dossier du service).
2. Démarrer le microservice : `./mvnw spring-boot:run`, ou exécuter `AuthServiceApplication` depuis l'IDE.
3. Vérifier l'enregistrement auprès du serveur Eureka avant de router du trafic via la Gateway.

---

## 8. Compatibilité frontend

Le microservice est conçu pour rester compatible avec le frontend React existant. Points clés de compatibilité :

- Tous les noms de champs JSON utilisent le snake_case (`first_name`, `avatar_url`, `created_at`) ;
- Le champ `verified` est utilisé à la place de `is_verified` pour le badge de vérification ;
- Les rôles sont retournés en minuscules (`user`, `admin`, etc.), comme dans Supabase ;
- La réponse d'authentification inclut le profil complet, au format identique au schéma Supabase ;
- Les UUID sont utilisés comme identifiants pour toutes les entités.
