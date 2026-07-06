#  Identity & Auth Service (Darwin & Ericka)

## Synthèse GitHub — 6 juillet 2026

### Description

Identity Service gère l'authentification, les JWT, les profils publics/privés, les rôles et la vérification KYC des étudiants. Il écoute sur `8081` et utilise PostgreSQL `identity_db`.

### Changements

- Remplacement des anciens usages Supabase par des endpoints Spring Boot sécurisés.
- Normalisation des emails, inscription transactionnelle et messages d'erreur lisibles.
- Bannissement effectif via `users.enabled=false`, en plus du champ profil `banned`.
- Synchronisation avec Catalog pour désactiver les gigs d'un étudiant banni.

### Ajouts

- JWT access/refresh avec `userId`, `role` et `tokenType`.
- Endpoints KYC pour soumission étudiant et décision admin.
- Endpoint interne `/internal/users/{id}/access` pour la révocation côté Gateway.
- Endpoint interne `/internal/users/{id}/payout-profile` pour le téléphone de retrait utilisé par Payment Service.

### Fonctionnement

Endpoints principaux : `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/profiles/me`, `/api/profiles/{userId}`, `/api/verifications`, `/api/admin/verifications/**` et `/api/admin/profiles/**`. La Gateway injecte l'identité dans les en-têtes internes, puis Identity vérifie encore les droits côté service.

### Problèmes rencontrés

- Modifier seulement `profiles.banned` ne bloquait ni login ni anciens JWT; `users.enabled` et le contrôle Gateway ont corrigé cela.
- Le profil public masque le téléphone; Payment Service a donc reçu une route interne dédiée au payout.
- L'inscription pouvait créer des doublons difficiles à comprendre; la transaction et les `409 Conflict` clarifient le parcours.

## Nettoyage des diagnostics IDE — 6 juillet 2026

Les anciens imports Lombok et `HttpStatus`, devenus inutiles dans `RestCatalogClient`, sont conservés en commentaires afin de supprimer les avertissements Java sans masquer l'historique à l'équipe.

## Profil de paiement interne — 6 juillet 2026

- `GET /internal/users/{userId}/payout-profile` retourne uniquement le téléphone de retrait.
- La route exige `X-Internal-Service-Token` et n'est pas exposée par la Gateway.
- Le profil public continue de masquer l'e-mail, le téléphone et le bannissement.

Pourquoi : Payment Service utilisait auparavant le profil public, recevait toujours `phone=null` et refusait le paiement même lorsque l'étudiant avait configuré un numéro valide.

##  Description
Gère l'authentification, les profils et la vérification KYC. Le JSON conserve les noms attendus par le frontend pendant la sortie progressive de Supabase.

## Bannissement effectif — 5 juillet 2026

- `users.enabled` permet à Spring Security de refuser la connexion, le refresh et les JWT d'un compte suspendu.
- Au démarrage, les anciens profils déjà bannis sont reportés dans `users.enabled=false`.
- `GET /internal/users/{id}/access`, protégé par le jeton inter-service, permet à la Gateway d'invalider les tokens déjà émis.
- Bannir un étudiant dépublie et désactive d'abord tous ses gigs dans Catalog.
- Réactiver le compte ne republie jamais automatiquement ces gigs.
- Le starter REST Client a été ajouté pour l'appel Identity → Catalog sous Spring Boot 4.1.0.

Pourquoi : modifier uniquement `profiles.banned` laissait fonctionner le login, les anciens JWT et les gigs existants.

##  Schéma de Base de Données (PostgreSQL)

### Table `users` (Interne)
- `id`: UUID (PK)
- `email`: String (Unique)
- `password`: String (Haché)
- `role`: Enum (admin, moderator, user, student, client)

