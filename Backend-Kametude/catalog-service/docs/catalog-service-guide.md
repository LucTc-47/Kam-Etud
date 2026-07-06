# Kam'Etud Catalog Service - Documentation et guide d'utilisation

## 1. Role du service

Catalog Service gere le cycle de vie des prestations etudiantes, appelees `Gigs`.
Il expose une API REST sur le port `8083`, persiste ses donnees dans PostgreSQL et
stocke les trois formules de prix (`Basique`, `Standard`, `Premium`) dans des
colonnes JSONB.

Le service peut fonctionner seul, mais la publication d'un Gig depend du service
Identity. Avant de publier une offre, Catalog Service appelle Identity Service pour
verifier que l'etudiant est verifie et non banni.

## 2. Prerequis

- Java 21
- Maven wrapper fourni par le projet
- PostgreSQL avec une base `catalog_db`
- Identity Service disponible sur `http://localhost:8081` pour publier directement
- Postman pour tester manuellement l'API

## 3. Configuration locale

Fichier principal: `src/main/resources/application.yaml`

```yaml
server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/catalog_db
    username: postgres
    password: 809145
    driver-class-name: org.postgresql.Driver

identity:
  service:
    base-url: http://localhost:8081
    student-status-path: /api/students/{studentId}/status

app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
```

Adaptez `username`, `password` et `identity.service.base-url` selon votre machine.

## 4. Demarrage

Depuis la racine du projet:

```powershell
.\mvnw.cmd spring-boot:run
```

L'API est ensuite disponible sur:

```text
http://localhost:8083
```

Pour verifier la compilation et les tests:

```powershell
.\mvnw.cmd test
```

## 5. Modele de donnees API

### GigTierDto

Chaque formule contient:

```json
{
  "title": "Basic",
  "description": "Creation d'une page simple",
  "price": 10.00,
  "deliveryDays": 7
}
```

Regles de validation:

- `title`: obligatoire, non vide, 80 caracteres maximum
- `description`: obligatoire, non vide, 500 caracteres maximum
- `price`: obligatoire, minimum `0.01`, deux decimales maximum
- `deliveryDays`: obligatoire, entier positif

### GigCreateRequest

```json
{
  "studentId": "11111111-1111-1111-1111-111111111111",
  "title": "Developpement API Spring Boot",
  "description": "Creation d'une API REST documentee pour un projet etudiant.",
  "category": "Developpement",
  "location": "Paris",
  "tierBasique": {
    "title": "Basic",
    "description": "Un endpoint simple",
    "price": 10.00,
    "deliveryDays": 7
  },
  "tierStandard": {
    "title": "Standard",
    "description": "CRUD complet avec validation",
    "price": 25.00,
    "deliveryDays": 5
  },
  "tierPremium": {
    "title": "Premium",
    "description": "API complete avec documentation",
    "price": 45.00,
    "deliveryDays": 2
  },
  "published": true
}
```

Important: si `published` vaut `true`, Catalog Service appelle Identity Service.
Si l'etudiant n'est pas verifie ou est banni, la creation est refusee avec `403`.

## 6. Endpoints REST

### Creer un Gig

```http
POST /api/gigs
Content-Type: application/json
```

URL complete:

```text
http://localhost:8083/api/gigs
```

Reponse attendue:

- `201 Created` si le Gig est cree
- Header `Location: /api/gigs/{gigId}`
- Body: `GigResponse`

### Rechercher les Gigs publies

```http
GET /api/gigs
```

Filtres optionnels:

- `category`: recherche exacte insensible a la casse
- `location`: recherche partielle insensible a la casse

Exemples:

```text
GET http://localhost:8083/api/gigs
GET http://localhost:8083/api/gigs?category=Developpement
GET http://localhost:8083/api/gigs?location=par
GET http://localhost:8083/api/gigs?category=Design&location=Paris
```

Seuls les Gigs publies sont retournes.

### Recuperer un Gig par ID

```http
GET /api/gigs/{gigId}
```

Exemple:

```text
GET http://localhost:8083/api/gigs/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

Reponses:

- `200 OK` si le Gig existe
- `404 Not Found` si l'ID est inconnu

### Publier un Gig brouillon

```http
PATCH /api/gigs/{gigId}/publish
```

Exemple:

```text
PATCH http://localhost:8083/api/gigs/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/publish
```

Catalog Service appelle Identity Service avant de publier.

Reponses:

- `200 OK` si la publication reussit
- `403 Forbidden` si l'etudiant ne peut pas publier
- `404 Not Found` si le Gig n'existe pas
- `503 Service Unavailable` si Identity Service ne repond pas correctement

## 7. Codes d'erreur

Format standard des erreurs:

```json
{
  "timestamp": "2026-06-27T07:36:13.3428893+02:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid gig payload",
  "details": {
    "title": "must not be blank",
    "tierBasique.price": "must be greater than or equal to 0.01"
  }
}
```

Codes principaux:

- `400 Bad Request`: payload invalide
- `403 Forbidden`: etudiant non autorise a publier
- `404 Not Found`: Gig inexistant
- `503 Service Unavailable`: Identity Service indisponible

## 8. Guide Postman

### Option A - Importer la collection fournie

Importez ces deux fichiers dans Postman:

- `docs/postman/kametud-catalog-service.postman_collection.json`
- `docs/postman/kametud-catalog-local.postman_environment.json`

Selectionnez ensuite l'environnement `Kametud Catalog - Local`.

Variables fournies:

- `catalogBaseUrl`: `http://localhost:8083`
- `studentId`: UUID de test
- `gigId`: a renseigner apres une creation

