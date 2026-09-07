package com.ipl.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
public class ClientOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private OffsetDateTime createdAt;

    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public ClientOrder() {}

    public ClientOrder(Long clientId) {
        this.clientId = clientId;
        this.status = OrderStatus.PENDING;   // INCHANGE, mais cela veut desormais dire quelque chose
        this.createdAt = OffsetDateTime.now();
        this.total = BigDecimal.ZERO;
    }

    /*
     * ~~~ MODIFIE : le total ne peut plus etre calcule a l'ajout, puisque
     * le prix est inconnu tant que product-service n'a pas repondu.
     * Il est recalcule par recomputeTotal() a la confirmation.
     */
    public void addItem(OrderItem i) {
        i.setOrder(this);
        items.add(i);
    }

    // +++ AJOUT : appele une fois les prix connus
    public void recomputeTotal() {
        this.total = items.stream()
                .map(OrderItem::getLineTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public BigDecimal getTotal() { return total; }
    public List<OrderItem> getItems() { return items; }

    // +++ AJOUT : le getter n'existait pas dans la version synchrone
    public OrderStatus getStatus() { return status; }

    // +++ AJOUT : indispensable, c'est le listener qui fait la transition
    public void setStatus(OrderStatus status) { this.status = status; }
}
