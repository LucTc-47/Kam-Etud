# Kam'Etud Payment Service — Documentation et guide d'utilisation

## 1. Rôle du service

Payment Service gère le cycle de vie des transactions financières sur la plateforme Kam'Etud. Il expose une API REST sur le port **8085**, persiste ses données dans PostgreSQL (`payment_db`) et intègre **MeSomb** comme fournisseur de paiement Mobile Money.

Le service implémente un mécanisme d'**escrow** (séquestre) : lorsqu'un client paie pour une prestation, les fonds sont bloqués sur le compte de la plateforme jusqu'à ce que le client valide le livrable. La libération vers le prestataire est ensuite déclenchée par le Business Service.

Une **commission de 10%** est prélevée par la plateforme sur chaque transaction. Le prestataire reçoit donc 90% du montant initial.

> ⚠️ **Note sandbox** : la validation en environnement sandbox réel est actuellement **bloquée côté MeSomb** (voir section 14) — le compte/application n'est pas encore pleinement activé côté fournisseur, malgré une intégration technique complète et testée (mocks). Le support MeSomb a été contacté à deux reprises et une réponse est en attente.

---

## 2. Choix techniques et justifications

### Pourquoi MeSomb (et pas FedaPay) ?

Trois fournisseurs ont été évalués au cours du projet : **Campay**, **FedaPay**, puis **MeSomb**.

- **Campay** : spécialisé MTN/Orange Cameroun, mais sandbox limitée à **25 FCFA par transaction de test** — impossible de simuler des montants réalistes (5 000–7 000 FCFA).
- **FedaPay** : sandbox sans limite de montant, mais devise par défaut `XOF` (zone BCEAO), incompatible à terme avec les numéros et la devise réels du Cameroun (`XAF`). **FedaPay a d'abord été retenu**, avant d'être définitivement écarté : ses **payouts sont désactivés en sandbox** (confirmé par le support FedaPay le 02/07/2026), rendant impossible le test de `libererFonds()`.
- **MeSomb** : retenu en définitive — devise `XAF` native, détection automatique MTN/Orange Cameroun, sandbox sans limite annoncée, et surtout **Collect et Deposit (payout) tous deux disponibles**, contrairement à FedaPay.

Ni Campay, ni FedaPay, ni MeSomb ne gèrent l'escrow nativement — ce sont des passerelles de paiement (Collect + Payout/Disburse séparés). La logique de séquestre et de libération conditionnelle est entièrement implémentée dans ce microservice.

### Architecture agnostique du fournisseur

Le service est conçu pour être **indépendant du fournisseur de paiement**. Une interface `PaymentProvider` définit le contrat commun que toute implémentation doit respecter :

```java
public interface PaymentProvider {
    PaymentResponseDTO initierPaiement(PaymentRequestDTO requestDTO);
    String verifierStatut(String externalReference);
    String libererFonds(String sellerPhone, Double amount, String orderReference);
    String getProviderName();
}
```

`PaymentService` ne connaît jamais le nom du fournisseur directement — il parle uniquement à cette interface. C'est précisément ce qui a permis de passer de FedaPay à MeSomb **sans modifier** `PaymentService`, `PaymentController`, ni les DTOs : seule une nouvelle implémentation de `PaymentProvider` (`MesombProvider`) a été ajoutée.

### Pourquoi le SDK officiel MeSomb (et pas des appels REST manuels) ?

MeSomb requiert une signature cryptographique stricte des requêtes. Plutôt que de la reconstruire manuellement en `RestTemplate`, le service utilise le SDK Java officiel `mesomb-java` (version `1.1.3`, résolu via JitPack car non publié sur Maven Central) :

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.hachther</groupId>
    <artifactId>mesomb-java</artifactId>
    <version>1.1.3</version>
