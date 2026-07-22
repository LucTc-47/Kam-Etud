#  Kam'Etud — Plateforme Camerounaise des Étudiants Freelances

## Démarrage local et préparation GitHub — 6 juillet 2026

### Prérequis

- Java `21`
- Node.js `18+` et npm
- Docker Desktop avec Docker Compose v2
- PowerShell sur Windows

### Fichiers d'environnement

Créer les fichiers locaux à partir des modèles versionnés :

```powershell
Copy-Item .env.example .env
Copy-Item Backend-Kametude\.env.example Backend-Kametude\.env
```

Le frontend utilise principalement :

```env
VITE_API_URL=http://localhost:8080
```

Le backend utilise notamment :

```env
JWT_SECRET=<secret-base64-partage>
INTERNAL_SERVICE_TOKEN=<jeton-inter-service>
PAYMENT_PROVIDER=mock
```

`PAYMENT_PROVIDER=mock` est recommandé en local pour tester le parcours de paiement sans argent réel. Pour MeSomb réel, utiliser `PAYMENT_PROVIDER=mesomb` et fournir les clés MeSomb dans `Backend-Kametude/.env` sans jamais les committer.

`PAYMENT_MODE` est un alias équivalent, utilisé par les fichiers Docker Compose : le service résout `payment.provider=${PAYMENT_PROVIDER:${PAYMENT_MODE:mesomb}}`, donc `PAYMENT_PROVIDER` l'emporte s'il est défini.

### Lancer toute la plateforme

Deux parcours sont disponibles depuis la racine du dépôt.

**Tout-Docker — recommandé pour une démo.** Les sept microservices ont un `Dockerfile` et sont construits par Docker Compose : aucun Java ni Maven n'est requis sur la machine.

```powershell
.\start-demo.ps1 -SeedDemo
```

Le script construit et démarre la pile complète, attend que les bases soient `healthy` puis que les sept applications Spring Boot aient fini leur démarrage, vérifie `http://localhost:8080/actuator/health`, recrée les comptes de démonstration, valide le login via la Gateway et exécute le smoke test bout-en-bout. Les paiements y sont en mode mock. Le frontend se lance ensuite avec `.\start-frontend.ps1`.

Arrêt : `cd Backend-Kametude ; docker compose down`.

**Processus locaux — recommandé pour développer.** Seules les bases restent dans Docker, les applications tournent via Maven.

```powershell
.\start-local.ps1
```

Le script démarre les bases PostgreSQL Docker, les sept microservices, l'API Gateway, puis Vite.

Créer/restaurer les comptes de démonstration :

```powershell
.\Backend-Kametude\scripts\seed-demo-users.ps1
```

Comptes disponibles :

```text
admin@kametud.com     / 123456789!
moderator@kametud.com / 123456789!
student@kametud.com   / 123456789!
client@kametud.com    / 123456789!
```

URLs locales :

| Composant | URL |
| --- | --- |
| Frontend | `http://localhost:5173` |
| API Gateway | `http://localhost:8080` |
| Identity | `http://localhost:8081` |
| Request | `http://localhost:8082` |
| Catalog | `http://localhost:8083` |
| Business | `http://localhost:8084` |
| Payment | `http://localhost:8085` |
| Support | `http://localhost:8086` |

Arrêt :

```powershell
.\start-local.ps1 -Stop
```

Arrêt avec bases PostgreSQL :

```powershell
.\start-local.ps1 -Stop -StopDatabases
```

### Lancer séparément

Backend seulement :

```powershell
.\start-local.ps1 -BackendOnly
```

ou avec le raccourci :

```powershell
.\start-backend.ps1
```

Depuis `cmd.exe` :

```cmd
start-backend.cmd
```

Frontend seulement, si la Gateway tourne déjà :

```powershell
.\start-frontend.ps1
```

ou directement :

```powershell
npm run dev -- --host 0.0.0.0
```

### Déploiement production (VPS)

`Backend-Kametude/docker-compose.prod.yml` déploie la même pile sans exposer les bases ni RabbitMQ, persiste le stockage de Support dans un volume Docker et fixe les origines CORS sur `https://kametud.com`. Tous les secrets sont obligatoires : le fichier Compose utilise la syntaxe `${VAR:?}` et refuse de démarrer si une variable manque.

