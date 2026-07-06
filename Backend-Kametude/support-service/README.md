# Support & Interaction Service — Guide complet

## Synthèse GitHub — 6 juillet 2026

### Description

Support Service regroupe le chat, les notifications et le stockage de fichiers publics/privés. Il écoute sur `8086` et utilise PostgreSQL `support_db`.

### Changements

- Les messages, notifications et fichiers ne reposent plus sur Supabase.
- Les identités envoyeur/propriétaire sont déduites du JWT ou du jeton inter-service.
- Les fichiers privés sont protégés par propriétaire, staff ou participation à la commande.
- Le WebSocket reste disponible, mais le frontend utilise surtout les endpoints REST stabilisés.

### Ajouts

- `POST /api/storage/upload` avec métadonnées de visibilité et ressource liée.
- `GET /api/storage/private/files/{filename}` avec contrôle d'accès.
- Notifications personnelles `/api/notifications/me` et endpoint interne `/api/notifications/internal`.
- Chat par commande avec validation du contexte auprès de Business Service.

### Fonctionnement

Endpoints principaux : `GET/POST /api/chat/orders/{orderId}/messages`, `/api/notifications/me`, `/api/notifications/me/unread`, `/api/notifications/{id}/read`, `/api/notifications/me/read-all`, `POST /api/notifications/internal`, `POST /api/storage/upload`, `GET /api/storage/files/{filename}`, `GET /api/storage/private/files/{filename}` et WebSocket `/ws`.

### Problèmes rencontrés

- Les anciennes routes acceptaient `userId`/`senderId` depuis le navigateur; ces champs sont désormais ignorés au profit de l'identité fiable.
- Les fichiers privés pouvaient être trop largement exposés; le service vérifie maintenant la visibilité et la ressource liée.
- Les notifications créées depuis d'autres services devaient être protégées; `INTERNAL_SERVICE_TOKEN` sert de barrière inter-service.

## État actuel après l'intégration frontend/backend — 4 juillet 2026

Le service écoute sur `8086` et utilise `support_db` (`localhost:5436`). Il fournit maintenant au frontend les API sécurisées de chat, notifications et stockage. Les exemples historiques plus bas sont conservés pour montrer les contrats qui existaient avant la migration ; les routes courantes sont celles de cette section.

### Contrat courant

- `GET/POST /api/chat/orders/{orderId}/messages` : historique et envoi REST. Business Service vérifie que l'utilisateur participe à la commande ou appartient au staff.
- `/ws` et `/app/chat.send` : canal STOMP conservé, avec validation du JWT à la connexion.
- `GET /api/notifications/me`, `GET /me/unread`, `PATCH /{id}/read`, `PATCH /me/read-all`, `DELETE /me` : centre de notifications du compte connecté.
- `POST /api/notifications/internal` : création inter-services protégée par `INTERNAL_SERVICE_TOKEN`.
- `POST /api/storage/upload` : upload public ou privé, propriétaire dérivé du JWT.
- `GET /api/storage/files/{filename}` : fichier public.
- `GET /api/storage/private/files/{filename}` : fichier privé réservé à son propriétaire, au staff ou aux participants de la commande liée.

### Modifications et raisons

- Les anciennes routes où le navigateur fournissait librement `userId` ou `senderId` ont été commentées et remplacées par l'identité injectée par la Gateway.
- Le chat demande à Business Service le contexte de commande avant toute lecture ou écriture, afin d'empêcher l'accès à une conversation étrangère.
- Le nom d'expéditeur vient d'Identity Service et chaque message crée une notification pour l'autre participant.
- Le frontend utilise les API REST et un rafraîchissement court ; WebSocket reste disponible pour une évolution temps réel ultérieure.
- Les fichiers possèdent maintenant un propriétaire, une visibilité, une catégorie et éventuellement un `resourceId` de commande. Le téléchargement privé applique ces métadonnées.
- Les notifications sont créées par Business, Request et Payment au moyen d'un jeton interne partagé, et non par une route publique falsifiable.
- Le décodage JWT a été aligné sur la clé Base64 de la Gateway et d'Identity ; seuls les access tokens sont acceptés.
- Spring Boot `4.1.0`, Java `21`, Spring Cloud `2025.1.2`, REST Client et H2 sont utilisés.

Variables supplémentaires : `BUSINESS_SERVICE_URL`, `IDENTITY_SERVICE_URL`, `INTERNAL_SERVICE_TOKEN`, `JWT_SECRET`, `STORAGE_LOCATION`.

Validation : `mvn clean test`. Les tests couvrent l'accès au chat, les notifications personnelles/inter-services et les fichiers privés.

## Documentation historique de l'équipe

# 1️⃣ Présentation

Le support-service gère 3 fonctionnalités indépendantes :

- Le chat en temps réel entre client et étudiant sur une commande
- Les notifications (alertes utilisateur)
- Le stockage de fichiers (CV, livrables, photos)

Port d'écoute : 8086
Base de données : PostgreSQL (support_db, port 5436)

<aside>
⚠️

