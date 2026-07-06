#  Catalog Service (Noel & Steve)

## Synthèse GitHub — 6 juillet 2026

### Description

Catalog Service gère les services publiés par les étudiants (`gigs`), les catégories et les villes visibles sur `/services`. Il écoute sur `8083` et utilise PostgreSQL `catalog_db`.

### Changements

- Les gigs sont créés, modifiés, publiés, désactivés et supprimés via le backend, avec propriétaire dérivé du JWT.
- Les catégories et villes viennent de PostgreSQL au lieu d'être codées côté frontend.
- Les notes affichées sur `/services` sont synchronisées depuis Business Service après chaque avis ou modération.
- Les gigs d'un étudiant banni sont désactivés via route interne.

### Ajouts

- `PUT /api/gigs/{gigId}` pour modifier un gig existant depuis `/mes-gigs`.
- `PATCH /api/gigs/internal/{gigId}/rating` pour recevoir la moyenne et le nombre d'avis visibles.
- Seed initial de catégories et villes camerounaises si les tables sont vides.
- Contrôle KYC auprès d'Identity avant publication d'un gig.

### Fonctionnement

Endpoints principaux : `GET /api/gigs`, `GET /api/gigs/{id}`, `GET /api/gigs/mine`, `POST /api/gigs`, `PUT /api/gigs/{id}`, `PATCH /api/gigs/{id}/publish`, `PATCH /api/gigs/{id}/active`, `GET /api/categories` et `GET /api/cities`. Catalog dépend d'Identity pour le statut étudiant et de Business pour les statistiques d'avis.

### Problèmes rencontrés

- PostgreSQL recevait certains filtres `null` comme `bytea`, ce qui cassait `/api/gigs`; la recherche normalise maintenant les filtres vides.
- Les avis étaient corrects sur `/profil` mais pas sur `/services`; une propagation vers Catalog a été ajoutée.
- Le bouton modifier existait côté UI mais aucun endpoint général n'existait; `PUT /api/gigs/{id}` corrige ce manque.

## Synchronisation des notes — 6 juillet 2026

- `PATCH /api/gigs/internal/{gigId}/rating` reçoit la moyenne et le nombre
  d'avis visibles calculés par Business Service.
- La route exige `X-Internal-Service-Token`; elle n'est pas utilisable comme
  endpoint public depuis le frontend.
- Catalog persiste ces valeurs dans le gig afin que `/api/gigs`, et donc
  `/services`, renvoie immédiatement la note correcte.

Pourquoi : les avis étaient stockés dans Business Service tandis que les
cartes de services lisaient encore les compteurs initialisés à zéro dans
Catalog Service.

## Modification des gigs existants — 6 juillet 2026

- `PUT /api/gigs/{gigId}` modifie le titre, la description, la catégorie, la ville, les trois paliers, les images et la publication.
- Le rôle `STUDENT` et la propriété du gig sont vérifiés côté serveur; un autre étudiant reçoit `403`.
- Une publication depuis le formulaire conserve la vérification KYC.
- Le frontend ouvre le même formulaire prérempli depuis le bouton « Modifier » de `/mes-gigs`.

Pourquoi : l'ancien bouton Edit n'avait aucun gestionnaire et Catalog ne possédait aucun endpoint de mise à jour générale.

## Visibilité des gigs publics — 6 juillet 2026

- La recherche remplace les paramètres `null` par des chaînes vides typées.
- Le filtre PostgreSQL continue de retourner uniquement `published=true` et `active=true`.
- Un test HTTP couvre maintenant `GET /api/gigs` sans aucun filtre.

Pourquoi : Hibernate 7 liait les filtres `null` comme `bytea`; PostgreSQL tentait alors `lower(bytea)` et renvoyait une erreur 500. Le frontend recevait donc une liste inutilisable même après une publication réussie.

##  Description
Gère les Gigs. **Important :** Le frontend s'attend à ce que les paliers (Tiers) soient inclus dans l'objet Gig sous forme de JSON.

## Désactivation après bannissement — 5 juillet 2026

