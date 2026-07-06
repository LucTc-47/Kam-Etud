
# 📋 Request Service — Kam'Etud

## Synthèse GitHub — 6 juillet 2026

### Description

Request Service gère les demandes publiées par les clients et les propositions envoyées par les étudiants. Il écoute sur `8082` et utilise PostgreSQL `request_db`.

### Changements

- Les identités client/étudiant sont déduites du JWT et non plus du corps JSON.
- L'acceptation d'une proposition crée une commande dans Business Service de manière idempotente.
- Les étudiants doivent être vérifiés et non bannis pour proposer.
- Les anciennes écritures directes Supabase ont été remplacées par des appels REST entre microservices.

### Ajouts

- Endpoints `/api/v1/requests/**` pour publier, lister, fermer, annuler ou supprimer une demande.
- Endpoints `/api/v1/proposals/**` pour proposer, accepter, refuser et retirer une proposition.
- Notification au client quand une proposition est reçue.
- Appel interne `Request -> Business` protégé par `INTERNAL_SERVICE_TOKEN` lors de l'acceptation.

### Fonctionnement

Flux principal : le client publie une demande, un étudiant vérifié propose un prix, le client accepte, Request ferme la demande puis Business crée la commande. Endpoints principaux : `/api/v1/requests`, `/api/v1/requests/mine`, `/api/v1/proposals`, `/api/v1/proposals/mine`, `/api/v1/proposals/{id}/accept` et `/api/v1/proposals/{id}/reject`.

### Problèmes rencontrés

- L'ancien frontend pouvait choisir librement `clientId` ou `studentId`; les données viennent maintenant du JWT.
- L'acceptation d'une proposition ne créait pas encore de commande; l'intégration Business a rendu le flux complet.
- Les notifications ne doivent pas être publiques; elles passent par Support avec le jeton inter-service.

## Description
Microservice de gestion des appels d'offres et propositions 
de la plateforme Kam'Etud.

- **Équipe** : Melista (Chef de groupe) + Prisca (Binôme 4)
- **Port** : 8082
- **Base de données** : request_db (PostgreSQL, port 5433)

## Stack technique
- Java 21
- Spring Boot 4.1.0 (ancienne version 3.4.1 commentée dans le POM)
- PostgreSQL
- Maven
- Lombok
- Spring Data JPA
- Spring REST Client pour lire les profils et le statut KYC dans Identity Service

## Endpoints principaux

| Méthode | URL | Accès | Description |
|---|---|---|---|
| GET | `/api/v1/requests` | Public | Lister uniquement les demandes ouvertes |
| GET | `/api/v1/requests/{id}` | Public | Lire une demande |
| GET | `/api/v1/requests/mine` | Client | Lister ses propres demandes |
| POST | `/api/v1/requests` | Client | Créer une demande avec l'identité du JWT |
| PATCH | `/api/v1/requests/{id}/cancel` | Client propriétaire | Annuler la demande et refuser les propositions en attente |
| PUT | `/api/v1/requests/{id}/close` | Client propriétaire | Fermer une demande ; ancienne route conservée |
| DELETE | `/api/v1/requests/{id}` | Client propriétaire | Supprimer une demande non assignée |
| GET | `/api/v1/proposals/request/{requestId}` | Authentifié | Client : toutes ; étudiant : uniquement la sienne |
| GET | `/api/v1/proposals/mine` | Étudiant | Lister ses propositions |
| POST | `/api/v1/proposals` | Étudiant vérifié | Créer une proposition avec l'identité du JWT |
| PUT | `/api/v1/proposals/{id}/accept` | Client propriétaire | Accepter une proposition et refuser les autres |
| PUT | `/api/v1/proposals/{id}/reject` | Client propriétaire | Refuser une proposition |
| DELETE | `/api/v1/proposals/{id}` | Étudiant propriétaire | Retirer une proposition en attente |

## Lancer le service
```bash
mvn spring-boot:run
```

## Modifications du 4 juillet 2026

- Ancien port `8085` commenté : il entrait en conflit avec le Payment Service.
- Nouveau port : `8082`.
- Connexion PostgreSQL déplacée vers `request_db` sur `localhost:5433`, conformément au `docker-compose.yml`.
- Ajout des variables `SERVER_PORT`, `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`.

