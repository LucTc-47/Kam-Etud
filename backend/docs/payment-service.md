# Payment Service

## Responsabilité

Gestion des paiements sécurisés.

## Fonctionnalités

- Paiement MoMo
- Escrow
- Paiement par tranches
- Commission plateforme
- Retrait gains

## Intégrations

- MTN MoMo API
- Orange Money API

## Workflow

1. Client paie
2. Argent bloqué
3. Mission validée
4. Déblocage automatique

## Endpoints

POST /api/payments/initiate

POST /api/payments/webhook

POST /api/payments/release

POST /api/payments/refund