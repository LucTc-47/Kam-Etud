# Order Service

## Responsabilité

Gestion des missions et commandes.

## États

- PENDING
- ACCEPTED
- IN_PROGRESS
- DELIVERED
- VALIDATED
- DISPUTED
- CANCELLED

## Fonctionnalités

- Création mission
- Validation mission
- Révisions
- Upload livrables
- Gestion litiges

## Endpoints

POST /api/orders

PUT /api/orders/{id}/accept

PUT /api/orders/{id}/deliver

PUT /api/orders/{id}/validate

PUT /api/orders/{id}/dispute