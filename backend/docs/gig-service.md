# Gig Service

## Responsabilité

Gestion des services proposés par les étudiants.

## Fonctionnalités

- Création de gig
- Packages Basique/Standard/Premium
- Gestion disponibilité
- Images portfolio
- Catégories

## Entités

### Gig

- id
- title
- description
- category
- location
- availability

### GigPackage

- name
- price
- deliveryTime

## Endpoints

POST /api/gigs

GET /api/gigs

GET /api/gigs/{id}

PUT /api/gigs/{id}

DELETE /api/gigs/{id}