# Architecture Microservices — Kam'Etud

Document de référence pour l'équipe (11 membres) — Projet L3 Informatique, Université de Dschang
Encadrant : Dr. AZANGUEZET Benoît

Ce document définit le découpage retenu : **12 microservices de domaine + 1 API Gateway**, ainsi que le modèle de données que chaque service doit implémenter dans sa propre base PostgreSQL.

---

## 1. Vue d'ensemble des microservices

| # | Microservice | Port | Rôle | Tables propres |
|---|---|---|---|---|
| 0 | **Gateway** | 8099 | Routage, JWT, rate limiting, CORS | — |
| 1 | **Identity Service** | 8081 | Auth, profils, rôles | `profiles`, `user_roles` |
| 2 | **KYC Service** | 8082 | Vérification d'identité | `verification_requests` |
| 3 | **Marketplace/Gigs Service** | 8083 | Catalogue de prestations | `gigs`, `categories`, `cities` |
| 4 | **Requests Service** | 8084 | Demandes personnalisées & propositions | `gig_requests`, `request_proposals` |
| 5 | **Orders Service** | 8085 | Cycle de vie des commandes | `orders` |
| 6 | **Payment Service** | 8086 | Escrow, Campay (MTN/Orange), payouts | `payment_transactions` |
| 7 | **Dispute Service** | 8087 | Litiges | `disputes` |
| 8 | **Chat Service** | 8088 | Messagerie temps réel | `chat_messages` |
| 9 | **Reviews Service** | 8089 | Notes & avis | `reviews` |
| 10 | **Notifications Service** | 8090 | Email / SMS / in-app | — (consomme des événements) |
| 11 | **Admin/Moderation Service** | 8091 | Back-office, modération | — (accès transverse) |
| 12 | **Storage Service** | 8092 | Fichiers (avatars, portfolio, livrables, KYC) | `stored_files` |

### Schéma de dépendances simplifié

```text
                         ┌─────────────────┐
                         │   API Gateway   │
                         └────────┬────────┘
                                  │
  ┌──────────┬──────────┬────────┼────────┬──────────┬──────────┐
Identity    KYC     Marketplace Requests  Orders     Chat     Storage
  │          │           │         │        │          │         │
  └────┬─────┴───────────┴────┬────┴───┬────┴──────────┘         │
       │                      │        │                        │
   Reviews                Payment  Disputes                     │
       │                      │        │                        │
       └──────────┬───────────┴────────┘                        │
                  │                                              │
              Event Bus ───────────────────────────────────────┘
                  │
          ┌───────┴────────┐
    Notifications      Admin/Moderation
```

### Hors-scope MVP

Reportées en V1/V2, à mentionner explicitement à l'encadrant pour cadrer les attentes :

