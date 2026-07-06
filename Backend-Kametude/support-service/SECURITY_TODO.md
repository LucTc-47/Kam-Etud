# SECURITY_TODO — support-service

## Limite connue : `GET /api/chat/orders/{orderId}/messages`

**Statut actuel :** l'endpoint exige un utilisateur authentifié (header `X-User-Id`,
sera injecté par api-gateway une fois le filtre JWT en place). Un appel anonyme
est rejeté avec `400 Bad Request`.

**Ce qui n'est PAS encore vérifié :** que `X-User-Id` correspond bien à un
participant (acheteur ou vendeur) de la commande `orderId` demandée. Aujourd'hui,
n'importe quel utilisateur authentifié peut lire l'historique de chat de
n'importe quelle commande, même s'il n'y participe pas.

**Pourquoi ce n'est pas corrigé maintenant :** `support-service` ne connaît pas
les participants d'une commande — cette information vit dans `business-service`.
Vérifier l'appartenance nécessite un appel inter-service (OpenFeign, cf.
architecture du projet).

**Action à faire, avant la PR finale ou en PR de suivi :**
1. Vérifier avec l'équipe `business-service` si un endpoint existe (ou peut être
   créé) pour répondre à : "l'utilisateur X est-il acheteur ou vendeur de la
   commande Y ?"
2. Ajouter un client Feign dans `support-service` vers cet endpoint
3. Dans `ChatController.getHistory()`, appeler ce client et renvoyer `403
   Forbidden` si l'utilisateur n'est pas participant

**Fichier concerné :** `controller/ChatController.java` (commentaire TODO déjà
présent dans le code)
