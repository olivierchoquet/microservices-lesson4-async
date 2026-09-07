# Microservices Lesson 3 — Spring Boot + RabbitMQ (version asynchrone)

Version **asynchrone** de la solution microservices, obtenue en remplaçant les appels HTTP bloquants
entre Order et Product par des événements RabbitMQ.

**Architecture :** API Gateway → Client (HTTP sync) · Order ⇄ Product (événements AMQP)

Chaque service garde sa base H2. Le passage de la version synchrone à celle-ci est détaillé
fichier par fichier dans **[MIGRATION.md](MIGRATION.md)**.

---

## 💻 Prérequis

- **JDK 17**
- **Maven**
- **Docker** (pour RabbitMQ)

## 🧱 Modules

```
microservices-lesson3/
├─ pom.xml           # POM parent
├─ api-gateway/      # Spring Cloud Gateway   (8080)
├─ product/          # Product Service        (8081)
├─ client/           # Client Service         (8082)
└─ order/            # Order Service          (8083)
```

---

## 🚀 Démarrage

### 1) Le broker

```bash
docker compose up -d
```

Console de management : <http://localhost:15672> — `guest` / `guest`

Le port **5672** est celui qu'utilisent les services Spring (AMQP). Le **15672** est celui de la console web.

### 2) Les services

Dans IntelliJ, lancer les quatre classes `main` :

- `ProductServiceApplication` → 8081
- `ClientServiceApplication` → 8082
- `OrderServiceApplication` → 8083
- `ApiGatewayApplication` → 8080

L'ordre n'a plus d'importance entre Order et Product : c'est justement l'intérêt du broker.

Au démarrage, Spring déclare l'exchange, les queues et les bindings. Rafraîchir la console pour les voir apparaître.

---

## 📨 Topologie des messages

```
order-service   --[ order.created ]-->   orders.exchange --> order.created.queue   --> product-service

product-service --[ stock.reserved ]-->  orders.exchange --> stock.reserved.queue  --> order-service
                --[ stock.rejected ]-->                  --> stock.rejected.queue  -->
```

| Élément | Valeur |
|---|---|
| Exchange | `orders.exchange` (type `topic`) |
| Routing keys | `order.created`, `stock.reserved`, `stock.rejected` |
| Queues | `order.created.queue`, `stock.reserved.queue`, `stock.rejected.queue` |

---

## 🔌 API (via la Gateway sur 8080)

### Clients

- `GET /clients` · `GET /clients/{id}` · `POST /clients` · `PUT /clients/{id}` · `DELETE /clients/{id}`

```json
{ "name": "Alice", "address": "Brussels" }
```

### Products

- `GET /products` · `GET /products/{id}` · `POST /products` · `PUT /products/{id}` · `DELETE /products/{id}`

```json
{ "name": "Keyboard", "price": 49.90, "stock": 10 }
```

`POST /products/{id}/reserve` existe toujours mais **n'est plus appelé par Order**. Il reste disponible pour les tests manuels.

### Orders

`POST /orders` → **202 Accepted**

```json
{
  "clientId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

- `GET /orders` · `GET /orders?clientId=1` · `GET /orders/{id}`

### Cycle de vie d'une commande

| Statut | Signification |
|---|---|
| `PENDING` | Commande acceptée, stock pas encore réservé |
| `CONFIRMED` | Product a réservé le stock |
| `REJECTED` | Stock insuffisant ou produit inexistant |

---

## 🧪 Tester

### Données préchargées

```bash
curl http://localhost:8080/clients
curl http://localhost:8080/products
```

Les bases sont en mémoire : redémarrer un service le remet dans l'état de son `data.sql`.

### Cas nominal

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"clientId":1,"items":[{"productId":1,"quantity":2}]}'
```

**Attendu : `202 Accepted`** avec `"status":"PENDING"`, un total à 0 et des prix `null`.

Puis relire la commande :

```bash
curl http://localhost:8080/orders/1
```

**Attendu :** `"status":"CONFIRMED"`, nom, prix et total remplis.

C'est la **cohérence à terme** : la réponse est correcte, elle n'est simplement pas immédiate.

### Stock insuffisant

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"clientId":1,"items":[{"productId":2,"quantity":9999}]}'
```

**Attendu : `202`** puis `"status":"REJECTED"`. Un rejet métier n'est plus un code HTTP, c'est un événement.

### Client inexistant

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"clientId":999,"items":[{"productId":1,"quantity":1}]}'
```

**Attendu : une erreur immédiate.** La validation du client est restée synchrone : passer à l'asynchrone ne veut pas dire tout remplacer.

### Découplage temporel

```bash
# 1. arrêter product-service
# 2. passer trois commandes
# 3. les trois répondent 202, statut PENDING
# 4. console RabbitMQ : order.created.queue affiche Ready = 3
# 5. redémarrer product-service
# 6. les trois passent en CONFIRMED d'un coup
```

En synchrone, les trois auraient échoué. C'est la démonstration principale de la séance.

### Idempotence

Depuis la console RabbitMQ, publier à la main sur `orders.exchange` avec la routing key `order.created`
et le **même `orderId`** qu'une commande déjà traitée :

```json
{ "orderId": 1, "clientId": 1, "items": [{ "productId": 1, "quantity": 2 }] }
```

**Attendu :** le stock ne bouge pas une seconde fois. La table `ProcessedOrder` fait barrage.

RabbitMQ garantit *at-least-once*, jamais *exactly-once* : c'est au consumer d'encaisser la redélivrance.

---

## H2 consoles (dev)

- Product : <http://localhost:8081/h2-console>
- Client : <http://localhost:8082/h2-console>
- Order : <http://localhost:8083/h2-console>

Les URL JDBC sont dans les `application.yml` respectifs.

---

## 🧠 Notes de conception

- **Publier, pas appeler.** Order ne connaît plus l'adresse de Product : il connaît un nom d'exchange et une routing key.
- **Une queue par consommateur.** Les messages d'une queue sont *répartis* entre ses consommateurs, jamais dupliqués. Une queue par service, autant de bindings que de raisons de le déclencher.
- **Consumer idempotent.** Redélivrance possible à tout moment ; la table `ProcessedOrder` évite la double décrémentation.
- **Erreur métier ≠ erreur technique.** La première est attrapée et publiée en `stock.rejected` ; la seconde remonte et déclenche un réessai.
- **Transaction locale.** La réservation multi-lignes se fait dans Product, en une transaction : la réservation partielle devient impossible.