</dependency>
```

**Point d'attention découvert en cours d'intégration** : la documentation publique de MeSomb ne détaille les SDK que pour JS/PHP/Python — très peu pour Java. Les classes `TransactionResponse` et `Transaction` (package `com.hachther.mesomb.models`) n'ont **pas de getters** : leurs champs sont **publics et directs** (`response.reference`, `transaction.status`, etc.). Cette information a été obtenue par décompilation (FernFlower, IntelliJ IDEA) du `.jar` local, faute de documentation Java suffisante.

### Pourquoi Spring Boot et Maven ?

- **Spring Boot 4.1.0** : standard de fait pour les microservices Java. ⚠️ Version récente avec des changements notables pour les tests par rapport à Spring Boot 3.x (voir section 9).
- **Maven** : syntaxe XML explicite, plus accessible pour un projet d'équipe à niveaux d'expérience variés.
- **PostgreSQL en conteneur Docker isolé** : chaque microservice a sa propre base, conformément au principe "database per service" de l'architecture microservices, et pour éviter les conflits entre les 12 binômes travaillant en parallèle.

---

## 3. Architecture interne

```
com.kametude.payment/
├── PaymentServiceApplication.java        → point d'entrée Spring Boot
│
├── controller/
│   └── PaymentController.java            → endpoints REST (POST /api/payments, POST /api/payments/{id}/release)
│
├── service/
│   └── PaymentService.java               → logique métier : escrow, commission 10%, orchestration provider/base
│
├── provider/
│   ├── PaymentProvider.java              → interface commune (architecture agnostique du fournisseur)
│   ├── FedapayProvider.java              → @Component RETIRÉ (désactivé, conservé pour référence)
│   └── MesombProvider.java               → ACTIF — implémentation via le SDK officiel mesomb-java
│
├── repository/
│   └── PaymentTransactionRepository.java → accès base de données via Spring Data JPA (CRUD auto-généré)
│
├── entity/
│   └── PaymentTransaction.java           → mapping JPA de la table payment_transactions
│
├── dto/
│   ├── PaymentRequestDTO.java            → ce que Business Service envoie (orderId, amount, phone, sellerPhone)
│   └── PaymentResponseDTO.java           → ce que payment-service retourne (transactionId, status, commission, netAmount)
│
└── exception/
    ├── PaymentException.java             → exception métier de base (portée par tous les providers)
    ├── TransactionNotFoundException.java → transaction introuvable (404)
    ├── InvalidTransactionStatusException → statut incompatible avec l'opération demandée (400)
    └── GlobalExceptionHandler.java       → @RestControllerAdvice : transforme toutes les exceptions en JSON propre,
                                             y compris désormais un handler dédié à PaymentException (mappage par errorCode)
```

**Tests** (`src/test/java/com/kametude/payment/`) :
```
├── service/PaymentServiceTest.java              → 5 tests unitaires ✅ (mock PaymentProvider)
├── integration/PaymentIntegrationTest.java      → 4 tests d'intégration ✅ (MockitoBean PaymentProvider, base H2)
└── provider/MesombProviderTest.java             → 9 tests unitaires ✅ (nouveau — logique interne de MesombProvider)
```

---

## 4. Prérequis

- Java 21
- Maven 3.9+ installé globalement (`mvn --version` pour vérifier — **ne jamais utiliser `./mvnw`**, timeouts DNS constatés en réseau local)
- Docker Desktop lancé
- Postman pour tester manuellement l'API
- Un compte MeSomb avec une application enregistrée (menu "Applications" du dashboard) — voir section 14 pour le statut actuel du blocage sandbox

---

## 5. Configuration locale

### `src/main/resources/application.properties`

```properties
spring.application.name=payment-service
server.port=8085

spring.datasource.url=jdbc:postgresql://localhost:5435/payment_db
spring.datasource.username=user
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

