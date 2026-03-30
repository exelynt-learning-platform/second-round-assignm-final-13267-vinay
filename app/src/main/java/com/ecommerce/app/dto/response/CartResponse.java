package com.ecommerce.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long               cartId;
    private List<CartItemResponse> items;
    private BigDecimal         total;      // computed: sum of (price × qty) for all items

    @Data
    @Builder
    public static class CartItemResponse {
        private Long       cartItemId;
        private Long       productId;
        private String     productName;
        private String     imageUrl;
        private BigDecimal unitPrice;
        private Integer    quantity;
        private BigDecimal subtotal;
    }
}