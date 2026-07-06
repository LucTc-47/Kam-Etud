# Architecture Microservices — Kam'Etud

Découpage cible de la plateforme en microservices indépendants. Chaque service possède sa propre base de données (ou schéma isolé), expose une API REST/gRPC et communique via un bus d'événements (ex. Supabase Realtime, Redis Streams ou RabbitMQ).

---

## 1. 🔐 Auth Service
**Rôle :** Gestion de l'authentification et des sessions.
- Inscription / connexion (Email + OTP, Google OAuth)
- Gestion des tokens JWT (access + refresh)
- Réinitialisation mot de passe
- Rate limiting sur tentatives de connexion
- Intégration Supabase Auth

**Endpoints clés :** `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/otp`, `/auth/logout`

---

## 2. 👤 User & Profile Service
**Rôle :** Gestion des profils étudiants et clients.
- CRUD profils (`profiles`, `student_profiles`)
- Upload avatar, bio, ville, université
- Géolocalisation GPS
- Calcul XP / niveau / badges
- Génération CV PDF
- Vue publique restreinte (PII protégée)

**Endpoints clés :** `/users/:id`, `/profiles/me`, `/profiles/public/:id`, `/cv/generate`

---

## 3. ✅ KYC & Verification Service
**Rôle :** Validation d'identité (badge Vérifié).
- Upload CNI, carte étudiant, selfie
- File d'attente de modération
- Workflow d'approbation/rejet par admin
- Notification du résultat
- Stockage chiffré des documents

**Endpoints clés :** `/kyc/submit`, `/kyc/pending`, `/kyc/:id/approve`, `/kyc/:id/reject`

---

## 4. 🛠️ Gigs & Catalog Service
**Rôle :** Catalogue des prestations étudiantes.
- CRUD gigs (3 tiers : Basic / Standard / Premium)
- Catégories, tags, médias
- Publication conditionnée à KYC validé
- Recherche full-text
- Filtres (catégorie, ville, note, prix)
- Recherche par proximité (Haversine 5–100 km)

**Endpoints clés :** `/gigs`, `/gigs/:id`, `/gigs/search`, `/categories`

---

## 5. 📩 Gig Requests Service
**Rôle :** Demandes personnalisées client → étudiant.
- Création de demandes ouvertes
- Propositions des étudiants
- Sélection du prestataire
- Conversion en commande

**Endpoints clés :** `/requests`, `/requests/:id/proposals`, `/requests/:id/accept`

---

## 6. 🧾 Orders & Workflow Service
**Rôle :** Cycle de vie des commandes.
- Création commande depuis gig ou requête
- Statuts : `pending → paid → in_progress → delivered → completed | contested`
- Limite 2 révisions
- Soumission livrable (`deliverable_url`, `delivered_at`)
- Validation client
- Release automatique fonds après 24 h

**Endpoints clés :** `/orders`, `/orders/:id/deliver`, `/orders/:id/validate`, `/orders/:id/revision`

---

## 7. 💰 Payment & Escrow Service
**Rôle :** Paiement séquestre + Mobile Money (FCFA uniquement).
- Intégration MTN Mobile Money, Orange Money
- Mise sous séquestre (`escrow_amount`)
- Versement étudiant (`payout_status`)
- Calcul commission plateforme
- Webhooks paiement
- Reconciliation comptable

**Endpoints clés :** `/payments/initiate`, `/payments/webhook`, `/escrow/release`, `/payouts/:id`

---

## 8. ⚖️ Dispute Service
**Rôle :** Gestion des litiges.
- Ouverture d'un conflit (statut `contested`)
- Espace modérateur dédié
- Collecte de preuves (chat, livrables)
- Décision : release fonds ou remboursement
- Historique des décisions

**Endpoints clés :** `/disputes`, `/disputes/:id/evidence`, `/disputes/:id/resolve`

---

## 9. 💬 Chat & Messaging Service
**Rôle :** Messagerie temps réel par commande.
- Conversations 1-1 client ↔ étudiant
- Pièces jointes
- Indicateurs lus / typing
- Realtime (WebSocket / Supabase Realtime)
- Archivage à la clôture

