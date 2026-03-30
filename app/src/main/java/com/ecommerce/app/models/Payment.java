package com.ecommerce.app.models;

import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.models.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One-to-One: Payment -> Order (Payment owns the FK)
    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_gateway", nullable = false, length = 20)
    private PaymentGateway paymentGateway;

    // External transaction ID returned by Stripe / PayPal after charge creation
    @Column(name = "payment_id", length = 200)
    private String paymentId;

    @NotNull
    @Positive(message = "Payment amount must be greater than zero")
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // ISO 4217 currency code, e.g. "USD", "EUR"
    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // Populated on payment failure — stores gateway error code/message for debugging
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Idempotency key to prevent duplicate payment initiation on retries
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}