### Table `profiles` (Compatible Frontend)
- `id`: UUID (PK)
- `user_id`: UUID (FK)
- `first_name`: String
- `last_name`: String
- `email`: String
- `phone`: String
- `avatar_url`: String
- `bio`: Text
- `city`: String
- `skills`: List<String>
- `rating`: Float
- `role`: String (admin, moderator, user, student, client)
- `verified`: Boolean (Anciennement is_verified)
- `banned`: Boolean
- `university`, `faculty`, `level`: String
- `created_at`: Timestamp
- `updated_at`: Timestamp

## Modifications du 4 juillet 2026

- Le nom Spring est harmonisé de `auth-service` vers `identity-service`.
- La base par défaut devient `identity_db` sur le port `5431`, conformément à `docker-compose.yml`.
- Le port HTTP reste `8081`, mais peut être remplacé avec `SERVER_PORT`.
- `DB_URL`, `DB_USERNAME` et `DB_PASSWORD` permettent une configuration sans modifier le code.

L'ancienne configuration de base est conservée en commentaire dans `application.yaml` afin que l'équipe voie précisément la correction.

### Migration Spring Boot 4.1.0 et intégration Auth/KYC

- Parent Maven migré de Boot `3.5.15` vers `4.1.0`; ancienne version commentée.
- `spring-boot-starter-web` remplacé par `spring-boot-starter-webmvc` et starters de test Boot 4 ajoutés.
- `DaoAuthenticationProvider` adapté à Spring Security 7.
- JWT enrichi avec `userId`, `role` et `tokenType` (`access` ou `refresh`).
- Contrôle explicite du type lors du rafraîchissement du token.
- Refresh token déplacé du query parameter vers le corps JSON pour éviter sa présence dans les logs d'URL.
- Refus de créer un compte `admin` ou `moderator` via l'inscription publique.
- Masquage de l'e-mail, du téléphone et du statut de bannissement dans le profil public.
- Inscription enrichie avec les champs de profil utilisés par React.
- Modification du profil courant sécurisée par `/api/profiles/me`.
- Administration des comptes par `/api/admin/profiles`.
- Workflow KYC ajouté dans `verification_requests`; une approbation met `verified=true` dans la même transaction.
- Endpoint `/api/students/{studentId}/status` ajouté pour le Catalog Service.
- H2 ajouté uniquement pour les tests afin de ne pas dépendre de PostgreSQL pendant `mvn test`.

Pourquoi : le navigateur ne doit plus lire ou modifier directement les tables d'identité. Les identifiants et rôles viennent maintenant du JWT valide et non du corps JSON.

## Endpoints ajoutés

| Méthode | Chemin | Accès |
| --- | --- | --- |
| GET/PUT | `/api/profiles/me` | utilisateur connecté |
| GET | `/api/profiles/{userId}` | public, lecture |
| POST/GET | `/api/verifications`, `/api/verifications/me` | étudiant connecté |
| GET/PATCH | `/api/admin/verifications/**` | admin |
| GET/PATCH | `/api/admin/profiles/**` | admin |
| GET | `/api/students/{studentId}/status` | inter-service |

### Correction inscription et messages d'erreur — 5 juillet 2026

- L'inscription est transactionnelle : `users` et `profiles` sont créés ensemble ou annulés ensemble.
- L'email est normalisé (`trim` et minuscules) à l'inscription et à la connexion.
- Un email déjà utilisé renvoie `409 Conflict` avec un message lisible au lieu d'une erreur `500` générique.
- Les validations renvoient `400` et les mauvais identifiants `401` avec leur cause fonctionnelle.
- Le frontend affiche le message backend réel et signale une confirmation de mot de passe différente.

Pourquoi : une première inscription pouvait réussir dans la base alors que l'interface, après une erreur réseau/CORS, affichait un échec. Une nouvelle tentative rencontrait ensuite le doublon sans expliquer que le compte existait déjà.

---

##  Instructions
1. **Compatibilité :** Utilisez `verified` (au lieu de `is_verified`) pour que le badge de vérification s'affiche sans changer le React.
2. **DTO :** Le JSON de réponse du profil doit être identique à celui de Supabase.
