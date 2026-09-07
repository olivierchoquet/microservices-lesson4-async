package com.ipl.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private ClientOrder order;

    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItem() {}

    /*
     * ~~~ MODIFIE : le constructeur tolere desormais un prix null.
     *
     * En synchrone, order-service connaissait toujours le prix : il venait
     * d'appeler product-service. En asynchrone, la ligne est creee AVANT que
     * product-service ait repondu, donc nom et prix sont inconnus.
     * L'ancien code faisait unitPrice.multiply(...) et levait une
     * NullPointerException.
     */
    public OrderItem(Long productId, String productName, Integer qty, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = qty;
        this.unitPrice = unitPrice;
        this.lineTotal = (unitPrice == null || qty == null)
                ? null
                : unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    public void setOrder(ClientOrder o) { this.order = o; }

    /*
     * +++ AJOUT : completer la ligne a la confirmation.
     * StockReservedEvent transporte le nom et le prix, on les recopie ici.
     */
    public void price(String productName, BigDecimal unitPrice) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.lineTotal = (unitPrice == null || quantity == null)
                ? null
                : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
