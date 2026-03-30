package com.ecommerce.app.repository;

import com.ecommerce.app.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    // Look up by the external gateway transaction ID (used in webhook callbacks)
    Optional<Payment> findByPaymentId(String paymentId);

    // Look up by idempotency key (used to prevent duplicate payments on retries)
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}