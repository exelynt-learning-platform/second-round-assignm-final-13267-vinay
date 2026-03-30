package com.ecommerce.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderRequest {

    @NotBlank(message = "Shipping address is required")
    @Size(max = 500)
    private String shippingAddress;

    @NotNull(message = "Payment gateway is required")
    private String paymentGateway;   // "STRIPE" or "PAYPAL"
}