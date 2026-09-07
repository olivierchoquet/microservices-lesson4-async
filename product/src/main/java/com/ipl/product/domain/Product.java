package com.ipl.product.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true)
    private String name;

    @Column(nullable=false, precision=12, scale=2)
    private BigDecimal price;

    @Column(nullable=false)
    private Integer stock;

    @Version
    private Long version;

    public Product() {}

    public Product(String name, BigDecimal price, Integer stock){
        this.name=name;
        this.price=price;
        this.stock=stock;
    }

    public Long getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public void setName(String n){
        this.name=n;
    }

    public BigDecimal getPrice(){
        return price;
    }

    public void setPrice(BigDecimal p){
        this.price=p;
    }

    public Integer getStock(){
        return stock;
    }

    public void setStock(Integer s){
        this.stock=s;
    }
}
