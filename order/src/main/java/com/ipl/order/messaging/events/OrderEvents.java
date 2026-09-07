package com.ipl.order.messaging.events;

import java.math.BigDecimal;
import java.util.List;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * Les evenements echanges entre order-service et product-service.
 * Un evenement decrit un FAIT DEJA ACCOMPLI : on le nomme au passe.
 * OrderCreated, jamais CreateOrder (qui serait un ordre, pas un evenement).
 *
 * Ce fichier est duplique dans product-service (package different, memes
 * noms de champs). Voir RabbitConfig : le type mapper est configure en
 * INFERRED pour que la difference de package ne pose pas de probleme.
 */
public class OrderEvents {

    /** Publie par order-service, routing key "order.created". */
    public record OrderCreatedEvent(Long orderId, Long clientId, List<Line> items) {
        public record Line(Long productId, Integer quantity) {}
    }

    /*
     * Publie par product-service, routing key "stock.reserved".
     *
     * L'evenement transporte le nom et le prix des produits. C'est
     * volontaire : order-service n'appelle plus product-service, il ne
     * peut donc pas les connaitre autrement. On emporte la donnee avec
     * le message plutot que de rappeler la source ensuite.
     */
    public record StockReservedEvent(Long orderId, List<PricedLine> lines) {
        public record PricedLine(Long productId, String productName,
                                 Integer quantity, BigDecimal unitPrice) {}
    }

    /** Publie par product-service, routing key "stock.rejected". */
    public record StockRejectedEvent(Long orderId, String reason) {}
}