```powershell
Copy-Item Backend-Kametude\.env.prod.example Backend-Kametude\.env.prod
cd Backend-Kametude
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

`Backend-Kametude/.env.prod` est ignoré par Git et ne doit jamais être committé. Les valeurs par défaut du `docker-compose.yml` de développement (`password`, `change-this-internal-token`, `JWT_SECRET` de test) sont réservées au poste local.

## Signalement d'abus depuis un litige — 6 juillet 2026

Le modérateur peut désormais ouvrir un dossier d'abus depuis la fenêtre
d'arbitrage. La personne, le motif et une note d'au moins 10 caractères sont
obligatoires. L'administrateur retrouve ensuite le dossier dans **Modération**,
consulte les déclarations et preuves, puis peut le rejeter, avertir la personne
ou la bannir. Ces actions disciplinaires restent séparées du remboursement et
du paiement de la mission.

Kam'Etud est une plateforme bilingue (FR/EN) conçue pour connecter les étudiants talentueux des universités camerounaises avec des clients (familles, PME, particuliers) ayant besoin de services variés (académiques, numériques, aide à domicile, etc.).

---

##  Fonctionnalités Clés Implémentées

### 1. ️ Sécurité & Confiance
- **Vérification KYC (Étudiants uniquement)** : Inscription en 3 étapes avec chargement obligatoire de la CNI, d'un Selfie et de la carte étudiante.
- **Badge "Vérifié"** : Attribué manuellement par l'administrateur après audit des documents.
- **Paiement par Escrow (Séquestre)** : Les fonds sont bloqués via Mobile Money à la commande et libérés uniquement après validation de la livraison par le client.
- **Auto-validation 72h** : Paiement automatique à l'étudiant si le client ne réagit pas 72h après la soumission du livrable.

### 2.  Flux Métier (Bidding & Services)
- **Système d'Appels d'Offres** : Le client publie un besoin, les étudiants vérifiés proposent leurs prix et délais.
- **Services (Gigs)** : Les étudiants peuvent publier des services fixes avec 3 paliers de prix (Basique, Standard, Premium).
- **Chat en Temps Réel** : Communication instantanée entre client et étudiant pour chaque commande.
- **Livraison de Fichiers** : Soumission réelle du travail fini via le stockage sécurisé de la plateforme.

### 3.  Expérience Utilisateur (UX)
- **Centre de Notifications** : Alertes en temps réel pour les nouveaux messages, propositions reçues et changements de statut.
- **Bilinguisme Intégral** : Basculement fluide entre le Français et l'Anglais sur toute l'interface.
- **Mode Sombre/Clair** : Interface adaptative pour un confort visuel optimal.
- **Génération de CV PDF** : Les étudiants peuvent télécharger un CV professionnel basé sur leurs statistiques et missions réussies sur la plateforme.

### 4.  Tableaux de Bord (Dashboards)
- **Admin** : Gestion des utilisateurs (ban/unban), validation des identités, configuration des villes/catégories et export de rapports (Excel/PDF).
- **Modérateur** : Gestion des litiges avec droit de réponse des deux parties et arbitrage direct.
- **Étudiant** : Suivi des revenus (solde disponible vs en attente) et configuration du numéro de retrait.

---

##  Schéma Complet de la Base de Données (13 Tables)

###  Gestion des Utilisateurs
1. **`profiles`** : Informations civiles, académiques et statistiques.
   - `user_id` (UUID, PK), `first_name`, `last_name`, `email`, `role`, `phone` (Numéro de retrait), `city`, `university`, `faculty`, `level`, `bio`, `skills`, `avatar_url`, `verified`, `banned`, `rating`, `review_count`, `completed_jobs`, `xp`, `level_badge`.
2. **`user_roles`** : Gestion des droits d'accès (RBAC).
   - `id`, `user_id`, `role` (`admin`, `moderator`, `student`, `client`).

###  Services et Offres
3. **`gigs`** : Services fixes proposés par les étudiants.
   - `id` (UUID), `student_id`, `title`, `description`, `category`, `location`, `tier_basique`, `tier_standard`, `tier_premium` (JSONB), `images`, `rating`, `published`, `active`.
4. **`gig_requests`** : Besoins publiés par les clients (Appels d'offres).
   - `id` (UUID), `client_id`, `client_name`, `title`, `description`, `category`, `budget`, `deadline`, `status` (`open`, `assigned`, `cancelled`).
5. **`request_proposals`** : Réponses/Devis des étudiants aux appels d'offres.
   - `id`, `request_id`, `student_id`, `student_name`, `price`, `delivery_days`, `message`, `status`.

###  Transactions et Escrow
6. **`orders`** : Gestion des commandes et du séquestre financier.
   - `id` (UUID), `client_id`, `student_id`, `gig_id` (nullable), `budget`, `status`, `escrow_amount`, `deliverable_url`, `deliverable_note`, `delivered_at`, `revisions_left`, `payment_status`, `payout_status`.
7. **`payment_transactions`** : Historique technique des flux financiers (Campay).
   - `id`, `order_id`, `amount`, `phone`, `provider`, `reference`, `external_reference`, `status`, `kind` (`collect` or `payout`).

### ️ Confiance et Modération
8. **`verification_requests`** : Documents KYC pour audit administratif.
   - `student_id`, `student_name`, `email`, `university`, `id_type`, `id_file_url`, `selfie_url`, `student_card_url`, `status`.
9. **`disputes`** : Gestion des litiges par les modérateurs.
   - `id`, `order_id`, `client_id`, `student_id`, `client_statement`, `student_statement`, `status`, `moderator_note`, `resolved_at`.
10. **`reviews`** : Système de notation et avis.
    - `gig_id`, `order_id`, `reviewer_id`, `reviewer_name`, `student_id`, `rating`, `text`, `reported`.

###  Communication
11. **`chat_messages`** : Messagerie instantanée liée aux missions.
    - `id`, `order_id`, `sender_id`, `sender_name`, `content`, `type` (`text`, `file`, `system`).

###  Configuration
12. **`categories`** : Secteurs d'activité de la plateforme.
    - `id`, `name`, `icon`, `active`.
13. **`cities`** : Villes couvertes.
    - `id`, `name`, `active`.

---

## ️ Installation Technique

### Démarrage automatisé sur Windows

Depuis la racine d'un nouveau clone, installer uniquement **Java 21**, **Node.js 18+** et **Docker Desktop**, puis exécuter :

```powershell
.\start-local.ps1
```

Le script utilise Maven s'il est disponible, sinon il télécharge automatiquement la version déclarée par le Maven Wrapper du dépôt : Maven n'a donc pas besoin d'être installé globalement. Il installe les dépendances npm si nécessaire, démarre les six bases PostgreSQL, les sept applications Spring Boot et enfin Vite. Les ports PostgreSQL locaux `5632` à `5636` évitent la plage souvent réservée par Windows.

Une fois le démarrage terminé :

- frontend : `http://localhost:5173` ;
- API Gateway : `http://localhost:8080` ;
- logs : `logs/local/<date-du-demarrage>/`.

