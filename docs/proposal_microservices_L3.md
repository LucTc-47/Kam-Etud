# Proposition d'Architecture Microservices — Niveau Étudiant (L3)

Après analyse du document `microservices.md` existant, je propose une version simplifiée mais tout aussi rigoureuse, adaptée à un projet étudiant de niveau Licence 3.

##  Pourquoi simplifier ?
Le découpage initial en 18 services est trop complexe pour une petite équipe. Il impose une charge de maintenance (Docker, networking, CI/CD, monitoring) qui risque de paralyser le développement des fonctionnalités réelles. Une architecture à **4 microservices cœurs** permet d'apprendre les concepts fondamentaux (Bounded Contexts, communication asynchrone, isolation des DB) sans tomber dans l'over-engineering.

---

##  Découpage Proposé (4 Services)

### 1.  Identity Service (Le Pivot)
Regroupe tout ce qui touche à l'utilisateur et sa légitimité.
- **Rôle :** Auth, Gestion des Profils, KYC.
- **Entités :** `profiles`, `user_roles`, `verification_requests`.
- **Technologies :** Spring Boot, Spring Security, JWT, PostgreSQL.

### 2.  Marketplace Service (Le Cœur Métier)
Regroupe l'offre et la demande.
- **Rôle :** Catalogue de Gigs, Appels d'offres (Requests), Propositions des étudiants, Recherche.
- **Entités :** `gigs`, `categories`, `cities`, `gig_requests`, `request_proposals`.
- **Technologies :** Spring Boot, PostgreSQL, (Optionnel) Meilisearch pour la recherche.

### 3.  Transaction Service (La Valeur)
Gère le flux financier et contractuel. C'est le service le plus critique.
- **Rôle :** Cycle de vie des commandes (`Orders`), Escrow (Séquestre), Intégration Campay (Paiement), Litiges (`Disputes`).
- **Entités :** `orders`, `payment_transactions`, `disputes`.
- **Technologies :** Spring Boot, PostgreSQL, Redis (pour les verrous/timeouts de 72h).

### 4.  Communication Service (L'Interactivité)
Gère l'interaction en temps réel.
- **Rôle :** Messagerie (Chat), Notifications (In-app, Email, SMS).
- **Entités :** `chat_messages`.
- **Technologies :** Spring Boot WebFlux (pour les WebSockets), RabbitMQ ou Kafka pour consommer les événements des autres services.

---

## 📡 Communication Inter-Services

| Type | Usage | Exemple |
| :--- | :--- | :--- |
| **Synchrone (Feign/REST)** | Vérification immédiate | `OrderService` demande à `IdentityService` si l'étudiant est vérifié. |
| **Asynchrone (RabbitMQ)** | Effets de bord / Notifications | `TransactionService` publie `OrderPaid` -> `CommunicationService` envoie un email. |

---

##  Infrastructure Minimum (Spring Cloud)

1.  **API Gateway (Spring Cloud Gateway) :** Point d'entrée unique. Gère le routage et le filtrage JWT.
2.  **Service Discovery (Eureka) :** Pour que les services se trouvent sans IP fixes.
3.  **Config Server :** Centralisation des fichiers `application.yml`.

##  Risques à surveiller
- **Transactions Distribuées (Saga Pattern) :** Éviter qu'un paiement soit validé sans que la commande ne s'active. Il faut utiliser les événements pour assurer la cohérence finale.
- **Chatty Services :** Éviter que le `MarketplaceService` appelle l' `IdentityService` 50 fois pour afficher une liste de Gigs. (Solution : Dénormalisation légère ou agrégation à la Gateway).

---