La Gateway valide maintenant les JWT HTTP et le WebSocket possède son intercepteur JWT. Le port `8086` ne doit toutefois pas être exposé publiquement tant que chaque contrôleur REST ne vérifie pas encore finement l'accès à la ressource demandée.

</aside>

# 2️⃣ Prérequis

- Java 21 installé
- Maven (fourni via le wrapper `./mvnw`, pas d'installation séparée nécessaire)
- Docker Desktop installé et lancé
- Un IDE (VS Code avec Extension Pack for Java + Spring Boot Extension Pack, ou IntelliJ)

# 3️⃣ Créer le projet depuis zéro

Si vous devez recréer ce service depuis Spring Initializr ([https://start.spring.io](https://start.spring.io)) :

| Champ | Valeur |
| --- | --- |
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.0 (ancienne recommandation 3.3.x) |
| Group | com.kametude |
| Artifact | support-service |
| Packaging | Jar |
| Java | 21 |

**Dependencies à ajouter :**

- Spring Web
- WebSocket
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Eureka Discovery Client
- Lombok

<aside>
💡

Le nom du package généré sera `com.kametude.support_service` (avec underscore) — c'est normal, Spring Initializr convertit automatiquement les tirets de l'Artifact ID.

</aside>

# 4️⃣ Configuration

Renommer `application.properties` en `application.yml` dans `src/main/resources`, puis y mettre :

```yaml
spring:
  application:
    name: support-service
  datasource:
    url: jdbc:postgresql://localhost:5436/support_db
    username: user
    password: password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

server:
  port: 8086

eureka:
  client:
    enabled: false

app:
  storage:
    location: storage
```

<aside>
⚠️

`eureka.client.enabled: false` est temporaire — à retirer / passer à `true` dès qu'un serveur Eureka existe dans l'équipe, sinon ce service restera invisible pour l'api-gateway.

</aside>

# 5️⃣ Lancer la base de données

Depuis la racine du repo Backend-Kametude (là où se trouve `docker-compose.yml`) :

```bash
docker compose up -d support-db
```

Vérifier que ça tourne :

```bash
docker ps
```

Vous devez voir une ligne `support-db` avec le port `5436->5432` et le statut "Up".

# 6️⃣ Lancer l'application

Depuis le dossier `support-service` :

```bash
./mvnw spring-boot:run
```

Si tout fonctionne, le terminal affiche en dernière ligne :

```
Started SupportServiceApplication in X seconds
```

<aside>
🪟

Sur Windows, `curl` est un alias PowerShell qui casse la syntaxe classique. Utilisez toujours `curl.exe` (pas juste `curl`) pour tester les API en ligne de commande.

</aside>

# 7️⃣ Architecture du code

Le projet suit le pattern en couches classique de Spring Boot :

| Couche | Rôle | Dossier |
| --- | --- | --- |
| Entity | Représente une table SQL | `entity/` |
| Repository | Accès aux données (SQL généré automatiquement) | `repository/` |
| Service | Logique métier | `service/` |
| Controller | Expose les endpoints HTTP/WebSocket | `controller/` |
| DTO | Objets d'échange pour les requêtes (pas les entités directement) | `dto/` |
| Config | Configuration technique (WebSocket, etc.) | `config/` |

Flux d'une requête typique :
Client → Controller → Service → Repository → Base de données → retour JSON

# 8️⃣ API — Notifications

- POST /api/notifications — Créer une notification
    
    ```json
    {
      "userId": "uuid-de-l-utilisateur",
      "message": "Ta commande a ete mise a jour",
      "type": "ORDER_UPDATE"
    }
    ```
    
    `type` accepte uniquement : `ORDER_UPDATE`, `NEW_MESSAGE`, `PAYMENT_CONFIRMED`
    
    Exemple curl (Windows PowerShell) :
    
    ```bash
    curl.exe -X POST http://localhost:8086/api/notifications -H "Content-Type: application/json" --data "@notification.json"
    ```
    
- GET /api/notifications/users/{userId} — Liste des notifications
    
    ```
    GET http://localhost:8086/api/notifications/users/3fa85f64-5717-4562-b3fc-2c963f66afa6
    ```
    
    Réponse exemple :
    
    ```json
    [
      {
        "id": "c886ad4d-d02b-403c-829b-f138f4d8bb0f",
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "message": "Ta commande a ete mise a jour",
        "type": "ORDER_UPDATE",
        "createdAt": "2026-06-23T...",
        "read": false
      }
    ]
    ```
    
- PATCH /api/notifications/{notificationId}/read — Marquer comme lue

# 9️⃣ API — Chat

<aside>
🔌

Le chat fonctionne différemment du REST classique : le client ouvre une connexion persistante, s'abonne à un "topic", et reçoit les messages en temps réel sans avoir à redemander.

</aside>

## Connexion

```
ws://localhost:8086/ws  (via SockJS)
```

## Envoyer un message

| Élément | Valeur |
| --- | --- |
| Destination STOMP | /app/chat.send |

```json
{
  "orderId": "uuid-de-la-commande",
  "senderId": "uuid-de-l-expediteur",
  "content": "Bonjour !"
}
```

## Recevoir les messages en temps réel

S'abonner à : `/topic/order.{orderId}`
Chaque message envoyé sur cette commande sera reçu automatiquement par tous les abonnés.

## Historique (REST classique)

```
GET /api/chat/orders/{orderId}/messages
```

# 🔟 API — Storage

- POST /api/storage/upload — Upload d'un fichier
    
    ```bash
    curl.exe -X POST http://localhost:8086/api/storage/upload -F "file=@monfichier.pdf"
    ```
    
    Réponse :
    
    ```json
    {
      "filename": "24069ab4-bc17-48da-8a1a-18785bb05962.pdf",
      "downloadUrl": "/api/storage/files/24069ab4-bc17-48da-8a1a-18785bb05962.pdf"
    }
    ```
    
    <aside>
    💡
    
    Le fichier est renommé avec un UUID pour éviter les collisions de noms. C'est le `downloadUrl` retourné qu'il faut conserver pour récupérer le fichier plus tard.
    
    </aside>
    
- GET /api/storage/files/{filename} — Télécharger un fichier

- GET `/api/storage/private/files/{filename}` — Lire un fichier KYC privé. Cette route exige un JWT admin au niveau de la Gateway.

Le formulaire d'upload accepte `visibility=public|private`. Les avatars restent publics; les pièces KYC utilisent `private`. Les chemins sont normalisés pour bloquer la traversée de répertoires et la taille est limitée à 10 Mo par défaut.

# 🧪 Comment tester soi-même

**Pour le REST (notifications, storage) :**
Utiliser `curl.exe` (PowerShell) ou Postman.

**Pour le WebSocket (chat) :**
`curl` ne fonctionne pas bien avec WebSocket. Utiliser une page HTML de test avec SockJS + STOMP.js (voir code complet en annexe ci-dessous), ou un outil dédié comme Postman (qui supporte aussi STOMP).

- Code de la page de test HTML (chat)
    
    Colle ici le code complet du `test-chat.html` qu'on a utilisé.
    

# ⚠️ Problèmes rencontrés et solutions

| Problème | Cause | Solution |
| --- | --- | --- |
| `curl` renvoie des erreurs "Could not resolve host" | PowerShell interprète `curl` comme `Invoke-WebRequest`, casse les guillemets | Utiliser `curl.exe` explicitement |
| `--data "@fichier"` échoue | Mauvais dossier courant dans le terminal | Vérifier avec `pwd` avant de lancer la commande |
| Erreur "Syntax error on token }" | Méthode collée en dehors des accolades de la classe | Toujours vérifier que le code est entre `{ }` de la classe |
| Eureka spam des erreurs en boucle au démarrage | Pas de serveur Eureka disponible encore | `eureka.client.enabled: false` dans `application.yml` |
| Unknown property 'app' (warning VS Code) | Propriété personnalisée non reconnue par l'extension | Pas une erreur réelle, ignorer ce warning |

# 🧭 Ce qu'il reste à faire

- [ ]  Implémenter la vérification JWT (extraction userId depuis le token)
- [ ]  Sécuriser ou retirer le POST /api/notifications manuel (passer par RabbitMQ)
- [ ]  Migrer le stockage local vers MinIO/S3

## Modifications du 4 juillet 2026

- Le port `8086` devient configurable avec `SERVER_PORT`.
- L'URL PostgreSQL devient configurable avec `DB_URL` et reste alignée sur `support-db` du `docker-compose.yml`.
- La Gateway route maintenant REST et WebSocket vers ce service.
- Parent Maven migré vers Spring Boot `4.1.0` et Spring Cloud `2025.1.2`; anciennes versions commentées.
- Starter servlet migré vers `spring-boot-starter-webmvc` et starters de test Boot 4 ajoutés.
- H2 ajouté pour les tests de contexte hors PostgreSQL.
- `JWT_SECRET` partagé avec l'Identity Service et la Gateway.
- L'upload `/api/storage/upload` est protégé au niveau de la Gateway et sert maintenant aux pièces KYC privées et aux avatars publics du frontend.
- Le téléchargement KYC `/api/storage/private/**` est réservé aux JWT admin; l'ancien téléchargement public reste disponible pour les avatars.
- Suite Maven validée sous Java 21.

### Pourquoi

Le service doit pouvoir fonctionner en local et dans Docker sans modifier le dépôt. Le routage WebSocket séparé évite également de traiter `/ws/**` comme une requête HTTP ordinaire.

La migration Boot 4.1.0 garantit que WebSocket, validation et tests utilisent la même génération Spring que le reste du backend. La protection Gateway empêche un upload anonyme depuis le point d'entrée public; les contrôles d'autorisation internes restent à compléter avant d'exposer directement le service.

### À faire à l'étape Support

- Remplacer les en-têtes `X-User-Id` fournis par le navigateur par une identité issue du JWT.
- Vérifier que l'utilisateur appartient bien à la commande avant de retourner l'historique du chat.
- Protéger l'upload et le téléchargement des fichiers privés.
- [ ]  Réactiver Eureka une fois disponible dans l'équipe
