package com.ipl.product.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * Trace des commandes deja traitees, pour rendre le consumer idempotent.
 *
 * Pourquoi une table et pas un Set en memoire : le Set serait vide au
 * redemarrage du service, et un message redelivre juste apres serait traite
 * une seconde fois. C'est precisement le cas que l'on veut couvrir.
 */
@Entity
public class ProcessedOrder {

    @Id
    private Long orderId;          // l'id de commande sert de cle : deux fois le meme = doublon

    private OffsetDateTime processedAt;

    public ProcessedOrder() {}

    public ProcessedOrder(Long orderId) {
        this.orderId = orderId;
        this.processedAt = OffsetDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
