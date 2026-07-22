# Payment Service — Kam'Etud

## Synthèse GitHub — 6 juillet 2026

### Description

Payment Service gère la collecte Mobile Money, le séquestre local, la vérification fournisseur et la libération des fonds. Il écoute sur `8085`, utilise PostgreSQL `payment_db` et ne doit jamais être appelé directement par le navigateur hors Gateway.

### Changements

- Les montants, la commande, le client et l'étudiant sont relus depuis Business/Identity.
- Les paiements sont idempotents par `orderId` et protégés contre les appels concurrents.
- Un provider local `MockPaymentProvider` permet les tests sans argent réel.
- Les secrets MeSomb ne sont plus stockés dans Git.

### Ajouts

- `PAYMENT_PROVIDER=mock` pour simuler collecte, vérification et payout en local.
- États techniques `INITIATING`, `HELD`, `COLLECTION_UNKNOWN`, `PAYOUT_IN_PROGRESS`, `RELEASED`.
- Route interne de release pour l'auto-validation Business.
- Validation distincte du numéro payeur client et du numéro de retrait étudiant.

### Fonctionnement

Endpoints principaux : `POST /api/payments/initiate`, `POST /api/payments/order/{orderId}/verify`, `POST /api/payments/order/{orderId}/release`, `GET /api/payments/order/{orderId}` et `POST /api/payments/internal/order/{orderId}/release`. En local, `PAYMENT_PROVIDER=mock` évite les appels réseau et garde le même workflow. En réel, `PAYMENT_PROVIDER=mesomb` demande les clés MeSomb.

### Problèmes rencontrés

- Les tests locaux ne peuvent pas manipuler d'argent réel; le provider mock reproduit le séquestre sans appeler MeSomb.
- Le profil public masquait le téléphone étudiant; Payment utilise maintenant l'endpoint interne `payout-profile`.
- Les anciennes clés live avaient été placées dans la configuration; elles ont été retirées et doivent rester révoquées.

## Nettoyage des diagnostics IDE — 6 juillet 2026

Les imports `HttpStatus` et `TransactionNotFoundException`, devenus inutiles après la centralisation des erreurs, sont conservés en commentaires afin de supprimer les avertissements Java signalés par l'IDE.

## Téléphone de retrait privé — 6 juillet 2026

- Payment lit le téléphone étudiant via la route interne protégée d'Identity et non plus via le profil public.
- `INVALID_PAYER_PHONE`, `SELLER_PHONE_MISSING` et `INVALID_SELLER_PHONE` distinguent désormais précisément la donnée incorrecte.
- Le navigateur ne fournit toujours ni le bénéficiaire ni son numéro de retrait.

Pourquoi : Identity masque correctement le téléphone dans `/api/profiles/{id}`; cet ancien appel faisait donc valider `null` comme numéro Mobile Money.

Ce service gère la collecte MeSomb, le séquestre et la libération d'un paiement. Il écoute sur `8085` et utilise `payment_db` (`localhost:5635` depuis l'hôte, `payment-db:5432` depuis le réseau Docker). C'est le seul service autorisé à appeler MeSomb.

## Fiabilisation des paiements — 5 juillet 2026

- La réservation `INITIATING` est validée en base avant l'appel MeSomb.
- La clé stable `collect-{orderId}` est envoyée comme `X-MeSomb-TrxID`; `orderId` reste unique.
- Verrou pessimiste et version optimiste protègent les appels concurrents.
- Un résultat ambigu passe à `COLLECTION_UNKNOWN` ou `PAYOUT_UNKNOWN` et n'est jamais rejoué à l'aveugle.
- Le payout passe par `PAYOUT_IN_PROGRESS`; `RELEASED` est idempotent.
- La confirmation est persistée avant la notification, empêchant un second versement si Support est indisponible.
- `POST /api/payments/internal/order/{orderId}/release` sert l'auto-validation Business.
- Montant et bénéficiaire viennent de Business/Identity; les clés MeSomb viennent uniquement de l'environnement.
- Timeout et retries sont configurables par `MESOMB_REQUEST_TIMEOUT_SECONDS` et `MESOMB_MAX_NETWORK_RETRIES`.