Ces changements permettent à Request Service et Payment Service de démarrer en même temps et évitent que Request Service utilise la base du Catalog Service.

### Migration Spring Boot 4.1.0

- Starter `spring-boot-starter-web` commenté et remplacé par `spring-boot-starter-webmvc`.
- Starter de test MVC Boot 4 et base H2 de test ajoutés.
- Ancienne annotation `@EntityScan` commentée : le package racine couvre déjà les entités.
- Package du test de contexte corrigé et `mvn clean test` validé.
- Classe principale et test déplacés physiquement de `cm/kametud/request_service` vers `cm/kametud/requestservice`, conformément à leur déclaration Java. Les anciens emplacements restent sous forme de fichiers commentés pour documenter le changement.

Pourquoi : la compilation devait être alignée sur les six autres services et le test ne devait plus réutiliser des classes compilées obsolètes dans `target`.

### Intégration Demandes et propositions

- Le préfixe `/api/v1` est conservé et le frontend l'utilise désormais via la Gateway.
- `clientId`, `studentId`, `clientName` et `studentName` envoyés dans les anciens DTO sont encore lisibles pour montrer l'ancien contrat, mais ils sont ignorés. L'identifiant vient des en-têtes fiables construits depuis le JWT et le nom vient d'Identity Service.
- La publication d'une proposition vérifie auprès d'Identity Service que l'étudiant est vérifié et non banni.
- Les contrôleurs vérifient les rôles et les services vérifient aussi la propriété de chaque demande ou proposition.
- Les statuts historiques français restent stockés dans le modèle (`OUVERT`, `ASSIGNE`, etc.) et sont traduits en statuts anglais attendus par React (`open`, `assigned`, etc.).
- L'acceptation est transactionnelle : une proposition devient `accepted`, les autres deviennent `rejected` et la demande devient `assigned` avec `acceptedProposalId`.
- L'annulation est transactionnelle et rejette toutes les propositions encore en attente.
- L'ancienne route publique `GET /student/{studentId}` a été commentée et retirée pour empêcher la consultation des propositions d'un autre étudiant.
- `spring-boot-starter-restclient` a été ajouté, car Spring Boot 4 place le client HTTP dans un starter modulaire dédié.
- Un gestionnaire d'erreurs uniforme renvoie les erreurs métier et de validation au frontend.
- Trois tests d'intégration couvrent le workflow complet, l'annulation et le refus d'un étudiant non vérifié avec H2 et un Identity Client simulé.

Pourquoi : le navigateur ne doit jamais pouvoir choisir l'identité ou contourner le KYC. La cohérence des statuts doit être garantie par une transaction backend, pas par plusieurs écritures Supabase indépendantes.

### Communication avec Identity Service

La destination par défaut est `http://localhost:8081`. Elle peut être remplacée avec :

```powershell
$env:IDENTITY_SERVICE_URL="http://identity-service:8081"
```

### Historique de l'étape Request

Accepter une proposition ne crée pas encore une commande. L'ancien insert direct dans la table Supabase `orders` est conservé sous forme de commentaire dans le hook React. La création de commande passera par Business Service pendant l'étape suivante afin de ne pas introduire une double source de vérité.

### Finalisation avec Business et Support — 4 juillet 2026

- L'acceptation appelle maintenant `POST /api/orders/internal/from-proposal` dans Business Service.
- La création est idempotente : rejouer l'acceptation ne crée pas une seconde commande.
- Request transmet uniquement les identifiants de la demande et de la proposition ; Business relit les données métier persistées.
- Une nouvelle proposition crée une notification pour le client via Support Service.
- Les appels inter-services utilisent `INTERNAL_SERVICE_TOKEN`; les variables sont `BUSINESS_SERVICE_URL` et `SUPPORT_SERVICE_URL`.
- Les tests simulent Business et Support pour vérifier le workflow sans dépendre de services démarrés.

Pourquoi : l'acceptation et la création de commande forment désormais un flux backend traçable, sans insert Supabase depuis React.

## Documentation complète
https://app.notion.com/p/Request-Service-Kam-Etud-38dad8ec5da480b4b754c01206039ab0

## Auteur
ASSONFACK Melista Elouanda — CM-UDS-23SCI0939  
Université de Dschang — L3 Informatique 2025/2026
