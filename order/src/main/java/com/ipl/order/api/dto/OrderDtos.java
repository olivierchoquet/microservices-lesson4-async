package com.ipl.order.api.dto;
import jakarta.validation.constraints.*; import java.util.List;

public class OrderDtos {
    public static class Item {
        @NotNull
        public Long productId;

        @NotNull
        @Min(1)
        public Integer quantity;
    }

    public static class Create {
        @NotNull
        public Long clientId;

        @NotNull
        public List<Item> items;
    }
}