Pourquoi : l'ancien appel fournisseur précédait l'écriture locale et pouvait être doublé par des requêtes concurrentes.

## Configuration

| Variable | Valeur locale | Rôle |
| --- | --- | --- |
| `SERVER_PORT` | `8085` | Port HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5635/payment_db` | Base du service (`jdbc:postgresql://payment-db:5432/payment_db` en Docker) |
| `BUSINESS_SERVICE_URL` | `http://localhost:8084` | Commande et montant fiables |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Téléphone de retrait étudiant |
| `SUPPORT_SERVICE_URL` | `http://localhost:8086` | Notifications |
| `INTERNAL_SERVICE_TOKEN` | `change-this-internal-token` | Authentification inter-services |
| `PAYMENT_MODE` | `mock` en soutenance/local | Alias simple pour choisir le mode de paiement si `PAYMENT_PROVIDER` est absent |
| `PAYMENT_PROVIDER` | `mock` en local, `mesomb` en réel | Choix du fournisseur de paiement; prioritaire sur `PAYMENT_MODE` |
| `MESOMB_BASE_URL` | `https://business.mesomb.com/fr/api/v1.1` | API MeSomb |
| `MESOMB_APPLICATION_KEY` | — | Clé application MeSomb |
| `MESOMB_ACCESS_KEY` | — | Clé d'accès MeSomb |
| `MESOMB_SECRET_KEY` | — | Secret MeSomb |

Les anciennes clés live ont été retirées, et non commentées, car un secret compromis ne doit jamais rester dans Git. Elles doivent être révoquées.

## Contrat utilisé par le frontend

- `POST /api/payments/initiate` : collecte du montant de la commande pour son client.
- `POST /api/payments/order/{orderId}/verify` : synchronisation avec le fournisseur.
- `POST /api/payments/order/{orderId}/release` : libération après commande terminée.
- `GET /api/payments/order/{orderId}` : transaction visible par les participants ou le staff.

Les anciennes routes où le navigateur choisissait le montant, le vendeur ou la référence de paiement sont conservées en commentaires dans le contrôleur.

## Modifications d'intégration du 4 juillet 2026

- Montants convertis de `Double` vers `BigDecimal`.
- Une transaction unique par `orderId` rend l'initiation idempotente.
- Montant et propriétaires lus depuis Business Service ; téléphone de retrait lu depuis Identity Service.
- Identité et rôle issus des en-têtes reconstruits par la Gateway, jamais du corps JSON.
- Une collecte échouée reste `FAILED` et une collecte non confirmée reste `PENDING` ; elle n'est jamais marquée `HELD` arbitrairement.
- Libération autorisée seulement au client propriétaire ou au staff, sur une commande terminée et des fonds réellement détenus.
- Référence fournisseur, référence de versement et code USSD retournés au frontend.
- Notification de l'étudiant après libération via Support Service.
- Synchronisation idempotente de Business après collecte confirmée : la commande devient `accepted` et le travail peut commencer.
- Si Business est momentanément indisponible après la collecte, la transaction `HELD` reste enregistrée et toute initiation/vérification suivante retente la synchronisation sans nouvelle collecte.
- Migration vers Spring Boot `4.1.0`, Java `21`, starter REST Client et tests H2.

Pourquoi : le navigateur ne doit pas pouvoir modifier un montant ou désigner le bénéficiaire. Le paiement est désormais rattaché au workflow Business et réutilise les données vérifiées des autres services.

## Validation

```powershell
mvn clean test
```

Les tests couvrent l'idempotence, les erreurs fournisseur, la protection JWT et la libération conditionnelle.