mesomb.application-key=VOTRE_CLE_APPLICATION
mesomb.access-key=VOTRE_CLE_ACCES
mesomb.secret-key=VOTRE_CLE_SECRETE
```

> ⚠️ Ce fichier est dans le `.gitignore` — il ne sera jamais commité. Copiez `application.properties.example` et renommez-le en `application.properties`, puis renseignez vos clés MeSomb.

**Les 3 clés sont obligatoires** (le SDK `PaymentOperation` les exige toutes) :

| Clé | Où la trouver |
|---|---|
| `application-key` | Dashboard MeSomb → **Applications** (créer une application dédiée si besoin — **pas** dans l'onglet "Clés API") |
| `access-key` | Dashboard MeSomb → **Clés API** → "Clé d'accès" |
| `secret-key` | Dashboard MeSomb → **Clés API** → "Clé secrète" |

---

## 6. Démarrage

### Étape 1 — Lancer la base de données

Depuis la racine du projet (`Backend-Kametude`) :

```bash
docker compose up -d payment-db
```

Vérifier que le conteneur tourne :
```bash
docker ps
```
Vous devez voir `payment-db` avec le port `0.0.0.0:5435->5432/tcp`.

### Étape 2 — Créer la table `payment_transactions`

```bash
psql -h localhost -p 5435 -U user -d payment_db
```
Mot de passe : `password`

```sql
CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(50) NOT NULL,
    external_reference VARCHAR(255),
    phone VARCHAR(20),
    seller_phone VARCHAR(20),
    payout_reference VARCHAR(255),
    commission DOUBLE PRECISION
);
```

Vérifier : `\dt`

### Étape 3 — Lancer l'application

```bash
mvn spring-boot:run
```

L'API est disponible sur `http://localhost:8085`. Confirmation attendue dans les logs :
```
Started PaymentServiceApplication in X.XXX seconds
Tomcat started on port 8085 (http) with context path '/'
```

Pour vérifier la compilation et les tests :
```bash
mvn clean test
```
**Statut actuel : `mvn clean compile` → BUILD SUCCESS. `mvn test` → BUILD SUCCESS (18/18 tests, tous mockés).**

---

## 7. Modèle de données API

### PaymentRequestDTO — corps de la requête

```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 6000,
  "phone": "237600000000",
  "sellerPhone": "237699999999"
}
```

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `orderId` | UUID | ✅ | ID de la commande côté Business Service |
| `amount` | Double | ✅ | Montant total en FCFA (XAF) |
| `phone` | String | ✅ | Numéro Mobile Money du client (payeur) |
| `sellerPhone` | String | ✅ | Numéro Mobile Money du prestataire (destinataire à la libération) |

### PaymentResponseDTO — corps de la réponse

```json
{
  "transactionId": "dc5fab57-ebc1-40d3-8b29-ddbbff50ae3f",
  "status": "HELD",
  "externalReference": "461328",
  "commission": 600.0,
  "netAmount": 5400.0,
  "message": "Paiement de 6000.0 XAF collecté avec succès (MeSomb)."
}
```

> ⚠️ Un `status: FAILED` avec `HTTP 200` est un comportement **normal et volontaire** : un échec de paiement Mobile Money est un cas métier, pas une erreur serveur. `PaymentService` gère ce cas sans lever d'exception.

### Statuts possibles de `payment_transactions`

| Statut | Signification |
|---|---|
| `PENDING` | Paiement initié côté MeSomb, en attente de confirmation Mobile Money |
| `HELD` | Fonds encaissés et bloqués en escrow sur le compte plateforme |
| `RELEASED` | Fonds libérés vers le prestataire après validation du livrable |
| `FAILED` | Échec du paiement ou de la libération |

### Schéma complet de `payment_transactions`

| Champ | Type | Description |
|---|---|---|
| `id` | UUID (PK) | Identifiant interne, généré côté base |
| `order_id` | UUID | Référence à la commande (Business Service) |
| `amount` | Double | Montant total en XAF |
| `status` | String | Statut courant (PENDING, HELD, RELEASED, FAILED) |
| `provider` | String | Fournisseur utilisé (MeSomb) |
| `external_reference` | String | Référence de transaction renvoyée par MeSomb |
| `phone` | String | Numéro Mobile Money du client |
| `seller_phone` | String | Numéro Mobile Money du prestataire |
| `payout_reference` | String | Référence du payout MeSomb (libération vers prestataire) |
| `commission` | Double | Montant de la commission plateforme (10% du montant total) |

---

## 8. Endpoints REST

### Créer un paiement (escrow)

```
POST /api/payments
Content-Type: application/json
```
`http://localhost:8085/api/payments` — `200 OK`, `status = HELD` en cas de succès MeSomb.

> ⚠️ **Important pour Business Service** : le `transactionId` retourné doit être **stocké côté Business Service** — indispensable pour déclencher la libération des fonds.

### Libérer les fonds (après validation du livrable)
```
POST /api/payments/{transactionId}/release
```
`http://localhost:8085/api/payments/{transactionId}/release` — pas de corps. `200 OK`, `status = RELEASED` en cas de succès.

---