**Endpoints clés :** `/chats/:orderId/messages`, WS `/chats/:orderId`

---

## 10. 📦 Storage Service
**Rôle :** Fichiers et médias.
- Buckets : `avatars`, `portfolio`, `deliverables`, `kyc`
- URLs signées
- Antivirus / scan upload
- Politique de rétention

**Endpoints clés :** `/storage/upload`, `/storage/signed-url`

---

## 11. ⭐ Reviews & Ratings Service
**Rôle :** Notes et avis post-commande.
- Note 1–5 + commentaire
- Calcul rating moyen étudiant
- Anti-spam / un avis par commande
- Modération signalements

**Endpoints clés :** `/reviews`, `/reviews/student/:id`, `/reviews/:id/report`

---

## 12. 🔔 Notifications Service
**Rôle :** Notifications multi-canal.
- In-app (Realtime)
- Email (transactionnel)
- SMS / Push (mobile)
- Préférences utilisateur
- Templates FR/EN

**Endpoints clés :** `/notifications`, `/notifications/preferences`

---

## 13. 🔎 Search & Recommendation Service
**Rôle :** Découverte intelligente.
- Indexation gigs et étudiants (Meilisearch / Postgres FTS)
- Recommandations personnalisées
- Trending par ville / catégorie
- Suggestions étudiants similaires

**Endpoints clés :** `/search`, `/recommendations/:userId`

---

## 14. 🛡️ Admin & Moderation Service
**Rôle :** Back-office admin et modérateurs.
- Validation identités
- Modération gigs / avis / chats
- Configuration système (commission, seuils)
- Gestion des rôles (`user_roles`)
- Tableau de bord opérationnel

**Endpoints clés :** `/admin/*`, `/moderation/*`

---

## 15. 📊 Reporting & Analytics Service
**Rôle :** Indicateurs métier.
- Exports PDF / Excel
- KPIs : GMV, take rate, NPS, conversion
- Rapports admin (financier, opérationnel)
- Event tracking utilisateur

**Endpoints clés :** `/reports/export`, `/analytics/events`

---

## 16. 🌍 i18n & Content Service
**Rôle :** Traductions et contenu CMS.
- Bundles FR / EN
- Pages statiques (CGU, FAQ, Aide)
- Bannières et promos

**Endpoints clés :** `/i18n/:lang`, `/cms/pages/:slug`

---

## 17. 🚦 API Gateway
**Rôle :** Point d'entrée unique.
- Routage vers microservices
- Auth JWT centralisée
- Rate limiting global
- CORS, compression, logs
- Versioning API (`/v1`, `/v2`)

---

## 18. 📡 Event Bus
**Rôle :** Communication asynchrone inter-services.
- Événements : `order.paid`, `kyc.approved`, `dispute.opened`, `payout.released`...
- Supabase Realtime ou Redis Streams / RabbitMQ
- Garantie at-least-once

---

## Schéma de dépendances

```text
                       ┌─────────────────┐
                       │   API Gateway   │
                       └────────┬────────┘
                                │
   ┌────────────┬───────────────┼──────────────┬────────────┐
   │            │               │              │            │
 Auth        Users/KYC        Gigs          Orders        Chat
   │            │               │              │            │
   └────────────┴───────┬───────┴──────┬───────┴────────────┘
                       │              │
                  Payments        Notifications
                       │              │
                       └──────┬───────┘
                              │
                          Event Bus
                              │
              ┌───────────────┼────────────────┐
           Disputes        Reviews          Analytics
```

---

## Priorisation MVP → V1

| Phase | Services obligatoires |
|-------|----------------------|
| **MVP** | Auth, Users, Gigs, Orders, Payments, Chat, Storage, Notifications, Admin |
| **V1**  | KYC, Disputes, Reviews, Search, Reporting |
| **V2**  | Recommendation, i18n CMS avancé, Event Bus dédié |
