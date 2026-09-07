package com.ipl.order.messaging;

import com.ipl.order.messaging.events.OrderEvents.StockRejectedEvent;
import com.ipl.order.messaging.events.OrderEvents.StockReservedEvent;
import com.ipl.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * C'est ici que la commande sort de l'etat PENDING.
 * Ces methodes tournent sur un thread du listener container,
 * en dehors de la requete HTTP qui a cree la commande.
 *
 * ACQUITTEMENT : si la methode retourne normalement, Spring envoie un ack
 * et le broker supprime le message. Si elle leve une exception, Spring nack
 * et le message est redelivre. C'est la raison d'etre de l'idempotence.
 */
@Component
public class StockResultListener {

    private final OrderService service;

    public StockResultListener(OrderService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        service.confirm(event);
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_STOCK_REJECTED)
    public void onStockRejected(StockRejectedEvent event) {
        service.reject(event.orderId(), event.reason());
    }
}
