# Business Service — Kam'Etud

## Synthèse GitHub — 6 juillet 2026

### Description

Business Service est la source de vérité des commandes, missions, livrables, révisions, avis, litiges, statistiques étudiantes et dossiers d'abus. Il écoute sur `8084` et utilise PostgreSQL `business_db`.

### Changements

- Remplacement du workflow Supabase par un workflow serveur contrôlé.
- Séparation claire entre paiement (`Payment Service`), commande (`Business Service`) et sanction disciplinaire (`Identity/Admin`).
- Les commandes issues des gigs et celles issues des demandes passent par le même moteur de statut.
- Les avis visibles recalculent automatiquement les statistiques étudiant et les compteurs du catalogue.

### Ajouts

- Création idempotente de commande depuis une proposition Request.
- Livrables, révisions, litiges, modération d'avis, auto-validation et statistiques étudiant.
- Dossiers d'abus liés aux litiges, avec création par modérateur et décision par admin.
- Routes internes pour Payment et Support : contexte commande, confirmation de séquestre et auto-validation.

### Fonctionnement

Endpoints principaux : `/api/orders/**`, `/api/deliverables/**`, `/api/revisions/**`, `/api/reviews/**`, `/api/disputes/**`, `/api/abuse-reports/**`, `/api/admin/abuse-reports/**` et `/api/student-stats/**`. Business dépend d'Identity pour les profils, de Catalog pour les gigs et notes, de Payment pour les releases automatiques, et de Support pour les notifications.

### Problèmes rencontrés

- Les montants et propriétaires pouvaient auparavant venir du navigateur; Business relit maintenant les données fiables depuis JWT/Catalog/Request.
- Les avis étaient synchronisés sur le profil mais pas sur `/services`; une propagation vers Catalog a été ajoutée.
- Le bouton “Signaler abus” n'enregistrait rien; un vrai dossier d'abus existe maintenant sans déclencher automatiquement remboursement, paiement ou bannissement.

## Dossiers d'abus liés aux litiges — 6 juillet 2026

- `POST /api/abuse-reports` permet uniquement à un `MODERATOR` de signaler
  un client ou un étudiant participant réellement au litige.
- Le dossier conserve le litige, la personne signalée, le modérateur, le motif,
  la note, les preuves du litige et l'historique de décision.
- `GET /api/admin/abuse-reports` et
  `PATCH /api/admin/abuse-reports/{id}` sont réservés à `ADMIN`.
- Les décisions disponibles sont `DISMISS`, `WARN` et `BAN`. `WARN` crée une
  notification. `BAN` enregistre la décision après l'appel du mécanisme de
  bannissement Identity existant par le frontend administrateur.
- Un verrou sur le litige et une recherche `litige + utilisateur + motif`
  empêchent deux signalements ouverts identiques, y compris en cas de requêtes
  simultanées.
- La création et la décision d'un dossier ne modifient jamais la commande, le
  statut du litige, le séquestre, le remboursement ou le paiement étudiant.

Pourquoi : l'ancien bouton « Signaler abus » affichait uniquement un toast dans
React. Aucun dossier n'était enregistré et l'administrateur ne recevait rien.
L'arbitrage financier et la sanction disciplinaire sont maintenant séparés.

Tests : `BusinessWorkflowIntegrationTest` vérifie les rôles, le doublon, la
notification d'avertissement et l'absence de mutation de la commande et du
litige.

## Synchronisation des avis avec Catalog — 6 juillet 2026

- Après création d'un avis, Business recalcule la moyenne et le nombre d'avis
  visibles du gig, puis les transmet à Catalog avec le jeton inter-service.
- Une modération `HIDE` ou `DISMISS` déclenche le même recalcul afin que les
  cartes publiques n'affichent jamais un avis masqué.
- Si Catalog est indisponible, la création ou la modération échoue clairement
  au lieu de laisser deux statistiques contradictoires.

Pourquoi : le profil étudiant calculait déjà la note depuis les avis Business,
mais `/services` utilisait les champs `rating` et `reviewCount` non synchronisés
du gig Catalog.

Ce service est la source de vérité des commandes, livrables, révisions, avis et litiges. Il écoute par défaut sur le port `8084` et utilise la base `business_db` (`localhost:5434`).

