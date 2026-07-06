#  Contrats Communs & Standards (À lire par tous)

Pour que les 7 services fonctionnent ensemble, chaque binôme doit respecter ces standards.

## 1. Format des Échanges (JSON)
- Les dates doivent être au format ISO-8601 : `"2024-06-23T14:00:00Z"`.
- Les montants financiers sont en `Double` (ou `BigDecimal`).
- Les identifiants sont TOUJOURS des `UUID`.

## 2. En-têtes de Sécurité
- Chaque requête inter-service doit transmettre le header `Authorization: Bearer <JWT>`.
- L'identifiant de l'utilisateur connecté est extrait du JWT (champ `sub` ou `userId`).

## 3. Codes Erreurs Standards
- `401 Unauthorized` : Token manquant ou invalide.
- `403 Forbidden` : Rôle insuffisant (ex: un client qui veut créer un Gig).
- `404 Not Found` : Ressource inexistante.
- `422 Unprocessable Entity` : Erreur de validation métier (ex: solde insuffisant).

## 4. Communication Inter-Services (Ports par défaut)
| Service | Port Local |
| :--- | :--- |
| `gateway-service` | 8080 |
| `identity-service` | 8081 |
| `catalog-service` | 8082 |
| `request-service` | 8083 |
| `business-service` | 8084 |
| `payment-service` | 8085 |
| `support-service` | 8086 |