## 9. Codes d'erreur

Format standard :
```json
{
  "timestamp": "2026-07-03T10:00:00",
  "status": 404,
  "error": "TRANSACTION_NOT_FOUND",
  "message": "Transaction introuvable : dc5fab57-ebc1-40d3-8b29-ddbbff50ae3f"
}
```

| Code HTTP | Code erreur | Cause |
|---|---|---|
| `400` | `INVALID_TRANSACTION_STATUS` | Transaction déjà libérée ou dans un statut incompatible |
| `404` | `TRANSACTION_NOT_FOUND` | `transactionId` inexistant en base, ou référence MeSomb introuvable |
| `502` | `PAYMENT_PROVIDER_ERROR` | MeSomb a rejeté la requête (clé invalide, montant incorrect...) |
| `503` | `PAYMENT_PROVIDER_UNREACHABLE` | Impossible de joindre MeSomb (timeout, réseau) |
| `500` | `INTERNAL_ERROR` | Erreur interne non prévue — contacter le binôme 6 |

`GlobalExceptionHandler` route désormais toute `PaymentException` levée par `MesombProvider` vers le bon code HTTP via un `switch` sur `errorCode` — les anciens handlers `HttpClientErrorException`/`HttpServerErrorException`/`ResourceAccessException` (utiles pour l'ancienne implémentation MeSomb en `RestTemplate` brut) ont été retirés car devenus du code mort avec le SDK officiel, qui catch déjà tout en interne.

---

## 10. Spécificités Spring Boot 4.1.0 pour les tests

⚠️ Package/API changées par rapport à Spring Boot 3.x — validées via les 18 tests au vert :

| Spring Boot 3.x (ne fonctionne plus) | Spring Boot 4.1.0 (à utiliser) |
|---|---|
| `org.springframework.boot.test.mock.mockito.MockBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `ObjectMapper` (Jackson 2) injecté dans les tests | Jackson 3 — construire le JSON manuellement en `String` dans les tests |

---

## 11. Guide Postman

### Collection `Kametud - Payment Service`

**1. Créer un paiement**
- `POST http://localhost:8085/api/payments`
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 7000,
  "phone": "237600000001",
  "sellerPhone": "237600000001"
}
```
> Numéros de test MeSomb officiels (confirmés via le dashboard, opération COLLECTION) : `237600000001` = succès garanti, `237600000002` = pending, `237600000019/029/039/049/069` = différents scénarios d'échec. La liste diffère légèrement pour l'opération DISBURSEMENT (payout) — vérifier le widget "Test mode" du dashboard MeSomb si besoin.

**2. Libérer les fonds**
- `POST http://localhost:8085/api/payments/{{transactionId}}/release` — pas de body

### Script Tests (récupération automatique du `transactionId`)

```javascript
const response = pm.response.json();
if (response.transactionId) {
    pm.environment.set("transactionId", response.transactionId);
}
```

---

## 12. Contrat attendu avec Business Service

Payment Service est appelé exclusivement par **Business Service — Binôme 5 (Elvira & Veronique)**.

```
[Business Service]
        │ POST /api/payments { orderId, amount, phone, sellerPhone }
        ▼
[Payment Service] → MeSomb Collect (makeCollect) → HELD en base
        │ retourne transactionId
        ▼
[Business Service stocke transactionId dans la commande]
        │ (le client valide le livrable)
        │ POST /api/payments/{transactionId}/release
        ▼
[Payment Service] → MeSomb Deposit/Payout (makeDeposit, 90% vers sellerPhone) → RELEASED en base
```

### Gestion des erreurs côté Business Service

| Code | Action recommandée |
|---|---|
| `400` | Ne pas retenter — la transaction est dans un état final |
| `404` | Vérifier le `transactionId` stocké dans la commande |
| `502` | Réessayer après un délai (erreur MeSomb transitoire) |
| `503` | MeSomb indisponible — réessayer plus tard |

---

## 13. Notes techniques importantes