## Statistiques, modération et auto-validation — 5 juillet 2026

- `GET /api/student-stats/{studentId}` calcule missions terminées, moyenne, avis visibles, temps moyen d'acceptation, XP, badge et prochain palier.
- `acceptedAt` rend le temps mesurable; l'historique incomplet affiche `Non mesuré`.
- L'étudiant concerné peut signaler un avis; le staff peut le conserver (`DISMISS`) ou le masquer (`HIDE`) sans suppression physique.
- Les avis masqués disparaissent du profil et des statistiques tout en restant auditables.
- Un ordonnanceur valide après 24 h, demande le payout à Payment et reprend les versements différés.
- `POST /api/orders/auto-validation/run` déclenche le même moteur côté staff.

Configuration : `ORDER_AUTO_VALIDATION_DELAY` (défaut `PT24H`) et `ORDER_AUTO_VALIDATION_SCAN_MS` (défaut `60000`).

Pourquoi : les statistiques avaient disparu, la modération n'agissait pas et l'auto-validation dépendait d'un bouton React à 72 h.

## Configuration

| Variable | Valeur locale | Rôle |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Port HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5434/business_db` | Base du service |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Profils fiables |
| `CATALOG_SERVICE_URL` | `http://localhost:8083` | Gig et palier commandés |
| `SUPPORT_SERVICE_URL` | `http://localhost:8086` | Notifications |
| `INTERNAL_SERVICE_TOKEN` | `change-this-internal-token` | Appels internes entre services |

En production, `INTERNAL_SERVICE_TOKEN` doit être une valeur longue, aléatoire et identique dans Business, Request, Payment et Support.

## Contrat utilisé par le frontend

- `POST /api/orders` : créer une commande depuis un gig, rôle `CLIENT`.
- `GET /api/orders/mine` : commandes du client connecté.
- `GET /api/orders/missions` : missions de l'étudiant connecté.
- `GET /api/orders/{id}` : détail réservé aux participants ou au staff.
- `PATCH /api/orders/{id}/status` : transition contrôlée du workflow.
- `POST /api/deliverables` et `/api/revisions` : livraison et demande de révision.
- `GET/POST /api/reviews/**` : avis publics et création après commande terminée.
- `GET/POST/PATCH /api/disputes/**` : ouverture, réponse et arbitrage d'un litige.

La route interne `POST /api/orders/internal/from-proposal` est appelée par Request Service lorsqu'un client accepte une proposition. Elle est idempotente grâce à `sourceProposalId`. `GET /api/orders/internal/{id}` fournit à Payment et Support un contexte fiable, et `POST /api/orders/internal/{id}/payment-held` confirme le séquestre. Ces routes ne sont pas exposées par la Gateway.

## Modifications d'intégration du 4 juillet 2026

- Identifiants `Long` remplacés par des `UUID`, compatibles avec Identity, Request et React.
- Table renommée `business_orders` afin de ne pas convertir silencieusement une ancienne table utilisant des identifiants numériques.
- Identité, prix, étudiant et contenu du gig ne sont plus acceptés comme données fiables du navigateur : ils viennent du JWT, d'Identity et de Catalog.
- Création automatique et idempotente d'une commande après acceptation d'une proposition.
- Contrôle du rôle, de la propriété et des transitions de statut dans le service, en plus de la Gateway.
- Une commande passe de `pending` à `accepted` seulement quand Payment confirme que les fonds sont détenus ; l'étudiant ne peut pas démarrer avant.
- Ajout des livrables, révisions, avis et litiges attendus par le frontend.
- Notifications envoyées à Support Service lors des changements importants ; leur échec ne corrompt pas la transaction métier.
- Anciennes routes et anciens champs risqués conservés en commentaires dans le code pour rendre la migration visible à l'équipe.
- Parent Maven migré vers Spring Boot `4.1.0`, Java `21`, starter REST Client et H2 de test ajoutés.

Pourquoi : Supabase réalisait auparavant plusieurs écritures indépendantes côté navigateur. Le workflow est maintenant atomique, autorisé côté serveur et chaque donnée sensible possède une seule source de vérité.

## Validation

```powershell
mvn clean test
```

Les tests couvrent la création sécurisée depuis un gig, l'idempotence depuis une proposition, le workflow de commande et la résolution d'un litige.
