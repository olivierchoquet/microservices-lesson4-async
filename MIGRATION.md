# Du synchrone à l'asynchrone — ce qui change

Chaque fichier touché porte des marqueurs dans ses commentaires :
`+++ AJOUT`, `--- SUPPRIMÉ`, `~~~ MODIFIÉ`, `CONSERVÉ`, `INCHANGÉ`.

Le point de départ est la version synchrone où `OrderService.create()` appelait Product en HTTP bloquant, ligne par ligne.

---

## Vue d'ensemble

**Avant.** Order appelait Product pour chaque ligne et attendait chaque réponse. Coût : 1 + 3N appels HTTP. Risque : du stock réservé pour une commande jamais créée.

**Après.** Order enregistre la commande en `PENDING`, publie **un** événement, répond immédiatement. Product consomme, réserve tout le panier en une transaction locale, publie le résultat. Order écoute et fait passer la commande en `CONFIRMED` ou `REJECTED`.

```
order-service   --[ order.created ]-->   orders.exchange --> order.created.queue   --> product-service

product-service --[ stock.reserved ]-->  orders.exchange --> stock.reserved.queue  --> order-service
                --[ stock.rejected ]-->                  --> stock.rejected.queue  -->
```

---

## Fichiers nouveaux

| Service | Fichier | Rôle |
|---|---|---|
| order | `messaging/events/OrderEvents.java` | Les trois événements |
| order | `messaging/RabbitConfig.java` | Exchange, queues, bindings, converter |
| order | `messaging/StockResultListener.java` | Transitions PENDING → CONFIRMED / REJECTED |
| product | `messaging/events/OrderEvents.java` | Copie, package différent |
| product | `messaging/RabbitConfig.java` | Queue de consommation, binding, converter |
| product | `messaging/OrderCreatedListener.java` | Réserve puis publie le résultat |
| product | `domain/ProcessedOrder.java` | Trace d'idempotence |
| product | `repository/ProcessedOrderRepository.java` | — |

## Fichier supprimé

`order/external/ProductClient.java`. C'était un `@Component` avec `@Value("${external.product-service}")` : le garder après avoir retiré la propriété empêchait le démarrage.

## Fichiers modifiés

| Fichier | Changement |
|---|---|
| `order/OrderService.java` | Les deux boucles HTTP remplacées par une publication ; ajout de `confirm()` et `reject()` |
| `order/OrderStatus.java` | `CONFIRMED` et `REJECTED` ajoutés ; `CREATED` et `APPROVED` supprimés |
| `order/ClientOrder.java` | Ajout de `getStatus()` (**il n'existait pas**), `setStatus()`, `recomputeTotal()` |
| `order/OrderItem.java` | Constructeur rendu tolérant au prix `null` ; ajout de `price()` |
| `order/OrderView.java` | Ajout du champ `status` |
| `order/OrderController.java` | `201 Created` → `202 Accepted` |
| `product/ProductService.java` | Ajout de `reserveAll()` et `alreadyProcessed()` |
| `*/application.yml` | Bloc `spring.rabbitmq` ; suppression de `external.product-service` |
| `*/pom.xml` | Ajout de `spring-boot-starter-amqp` |

---

## Cinq décisions à comprendre

### 1. La réservation partielle disparaît, et ce n'est pas grâce à RabbitMQ

`reserveAll()` traite tout le panier dans **une transaction locale** de product-service. Si la dernière ligne échoue, la base annule les précédentes.

Aucune correction côté Order ne pouvait donner cette garantie : `@Transactional` ne sait pas annuler un appel HTTP déjà parti. Ce qui change, c'est que l'opération vit désormais **là où vivent les données**.

`reserveAll()` agrège aussi les quantités par produit, ce qui corrige le bug des lignes en double — deux lignes de 30 sur un stock de 50 passaient toutes les deux la validation.

### 2. Erreur métier et erreur technique se traitent à l'opposé

Dans `OrderCreatedListener` :

- `IllegalStateException` et `EntityNotFoundException` sont **attrapées** → publication de `StockRejected`, message acquitté. Rejouer ne changerait rien.
- Toute autre exception **remonte** → Spring nack, le message est réessayé. C'est une panne, ça peut réussir plus tard.

Confondre les deux donne soit des boucles infinies, soit des messages perdus.

### 3. Une queue par type d'événement, pas un `stock.*`

On aurait pu binder une seule queue sur `stock.*` pour capter les deux réponses. Mais deux `@RabbitListener` sur la même queue **se partagent** les messages au lieu de les filtrer par type : la moitié arriverait sur la mauvaise méthode.

La queue est l'unité de travail, le binding est le filtre.

### 4. `TypePrecedence.INFERRED` est obligatoire ici

Par défaut, `Jackson2JsonMessageConverter` écrit le nom complet de la classe de l'expéditeur dans l'en-tête `__TypeId__`, et le destinataire essaie de charger cette classe. Nos deux services ont des packages différents (`com.ipl.order` et `com.ipl.product`), donc le chargement échouerait.

`INFERRED` dit à Spring d'ignorer l'en-tête et d'utiliser le type du paramètre de la méthode `@RabbitListener`.

### 5. La validation du client reste synchrone

`clientClient.getClient()` est conservé tel quel. C'est une lecture sans effet de bord, rapide, dont l'échec doit être immédiat.

Passer à l'asynchrone ne veut pas dire tout remplacer : seules les écritures avec effet de bord distant changent. Les lectures (`GET /orders`) ne bougent pas non plus.

---

## Tester

```bash
docker compose up -d          # RabbitMQ + console sur 15672
```

Démarrer Product (8081), Client (8082), Order (8083), Gateway (8080), puis :

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"clientId":1,"items":[{"productId":1,"quantity":2}]}'
```

→ **202 Accepted**, `"status":"PENDING"`, `total` à 0 et prix encore `null`.

```bash
curl http://localhost:8080/orders/1
```

→ `"status":"CONFIRMED"` quelques millisecondes plus tard, avec le nom, le prix et le total remplis par `StockReservedEvent`.

### Les trois démonstrations qui valent le détour

**Le découplage temporel.** Arrêter product-service, passer trois commandes : toutes acceptées en `PENDING`, les messages s'empilent dans `order.created.queue`. Redémarrer Product : les trois sont traitées d'un coup. La version synchrone aurait renvoyé trois erreurs.

**Le rejet métier.** Commander une quantité supérieure au stock : la commande est créée en `PENDING` puis passe en `REJECTED`. Un rejet n'est plus un code HTTP, c'est un événement.

**L'idempotence.** Republier le même `OrderCreatedEvent` à la main depuis la console RabbitMQ (exchange `orders.exchange`, routing key `order.created`, même `orderId`) : le stock ne bouge pas une seconde fois, grâce à la table `ProcessedOrder`.

---

## Ce qui reste ouvert

- Le catch-all `@ExceptionHandler(RuntimeException.class)` → 409 dans `OrderController` masque toujours les vrais bugs. Conservé volontairement pour que la comparaison avec la version synchrone reste lisible.
- Après trois tentatives, un message en échec est jeté. Le brancher sur une **dead letter queue** permettrait de l'inspecter.
- Rien ne prévient l'utilisateur du passage à `CONFIRMED` : polling, websocket ou e-mail.
- Les classes d'événements sont **dupliquées** dans les deux services. En production : un module Maven partagé ou un contrat versionné.
