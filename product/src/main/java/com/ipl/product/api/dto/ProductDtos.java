package com.ipl.product.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ProductDtos {
    public static class Create {
        @NotBlank
        public String name;

        @NotNull
        public BigDecimal price;

        @Min(0)
        @NotNull
        public Integer stock;
    }

    public static class Update extends Create {}

    public static class Reserve {
        @NotNull
        @Min(1)
        public Integer quantity;
    }

    public static class View {
        public Long id;
        public String name;
        public BigDecimal price;
        public Integer stock;

        public View(){}

        public View(Long id,String n,BigDecimal p,Integer s){
            this.id=id;
            this.name=n;
            this.price=p;
            this.stock=s;
        }
    }
}
