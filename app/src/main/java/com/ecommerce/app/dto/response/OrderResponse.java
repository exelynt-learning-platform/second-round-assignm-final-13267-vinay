package com.ecommerce.app.dto.response;

import com.ecommerce.app.models.enums.OrderStatus;
import com.ecommerce.app.models.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long            id;
    private BigDecimal      totalPrice;
    private String          shippingAddress;
    private OrderStatus     orderStatus;
    private PaymentStatus   paymentStatus;
    private LocalDateTime   createdAt;
    private LocalDateTime   updatedAt;
    private List<OrderItemResponse> items;

    @Data
    @Builder
    public static class OrderItemResponse {
        private Long       productId;
        private String     productName;
        private Integer    quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
}