- **Search & Recommendation** (indexation, suggestions personnalisées)
- **Reporting & Analytics** (exports, KPIs, tracking d'événements)
- **i18n & Content CMS** (traductions, pages statiques type FAQ/CGU)

---

## 2. Modèle de données par microservice

Chaque microservice possède sa **propre base PostgreSQL** (principe d'isolation des données en architecture microservices). Les relations entre services (ex. `student_id` référençant un utilisateur d'Identity Service) ne sont **pas des FK SQL inter-bases** : elles se vérifient via appel API vers le service propriétaire.

### Enum global

#### `app_role`
Valeurs : `admin`, `moderator`, `user`, `student`, `client`
Utilisé pour les autorisations applicatives (à exposer via JWT claim depuis Identity Service).

---

### 2.1 Identity Service

**`profiles`** — profil applicatif principal

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK |
| `user_id` | uuid | NN, UQ |
| `first_name`, `last_name`, `email` | text | NN, default `''` |
| `phone`, `city`, `avatar_url` | text | nullable |
| `role` | text | NN, default `'client'` |
| `verified`, `banned` | boolean | default `false` |
| `university`, `faculty`, `level`, `bio` | text | nullable |
| `skills`, `availability` | text[] | default `'{}'` |
| `level_badge` | text | default badge débutant |
| `xp`, `next_level_xp`, `completed_jobs`, `review_count` | integer | defaults |
| `rating` | numeric(3,2) | default 0 |
| `response_time` | text | nullable |
| `gps_lat`, `gps_lng` | double precision | nullable |
| `created_at`, `updated_at` | timestamptz | NN, default now() |

Comportements à implémenter :
- Mise à jour automatique de `updated_at` à chaque modification.
- Synchronisation de `user_roles` chaque fois que `profiles.role` change.

**`user_roles`** — rôles applicatifs

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK |
| `user_id` | uuid | NN |
| `role` | app_role | NN |

UQ : `(user_id, role)`.

**Endpoint public** : exposer une projection non sensible du profil (`first_name`, `last_name`, `role`, `city`, `avatar_url`, `verified`, `university`, `faculty`, `level`, `bio`, `skills`, `level_badge`, `xp`, `rating`, `review_count`, `created_at`), filtrée sur les comptes non bannis. Ne jamais exposer `email`, `phone`, ni les champs financiers liés.

---

### 2.2 KYC Service

**`verification_requests`**
- Upload pièce d'identité, carte étudiant, selfie
- Statut géré en `text`, contrôlé côté applicatif (ex. `pending`, `approved`, `rejected`)
- Workflow d'approbation/rejet par un administrateur
- Notification du résultat au demandeur

---

### 2.3 Marketplace/Gigs Service

**`gigs`**

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK |
| `student_id` | uuid | NN |
| `title` | text | NN |
| `description`, `category`, `location`, `badge` | text | nullable |
| `rating` | numeric(3,2) | default 0 |
| `review_count`, `order_count` | integer | default 0 |
| `images` | text[] | default `'{}'` |
| `active` | boolean | default true |
| `gps_lat`, `gps_lng` | double precision | nullable |
| `tier_basique`, `tier_standard`, `tier_premium` | jsonb | NN, default JSON tier |
| `published` | boolean | NN, default false |
| `created_at`, `updated_at` | timestamptz | NN, default now() |

Règle métier critique : **avant de publier un gig (`published = true`), vérifier via appel API à Identity Service que l'étudiant (`student_id`) a bien `verified = true`.** Sinon, rejeter la publication.

**`categories`** : `id`, `name`, `icon`, `active`, `created_at`. Lecture publique, gestion réservée aux admins.

**`cities`** : `id`, `name`, `active`, `created_at`. Lecture publique, gestion réservée aux admins.

---

### 2.4 Requests Service

**`gig_requests`** — demandes ouvertes côté client, avec statut (`text`, indexé).

**`request_proposals`** — propositions soumises par les étudiants, indexées par `request_id`.

Comportement : mise à jour automatique de `updated_at` sur les deux tables.

---

### 2.5 Orders Service

**`orders`**

Champs clés à modéliser :
- Statuts (`text`) : `pending → paid → in_progress → delivered → completed | contested`
- `payment_reference`, `deliverable_url`, `delivered_at`
- `auto_release_at`, `released_at` (libération automatique des fonds après 24h sans validation client)
- `escrow_amount`, `budget`, `payment_status`
- Index recommandés sur `payment_reference` et sur `auto_release_at` (pour la tâche planifiée de libération automatique)

Règles métier à valider côté applicatif selon le rôle de l'appelant :
- **L'étudiant** ne peut pas modifier `budget`, `escrow_amount`, `payment_status`, `client_id`. Il ne peut faire passer `status` qu'à `in_progress` ou `delivered`.
- **Le client** ne peut pas modifier `student_id`, `budget`, `deliverable_url`. Il ne peut faire passer `status` qu'à `completed`, `revision_requested` ou `disputed`.
- Limite de 2 révisions par commande.

---

### 2.6 Payment Service

**`payment_transactions`**
- Intégration Campay (MTN Mobile Money, Orange Money)
- Champs : `order_id`, `reference`, `status` (text)
- Accès en écriture réservé à la logique interne du service (jamais en écriture directe par un client externe)
- Index recommandés sur `order_id` et `reference`

Tâche planifiée à implémenter (`@Scheduled` Spring) : vérifier périodiquement (ex. toutes les 5 minutes) les commandes dont `auto_release_at` est dépassé et `released_at` est nul, puis déclencher la libération automatique des fonds vers l'étudiant.

---

### 2.7 Dispute Service

**`disputes`**
- Ouverture d'un litige liée à une commande passée au statut `contested`
- Espace dédié pour la collecte de preuves (chat, livrables)
- Décision finale : libération des fonds ou remboursement
- Historique des décisions conservé

---

### 2.8 Chat Service

**`chat_messages`**
- Conversation 1-1 entre client et étudiant, rattachée à une commande
- Pièces jointes, indicateurs lus/typing
- **Temps réel à implémenter en WebSocket** (Spring WebFlux) plutôt qu'en polling
- Archivage à la clôture de la commande

---

### 2.9 Reviews Service

**`reviews`**
- Note de 1 à 5 + commentaire
- Un seul avis autorisé par commande (anti-spam)
- Modération des signalements abusifs
- Calcul du rating moyen à répercuter vers Identity Service (mise à jour de `profiles.rating`)

---

### 2.10 Notifications Service

Aucune table obligatoire au démarrage : le service consomme les événements émis par les autres microservices (`order.paid`, `kyc.approved`, `dispute.opened`, `payout.released`, etc.) et déclenche l'envoi (email, SMS, in-app) selon les préférences utilisateur.

Si un historique des envois est souhaité, prévoir une table `notifications` (type, destinataire, statut d'envoi, date).

---

### 2.11 Admin/Moderation Service

Aucune table propre — ce service orchestre des actions transverses sur les autres microservices via leurs API internes : validation KYC, modération des gigs/avis/messages, gestion de `categories`/`cities`, gestion des rôles utilisateurs, configuration système (taux de commission, seuils).

---

### 2.12 Storage Service

Stockage des fichiers (avatars, portfolio, livrables, documents KYC) **directement en base PostgreSQL**, sans dépendance à un service de stockage objet externe.

**`stored_files`**

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK, default `gen_random_uuid()` |
| `owner_id` | uuid | NN |
| `bucket` | text | NN — valeurs : `avatars`, `portfolio`, `deliverables`, `identity-documents` |
| `file_name` | text | NN |
| `mime_type` | text | NN |
| `file_size` | integer | NN — taille en octets |
| `content` | bytea | NN — contenu binaire du fichier |
| `is_public` | boolean | NN, default `false` |
| `is_encrypted` | boolean | NN, default `false` — `true` pour `identity-documents` |
| `created_at` | timestamptz | NN, default now() |

Règles d'accès à implémenter par type de fichier (`bucket`) :

| Bucket | Public | Règle d'accès |
|---|---|---|
| `avatars` | oui | lecture publique, écriture par le propriétaire uniquement |
| `portfolio` | oui | lecture publique, écriture par le propriétaire uniquement |
| `deliverables` | non | lecture par le propriétaire ou un modérateur, écriture par le propriétaire |
| `identity-documents` | non | upload par le propriétaire, lecture par le propriétaire + admin/modérateur, **chiffrement applicatif recommandé avant insertion** |

Recommandation d'implémentation : exposer un seul endpoint générique (`POST /storage/upload`, `GET /storage/{id}`) appelé par tous les autres microservices ayant besoin de fichiers, plutôt que de dupliquer la logique de stockage dans chaque service. Limiter la taille acceptée par fichier (ex. 10–20 Mo) pour préserver les performances de la base.

---

## 3. Logique métier transverse à implémenter

| Comportement | Service responsable | Implémentation suggérée |
|---|---|---|
| Vérification de rôle (`has_role`) | Identity Service | Méthode de service, exposée via JWT claim ou endpoint interne |
| Mise à jour automatique de `updated_at` | Tous | JPA Auditing (`@LastModifiedDate`) |
| Création automatique de profil à l'inscription | Identity Service | Listener sur événement `user.created` |
| Synchronisation rôle ↔ `user_roles` | Identity Service | Déclenchée lors d'un changement de `profiles.role` |
| Blocage de publication de gig si étudiant non vérifié | Marketplace Service | Appel synchrone à Identity Service avant `published = true` |
| Restriction des champs modifiables sur une commande | Orders Service | Validation selon le rôle de l'appelant (étudiant/client/admin) |

---

## 4. Communication inter-services

| Type | Usage | Exemple |
|---|---|---|
| **Synchrone (REST/Feign)** | Vérification immédiate | Orders Service interroge Identity Service pour savoir si l'étudiant est vérifié |
| **Asynchrone (événements)** | Effets de bord, notifications | Payment Service publie `order.paid` → Notifications Service envoie un email |

Infrastructure minimum recommandée (Spring Cloud) :
- **Gateway** (Spring Cloud Gateway) — point d'entrée unique, port 8099
- **Service Discovery** (Eureka) — pour que les 13 services se trouvent sans IP fixes
- **Config Server** — centralisation des `application.yml`
- **Bus d'événements** (RabbitMQ recommandé) — communication asynchrone inter-services

---

## 5. Points de vigilance pour l'équipe

- **Transactions distribuées (Saga Pattern)** : on va éviter qu'un paiement soit validé sans activation de la commande correspondante. Utiliser les événements pour garantir la cohérence finale plutôt que des transactions ACID inter-services.
- **Chatty services** : on va éviter que Marketplace Service appelle Identity Service en boucle pour afficher une liste de gigs. Prévoir une dénormalisation légère ou une agrégation côté Gateway.
- **Storage en PostgreSQL** : choix simplificateur pour l'infrastructure du projet L3 (pas de bucket S3/MinIO à gérer), à présenter à Dr. AZANGUEZET comme un compromis architectural conscient. Surveiller la croissance de la taille de la base à mesure que les livrables/avatars s'accumulent ; prévoir une limite de taille par fichier dès le départ.
- **Répartition par équipe** : chaque microservice peut être attribué à un ou deux membres, avec un responsable identifié par service pour faciliter la revue de code sur GitHub 
PS. (on est foutu, mais bon il y'a de l'espoir pour nous.)
