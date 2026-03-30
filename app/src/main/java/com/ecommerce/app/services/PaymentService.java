package com.ecommerce.app.services;

import com.ecommerce.app.dto.response.PaymentResponse;
import com.ecommerce.app.models.enums.PaymentGateway;

public interface PaymentService {

    // Initiate a payment for an order and return checkout/client-secret URL
    PaymentResponse initiatePayment(Long userId, Long orderId, PaymentGateway gateway, String idempotencyKey);

    // Called by webhook after Stripe/PayPal confirms or rejects the charge
    void handleWebhook(String gatewayPaymentId, boolean success, String failureReason);

    PaymentResponse getPaymentByOrder(Long userId, Long orderId);
}