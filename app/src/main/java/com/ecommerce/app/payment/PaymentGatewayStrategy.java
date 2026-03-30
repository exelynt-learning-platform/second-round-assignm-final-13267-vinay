package com.ecommerce.app.payment;

import com.ecommerce.app.models.Order;

/**
 * Strategy interface for payment gateway operations.
 * Each gateway (Stripe, PayPal) provides its own implementation.
 *
 * Follows the Strategy Pattern (Open/Closed Principle):
 * — Adding a new gateway only requires a new implementation,
 *   no changes to existing code.
 */
public interface PaymentGatewayStrategy {

    /**
     * Creates a payment session/intent with the external gateway.
     *
     * @param order    the order being paid for
     * @param currency the ISO 4217 currency code (e.g. "USD")
     * @param idempotencyKey unique key to prevent duplicate charges on retry
     * @return result containing the gateway transaction ID and optional approval URL
     */
    PaymentGatewayResult createPayment(Order order, String currency, String idempotencyKey);
}
