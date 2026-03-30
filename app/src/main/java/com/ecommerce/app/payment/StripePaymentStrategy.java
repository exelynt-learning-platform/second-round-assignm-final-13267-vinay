package com.ecommerce.app.payment;

import com.ecommerce.app.exception.PaymentException;
import com.ecommerce.app.models.Order;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stripe implementation of the PaymentGatewayStrategy.
 *
 * Creates a PaymentIntent using the Stripe Java SDK.
 * The client_secret is returned as the approvalUrl so the frontend
 * can use Stripe.js / Stripe Elements to confirm the payment.
 */
@Slf4j
@Component
public class StripePaymentStrategy implements PaymentGatewayStrategy {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Override
    public PaymentGatewayResult createPayment(Order order, String currency, String idempotencyKey) {
        try {
            Stripe.apiKey = stripeApiKey;

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(order.getTotalPrice().movePointRight(2).longValueExact()) // cents
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("order_id", order.getId().toString())
                    .putMetadata("idempotency_key", idempotencyKey)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            log.info("Stripe PaymentIntent created: id={}, orderId={}", intent.getId(), order.getId());

            return PaymentGatewayResult.builder()
                    .transactionId(intent.getId())
                    .approvalUrl(intent.getClientSecret())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe API error for orderId={}: {}", order.getId(), e.getMessage(), e);
            throw new PaymentException("Stripe payment creation failed: " + e.getMessage(), e);
        }
    }
}