Pour arrêter les processus lancés par le script :

```powershell
.\start-local.ps1 -Stop
```

Pour arrêter aussi les conteneurs PostgreSQL sans supprimer leurs données :

```powershell
.\start-local.ps1 -Stop -StopDatabases
```

`start-local.cmd` offre le même lancement depuis `cmd.exe` ou par double-clic. Les vraies clés MeSomb peuvent être placées dans `Backend-Kametude/.env`; sans elles, Payment Service démarre avec des valeurs locales factices et les paiements réels restent désactivés.

1. **Clonage & Dépendances** :
   ```bash
   git clone <depot-url>
   npm install
   ```

2. **Variable d'Environnement (.env)** :
   - `VITE_API_URL=http://localhost:8080`
   - Pour un test depuis un autre appareil, remplacez `localhost` par l'adresse IPv4 du PC backend.

   Le frontend utilise exclusivement les contrats TypeScript Kam'Etud et l'API Gateway.

3. **Lancement** :
   ```bash
   npm run dev
   ```

## Intégration microservices — 4 juillet 2026

Le frontend appelle uniquement l'API Gateway. Les parcours Auth/KYC, catalogue, demandes/propositions, commandes/livraisons, avis/litiges, paiement, chat, notifications et stockage sont connectés aux sept applications Spring Boot `4.1.0` du dossier `Backend-Kametude`.

- Les identités et rôles viennent du JWT, puis la Gateway reconstruit les en-têtes internes.
- Les montants et propriétaires ne sont jamais choisis librement par le navigateur.
- Les fichiers privés sont téléchargés avec le JWT et contrôlés par propriétaire ou commande.
- Les notifications sont lues depuis Support Service et les anciens artefacts frontend ont été retirés.

---
© 2026 Kam'Etud — Université de Dschang. Tous droits réservés.
