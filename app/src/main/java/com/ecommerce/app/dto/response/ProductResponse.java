package com.ecommerce.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long          id;
    private String        name;
    private String        description;
    private BigDecimal    price;
    private Integer       stockQuantity;
    private String        imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}