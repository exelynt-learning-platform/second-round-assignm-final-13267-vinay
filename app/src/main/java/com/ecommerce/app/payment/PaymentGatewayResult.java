package com.ecommerce.app.payment;

import lombok.Builder;
import lombok.Getter;

/**
 * Value object returned from a gateway strategy after initiating payment.
 */
@Getter
@Builder
public class PaymentGatewayResult {

    /** Transaction/payment ID assigned by the external gateway. */
    private final String transactionId;

    /**
     * URL or client secret for the frontend to complete payment.
     * — Stripe: the PaymentIntent client_secret
     * — PayPal: the approval redirect URL
     * Nullable if the gateway doesn't require frontend interaction.
     */
    private final String approvalUrl;
}
