package com.ecommerce.app.dto.response;

import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.models.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private Long           id;
    private Long           orderId;
    private PaymentGateway paymentGateway;
    private String         paymentId;
    private BigDecimal     amount;
    private String         currency;
    private PaymentStatus  status;
    private String         failureReason;

    /**
     * URL or client secret for the frontend to complete payment:
     * — Stripe: the PaymentIntent client_secret (used with Stripe.js)
     * — PayPal: the approval redirect URL
     * Only populated on payment initiation, not on subsequent reads.
     */
    private String         approvalUrl;

    private LocalDateTime  createdAt;
}