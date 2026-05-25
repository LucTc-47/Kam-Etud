# Matching Service

## Responsabilité

Algorithme intelligent de recommandation.

## Critères

- Distance GPS
- Note moyenne
- Disponibilité
- Prix
- Catégorie

## Algorithme

Score = (
rating * 0.4 +
distance * 0.3 +
availability * 0.2 +
price * 0.1
)

## Fonctionnalités

- Recherche géographique
- Score pertinence
- Filtres dynamiques

## Technologies

- Redis cache
- Haversine Formula

## Endpoints

GET /api/matching/search

GET /api/matching/recommendations