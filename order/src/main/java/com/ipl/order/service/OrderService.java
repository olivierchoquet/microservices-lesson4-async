package com.ipl.order.service;

import com.ipl.order.api.dto.OrderDtos;
import com.ipl.order.api.dto.OrderView;
import com.ipl.order.domain.ClientOrder;
import com.ipl.order.domain.OrderItem;
import com.ipl.order.domain.OrderStatus;
import com.ipl.order.external.ClientClient;
import com.ipl.order.messaging.RabbitConfig;                                  // +++ AJOUT
import com.ipl.order.messaging.events.OrderEvents.OrderCreatedEvent;          // +++ AJOUT
import com.ipl.order.messaging.events.OrderEvents.StockReservedEvent;         // +++ AJOUT
import com.ipl.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;                   // +++ AJOUT
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/*
 * --- SUPPRIMES (imports devenus inutiles) :
 *     com.ipl.order.external.ProductClient
 *     com.ipl.order.external.ProductClient.ProductDto
 *     java.util.LinkedHashMap
 *     java.util.Map
 *
 * La classe ProductClient elle-meme a ete supprimee du projet.
 */
@Service
public class OrderService {

    private final OrderRepository repo;
    private final ClientClient clientClient;   // CONSERVE : la validation du client reste synchrone
    private final RabbitTemplate rabbit;       // +++ AJOUT : remplace productClient

    // --- SUPPRIME : private final ProductClient productClient;

    // ~~~ MODIFIE : le constructeur recoit le RabbitTemplate a la place du ProductClient
    public OrderService(OrderRepository r, ClientClient cc, RabbitTemplate rabbit) {
        this.repo = r;
        this.clientClient = cc;
        this.rabbit = rabbit;
    }

    // =========================================================
    // LECTURES : INCHANGEES
    // Passer en asynchrone ne veut pas dire tout remplacer.
    // Seules les ecritures a effet de bord distant changent.
    // =========================================================

    public OrderView get(Long id) {
        ClientOrder o = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order " + id + " not found"));
        return toView(o);
    }

    public List<OrderView> all() {
        return repo.findAll().stream().map(this::toView).collect(Collectors.toList());
    }

    public List<OrderView> byClient(Long clientId) {
        return repo.findByClientId(clientId).stream().map(this::toView).collect(Collectors.toList());
    }

    // =========================================================
    // CREATION : c'est ici que tout change
    // =========================================================

    @Transactional
    public OrderView create(OrderDtos.Create req) {

        // CONSERVE : le client doit exister. Lecture sans effet de bord,
        // rapide, dont l'echec doit etre immediat -> elle reste synchrone.
        ClientClient.ClientDto client = clientClient.getClient(req.clientId);

        /* --- SUPPRIME : phase 1, validation des produits
         *
         *   Map<Long, ProductDto> products = new LinkedHashMap<>();
         *   for (OrderDtos.Item it : req.items) {
         *       ProductClient.ProductDto p = productClient.getProduct(it.productId);
         *       if (p.stock < it.quantity) throw new RuntimeException("INSUFFICIENT_STOCK");
         *       products.put(it.productId, p);
         *   }
         *
         * --- SUPPRIME : phase 2, reservation
         *
         *   for (OrderDtos.Item it : req.items) {
         *       ProductClient.ProductDto p = productClient.getProduct(it.productId);
         *       productClient.reserve(p.id, it.quantity);
         *       order.addItem(new OrderItem(p.id, p.name, it.quantity, p.price));
         *   }
         *
         * Ces deux boucles coutaient 3N appels HTTP bloquants et pouvaient
         * laisser du stock reserve pour une commande jamais creee.
         * Product-service fait desormais les deux en UNE transaction locale.
         */

        ClientOrder order = new ClientOrder(client.id);   // le constructeur met status = PENDING

        // ~~~ MODIFIE : nom et prix inconnus a ce stade, on ne les invente pas.
        // Ils arriveront dans StockReservedEvent.
        for (OrderDtos.Item it : req.items) {
            order.addItem(new OrderItem(it.productId, null, it.quantity, null));
        }

        ClientOrder saved = repo.save(order);

        /*
         * +++ AJOUT : publier au lieu d'appeler.
         *
         * order-service ne sait plus que product-service existe. Il connait
         * un nom d'exchange et une routing key, rien d'autre. C'est tout
         * l'interet du broker : le publisher ne nomme aucun destinataire.
         */
        rabbit.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RK_ORDER_CREATED,
                new OrderCreatedEvent(
                        saved.getId(),
                        saved.getClientId(),
                        req.items.stream()
                                .map(i -> new OrderCreatedEvent.Line(i.productId, i.quantity))
                                .collect(Collectors.toList())));

        // La commande part en PENDING : le stock n'est pas encore reserve.
        return toView(saved);
    }

    // =========================================================
    // +++ AJOUT : les transitions declenchees par les evenements
    // =========================================================

    /*
     * IDEMPOTENCE : on ne change le statut que si la commande est encore
     * PENDING. Si le broker redelivre le meme message, le deuxieme passage
     * ne fait rien. Indispensable : RabbitMQ garantit at-least-once,
     * jamais exactly-once.
     */
    @Transactional
    public void confirm(StockReservedEvent event) {
        repo.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PENDING) {
                return;   // deja traite, on ne rejoue pas
            }
            // Completer les lignes avec le nom et le prix transportes par l'evenement
            for (StockReservedEvent.PricedLine line : event.lines()) {
                order.getItems().stream()
                        .filter(i -> i.getProductId().equals(line.productId()))
                        .filter(i -> i.getUnitPrice() == null)
                        .findFirst()
                        .ifPresent(i -> i.price(line.productName(), line.unitPrice()));
            }
            order.recomputeTotal();
            order.setStatus(OrderStatus.CONFIRMED);
        });
    }

    @Transactional
    public void reject(Long orderId, String reason) {
        repo.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.REJECTED);
            }
        });
    }

    // ~~~ MODIFIE : le statut est desormais expose dans la vue
    private OrderView toView(ClientOrder o) {
        List<OrderView.Line> lines = o.getItems().stream()
                .map(li -> new OrderView.Line(
                        li.getProductId(),
                        li.getProductName(),
                        li.getQuantity(),
                        li.getUnitPrice(),
                        li.getLineTotal()))
                .collect(Collectors.toList());
        return new OrderView(o.getId(), o.getClientId(), o.getStatus().name(),
                o.getCreatedAt(), o.getTotal(), lines);
    }
}