- **Devise** : `XAF` (Cameroun), native chez MeSomb — contrairement à FedaPay qui imposait `XOF`.
- **La commission de 10%** est provisoire — à confirmer avec le cahier des charges officiel avant la mise en production.
- **En cas de litige**, la résolution (remboursement ou libération) est appliquée manuellement par le modérateur via Support Service — Payment Service n'initie aucune décision automatique.
- **Le paiement en tranches** (> 10 000 FCFA) est hors périmètre de la version actuelle.
- **`application.properties`** est dans le `.gitignore` — copier `application.properties.example` et renseigner ses propres clés MeSomb.
- **Champs publics sans getters** : `TransactionResponse`/`Transaction` du SDK MeSomb s'utilisent via `response.reference`, `response.message`, `transaction.status`, etc. — pas de `.getReference()`.

---

## 14. ⚠️ Point ouvert — blocage de validation sandbox (à surveiller)

L'intégration MeSomb est **techniquement complète et compilée/testée** (18/18 tests, tous mockés), mais **non encore validée en conditions réelles**. Symptôme observé : `initierPaiement()` renvoie `HTTP 200` avec `status: FAILED`, même avec les numéros de test officiels garantis (`237600000001`), reproduit à l'identique sur deux comptes MeSomb distincts.

**Diagnostic en cours** : très probablement lié à un statut de compte/application MeSomb non pleinement activé (KYC entreprise disproportionné demandé pour un projet académique). Le support MeSomb a été contacté deux fois (03/07/2026) — réponse en attente.

**Un bloc de débogage a été laissé volontairement dans `MesombProvider.initierPaiement()`** (juste après l'appel `client().makeCollect(payload)`) pour faciliter le diagnostic une fois la réponse du support obtenue :

```java
System.out.println("====== DEBUG MESOMB ======");
if (response != null) {
    System.out.println("isTransactionSuccess() : " + response.isTransactionSuccess());
    System.out.println("Message MeSomb          : " + (response.message != null ? response.message : "null"));
    System.out.println("Référence MeSomb        : " + (response.reference != null ? response.reference : "null"));
} else {
    System.out.println("La réponse brute est totalement NULL");
}
System.out.println("==========================");
```
👉 **À retirer avant la mise en production**, une fois le blocage résolu et le flux validé de bout en bout.

**Alternative explorée** : PayUnit — écartée en l'état, faute de SDK Java officiel et de fonctionnalité de payout programmatique claire vers un tiers (à reconfirmer avec leur support si MeSomb reste bloqué trop longtemps).

---

```markdown
## 15. Vérification rapide de bout en bout (à relancer une fois le blocage MeSomb résolu)

1. Lancer Docker Desktop
2. `docker compose up -d payment-db`
3. Créer la table si nécessaire (section 6)
4. `mvn spring-boot:run`
5. Attendre `Started PaymentServiceApplication`
6. Postman — **Créer un paiement** avec `237600000001` → vérifier `status = HELD`
7. Postman — **Libérer les fonds** → vérifier `status = RELEASED`
8. Vérifier en base :
```sql
SELECT * FROM payment_transactions ORDER BY id DESC LIMIT 5;
```

### Scripts de tests Postman (onglet "Tests")

**Requête 1 — Créer un paiement**

Body :
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 7000,
  "phone": "237600000001",
  "sellerPhone": "237600000001"
}
```

Script Tests (récupère automatiquement le `transactionId` pour la requête suivante, et vérifie le statut/la commission) :

```javascript
const response = pm.response.json();

if (response.transactionId) {
    pm.environment.set("transactionId", response.transactionId);
}

pm.test("Statut HELD", function () {
    pm.expect(response.status).to.eql("HELD");
});

pm.test("Commission correcte (10%)", function () {
    pm.expect(response.commission).to.eql(700.0);
});

pm.test("Montant net correct (90%)", function () {
    pm.expect(response.netAmount).to.eql(6300.0);
});
```

**Requête 2 — Libérer les fonds**

URL : `http://localhost:8085/api/payments/{{transactionId}}/release` — pas de body.

Script Tests :
```javascript
const response = pm.response.json();

pm.test("Statut RELEASED", function () {
    pm.expect(response.status).to.eql("RELEASED");
});

pm.test("Référence de payout présente", function () {
    pm.expect(response.externalReference).to.not.be.undefined;
});
```

> Ces deux scripts sont déjà enregistrés dans la collection Postman `Kametud - Payment Service (MeSomb)` — à relancer telle quelle une fois le blocage sandbox levé, sans modification.
```