### Option B - Creer les requetes manuellement

Dans Postman, creez une collection `Kametud Catalog Service`, puis ajoutez:

1. `Create published gig`
   - Method: `POST`
   - URL: `{{catalogBaseUrl}}/api/gigs`
   - Body: `raw`, `JSON`
   - Collez le payload `GigCreateRequest`

2. `Search published gigs`
   - Method: `GET`
   - URL: `{{catalogBaseUrl}}/api/gigs?category=Developpement&location=Paris`

3. `Get gig by id`
   - Method: `GET`
   - URL: `{{catalogBaseUrl}}/api/gigs/{{gigId}}`

4. `Publish draft gig`
   - Method: `PATCH`
   - URL: `{{catalogBaseUrl}}/api/gigs/{{gigId}}/publish`

### Astuce Postman pour recuperer automatiquement le gigId

Dans l'onglet `Tests` de la requete `Create published gig`, ajoutez:

```javascript
const response = pm.response.json();
if (response.id) {
  pm.environment.set("gigId", response.id);
}
```

## 9. Connexion avec le frontend

### URL de base

Pour React, Vite, Vue, Angular ou Next.js cote navigateur:

```text
http://localhost:8083/api
```

Exemple `.env` pour Vite:

```env
VITE_CATALOG_API_URL=http://localhost:8083/api
```

Exemple `.env.local` pour Next.js:

```env
NEXT_PUBLIC_CATALOG_API_URL=http://localhost:8083/api
```

### CORS

Le backend autorise par defaut:

- `http://localhost:3000`
- `http://localhost:5173`

Pour ajouter une autre origine frontend, modifiez:

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
      - http://localhost:4200
```

Redemarrez ensuite le service.

### Exemple avec fetch

```javascript
const API_URL = import.meta.env.VITE_CATALOG_API_URL;

export async function searchGigs({ category, location }) {
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  if (location) params.set("location", location);

  const response = await fetch(`${API_URL}/gigs?${params.toString()}`);

  if (!response.ok) {
    throw new Error("Impossible de charger les prestations");
  }

  return response.json();
}
```

### Exemple avec axios

```javascript
import axios from "axios";

export const catalogApi = axios.create({
  baseURL: import.meta.env.VITE_CATALOG_API_URL,
  headers: {
    "Content-Type": "application/json"
  }
});

export async function createGig(payload) {
  const { data } = await catalogApi.post("/gigs", payload);
  return data;
}

export async function publishGig(gigId) {
  const { data } = await catalogApi.patch(`/gigs/${gigId}/publish`);
  return data;
}
```

### Types TypeScript recommandes

```typescript
export type GigTier = {
  title: string;
  description: string;
  price: number;
  deliveryDays: number;
};

export type GigCreateRequest = {
  studentId: string;
  title: string;
  description?: string;
  category: string;
  location: string;
  tierBasique: GigTier;
  tierStandard: GigTier;
  tierPremium: GigTier;
  published: boolean;
};

export type GigResponse = GigCreateRequest & {
  id: string;
  rating: number;
  createdAt: string;
  updatedAt: string;
};
```

### Gestion des erreurs cote frontend

```javascript
try {
  await createGig(payload);
} catch (error) {
  const response = error.response?.data;

  if (response?.status === 400) {
    console.log(response.details);
  }

  if (response?.status === 403) {
    console.log("Votre profil etudiant ne permet pas encore de publier.");
  }

  if (response?.status === 503) {
    console.log("Verification du profil temporairement indisponible.");
  }
}
```

## 10. Points d'attention pour l'equipe frontend

- Pour creer un brouillon sans Identity Service, envoyez `published: false`.
- Pour publier directement, Identity Service doit etre lance et retourner un statut valide.
- Les recherches ne retournent que les Gigs publies.
- `studentId` et `gigId` doivent toujours etre des UUID valides.
- Le frontend ne doit pas recalculer les prix: il envoie et affiche les tiers fournis par l'API.
- Les messages de validation sont disponibles dans `details` quand le backend renvoie `400`.

## 11. Contrat attendu avec Identity Service

Catalog Service appelle:

```http
GET {identity.service.base-url}/api/students/{studentId}/status
```

Reponse attendue:

```json
{
  "verified": true,
  "banned": false
}
```

Publication autorisee uniquement si:

```text
verified == true
banned != true
```

## 12. Verification rapide de bout en bout

1. Demarrer PostgreSQL et creer `catalog_db`.
2. Demarrer Identity Service sur `8081`.
3. Demarrer Catalog Service sur `8083`.
4. Importer la collection Postman.
5. Executer `Create published gig`.
6. Copier ou enregistrer `id` dans `gigId`.
7. Executer `Get gig by id`.
8. Executer `Search published gigs`.
9. Depuis le frontend, appeler `GET http://localhost:8083/api/gigs`.
