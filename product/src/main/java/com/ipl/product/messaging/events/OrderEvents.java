package com.ipl.product.messaging.events;

import java.math.BigDecimal;
import java.util.List;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * Copie a l'identique de com.ipl.order.messaging.events.OrderEvents,
 * au nom de package pres. Les noms de champs doivent correspondre
 * exactement : c'est ce qui permet a Jackson de relire le JSON.
 *
 * La difference de package n'est pas un probleme grace au type mapper
 * configure en INFERRED dans RabbitConfig.
 *
 * Duplication assumee pour le cours. En production : un module Maven
 * partage, ou un contrat versionne (Avro, JSON Schema).
 */
public class OrderEvents {

    public record OrderCreatedEvent(Long orderId, Long clientId, List<Line> items) {
        public record Line(Long productId, Integer quantity) {}
    }

    public record StockReservedEvent(Long orderId, List<PricedLine> lines) {
        public record PricedLine(Long productId, String productName,
                                 Integer quantity, BigDecimal unitPrice) {}
    }

    public record StockRejectedEvent(Long orderId, String reason) {}
}
