package com.ipl.product.messaging;

import com.ipl.product.messaging.events.OrderEvents.OrderCreatedEvent;
import com.ipl.product.messaging.events.OrderEvents.StockRejectedEvent;
import com.ipl.product.messaging.events.OrderEvents.StockReservedEvent;
import com.ipl.product.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * Remplace l'endpoint POST /products/{id}/reserve appele en boucle par
 * order-service. Un seul message porte TOUTE la commande, la ou la version
 * synchrone faisait deux appels HTTP par ligne.
 */
@Component
public class OrderCreatedListener {

    private final ProductService service;
    private final RabbitTemplate rabbit;

    public OrderCreatedListener(ProductService service, RabbitTemplate rabbit) {
        this.service = service;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {

        /*
         * IDEMPOTENCE. RabbitMQ garantit at-least-once : si ce service plante
         * apres avoir decremente le stock mais avant l'acquittement, le broker
         * redelivre le message. Sans cette garde, le stock serait decremente
         * deux fois pour la meme commande.
         */
        if (service.alreadyProcessed(event.orderId())) {
            return;   // retour normal = ack = le message quitte la queue
        }

        try {
            List<StockReservedEvent.PricedLine> lines =
                    service.reserveAll(event.orderId(), event.items());

            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_STOCK_RESERVED,
                    new StockReservedEvent(event.orderId(), lines));

        } catch (IllegalStateException | EntityNotFoundException e) {
            /*
             * ERREUR METIER : stock insuffisant ou produit inexistant.
             * Ce n'est pas une panne : rejouer ne changerait rien. On repond
             * par un evenement de rejet et on acquitte le message.
             *
             * Une erreur TECHNIQUE (base indisponible, broker injoignable)
             * n'est volontairement PAS attrapee ici : elle remonte, Spring
             * nack le message, et il sera reessaye. Confondre les deux donne
             * soit des boucles infinies, soit des messages perdus.
             */
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_STOCK_REJECTED,
                    new StockRejectedEvent(event.orderId(), e.getMessage()));
        }
    }
}