- `PATCH /api/gigs/internal/students/{studentId}/deactivate` exige le jeton inter-service et n'est pas exposé par la Gateway.
- Une mise à jour SQL unique place tous les gigs concernés à `active=false` et `published=false`.
- La réactivation du compte ne remet aucun gig en ligne silencieusement.

Pourquoi : un étudiant banni ne doit plus rester visible ni recevoir de nouvelles commandes.

## ️ Schéma de Base de Données (PostgreSQL)

### Table `gigs`
- `id`: UUID (PK)
- `title`: String
- `description`: Text
- `category`: String (Le nom de la catégorie)
- `location`: String (La ville)
- `student_id`: UUID
- `student_name`: nom public copié depuis Identity à la création
- `rating`: Float
- `review_count`, `order_count`, `badge`, `active`: état d'affichage
- `images`: List<String> (Array d'URLs)
- `tier_basique`: JSON (Détails du pack basique)
- `tier_standard`: JSON (Détails du pack standard)
- `tier_premium`: JSON (Détails du pack premium)
- `published`: Boolean

## Modifications du 4 juillet 2026

- La connexion PostgreSQL est alignée sur `catalog-db` du `docker-compose.yml`.
- Les identifiants de base en dur sont remplacés par `DB_URL`, `DB_USERNAME` et `DB_PASSWORD` avec des valeurs locales par défaut.
- Le port `8083` devient surchargeable avec `SERVER_PORT`.
- L'adresse de l'Identity Service devient configurable avec `IDENTITY_SERVICE_URL`.

L'ancienne configuration est conservée en commentaire dans `application.yaml` pour rendre la modification visible à l'équipe.

### Migration Spring Boot 4.1.0

- Parent Maven migré vers `4.1.0`; ancienne version commentée.
- Starter web historique commenté et remplacé par `spring-boot-starter-webmvc`.
- Imports de tests MVC et Jackson adaptés à la modularisation Boot 4.
- Tests Maven validés sous Java 21.

Pourquoi : tous les microservices doivent utiliser la même génération Spring afin d'éviter des contrats et dépendances incompatibles.

### Intégration Catalogue au frontend

- Ajout des tables, repositories et endpoints `/api/categories` et `/api/cities`.
- Ajout d'un jeu initial de catégories et villes camerounaises lorsque les tables sont vides.
- Recherche publique des gigs par texte, catégorie et ville; seuls les gigs publiés et actifs sont retournés.
- Ajout de `/api/gigs/mine`, publication/dépublication, activation/désactivation et suppression.
- Le champ historique `studentId` du JSON est conservé mais ignoré. Le propriétaire vient de `X-User-Id`, construit par la Gateway depuis le JWT.
- Vérification du rôle `STUDENT` et de la propriété du gig avant chaque modification.
- Vérification KYC réelle via `/api/students/{id}/status` avant publication.
- Nom public étudiant chargé depuis Identity lors de la création du gig.
- Paliers JSON alignés sur React : la propriété publique est `name`; l'ancien champ Java `title` et son constructeur restent conservés pour compatibilité.
- Tests HTTP ajoutés pour l'identité JWT, le KYC, les filtres, les villes inactives et les mutations admin.

Pourquoi : React ne doit plus accéder aux tables Supabase ni choisir lui-même le propriétaire d'un gig.

## API actuelle

| Méthode | Chemin | Accès |
| --- | --- | --- |
| GET | `/api/gigs`, `/api/gigs/{id}` | public |
| GET | `/api/gigs/mine` | étudiant connecté |
| POST | `/api/gigs` | étudiant connecté |
| PATCH | `/api/gigs/{id}/publish`, `/api/gigs/{id}/active` | propriétaire |
| DELETE | `/api/gigs/{id}` | propriétaire |
| GET | `/api/categories`, `/api/cities` | public |
| POST/PATCH/DELETE | `/api/categories/**`, `/api/cities/**` | admin |

---

##  Instructions
1. **Paliers (Tiers) :** Au lieu d'une table séparée complexe, stockez les 3 tiers dans des colonnes JSONB (`tier_basique`, `tier_standard`, `tier_premium`) comme dans Supabase. Cela simplifie énormément le code Frontend.
2. **Recherche :** Implémentez la recherche par `location` et `category`.

---

