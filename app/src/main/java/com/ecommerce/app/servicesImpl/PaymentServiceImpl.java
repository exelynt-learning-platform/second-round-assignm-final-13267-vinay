package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.response.PaymentResponse;
import com.ecommerce.app.models.Order;
import com.ecommerce.app.models.Payment;
import com.ecommerce.app.models.enums.OrderStatus;
import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.models.enums.PaymentStatus;
import com.ecommerce.app.exception.AccessDeniedException;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.payment.PaymentGatewayFactory;
import com.ecommerce.app.payment.PaymentGatewayResult;
import com.ecommerce.app.payment.PaymentGatewayStrategy;
import com.ecommerce.app.repository.OrderRepository;
import com.ecommerce.app.repository.PaymentRepository;
import com.ecommerce.app.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository    paymentRepository;
    private final OrderRepository      orderRepository;
    private final PaymentGatewayFactory gatewayFactory;

    // Initiate payment — idempotent: if a payment with the same key exists,
    // return it instead of creating a new one.
    @Override
    @Transactional
    public PaymentResponse initiatePayment(Long userId, Long orderId,
                                           PaymentGateway gateway, String idempotencyKey) {

        // 1. Idempotency check — return existing payment if found
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent hit: returning existing payment for key={}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        // 2. Load and validate the order
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Ownership check
        if (!order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to pay for this order");
        }

        // Guard: only CREATED orders can be paid
        if (order.getOrderStatus() != OrderStatus.CREATED) {
            throw new BadRequestException(
                    "Order cannot be paid in its current status: " + order.getOrderStatus());
        }

        // Guard: prevent duplicate payment records (by orderId)
        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getStatus() != PaymentStatus.FAILED) {
                throw new BadRequestException("A payment record already exists for this order");
            }
        });

        // 3. Call the gateway via Strategy Pattern (OCP — no if/else chain)
        PaymentGatewayStrategy strategy = gatewayFactory.getStrategy(gateway);
        PaymentGatewayResult result = strategy.createPayment(order, "USD", idempotencyKey);

        // 4. Persist the payment record
        Payment payment = Payment.builder()
                .order(order)
                .paymentGateway(gateway)
                .paymentId(result.getTransactionId())
                .amount(order.getTotalPrice())
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: orderId={}, gateway={}, txn={}, idempotencyKey={}",
                orderId, gateway, result.getTransactionId(), idempotencyKey);

        return toResponse(saved, result.getApprovalUrl());
    }

    // Webhook handler — called by Stripe/PayPal after async charge result
    @Override
    @Transactional
    public void handleWebhook(String gatewayPaymentId, boolean success, String failureReason) {

        Payment payment = paymentRepository.findByPaymentId(gatewayPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment", "paymentId", gatewayPaymentId));

        Order order = payment.getOrder();

        if (success) {
            payment.setStatus(PaymentStatus.PAID);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setOrderStatus(OrderStatus.CONFIRMED);
            log.info("Payment successful: orderId={}, txn={}", order.getId(), gatewayPaymentId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            order.setPaymentStatus(PaymentStatus.FAILED);
            log.warn("Payment failed: orderId={}, txn={}, reason={}",
                    order.getId(), gatewayPaymentId, failureReason);
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    // Retrieve payment for an order
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to view this payment");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        return toResponse(payment);
    }

    // Mappers
    private PaymentResponse toResponse(Payment p) {
        return toResponse(p, null);
    }

    private PaymentResponse toResponse(Payment p, String approvalUrl) {
        return PaymentResponse.builder()
                .id(p.getId())
                .orderId(p.getOrder().getId())
                .paymentGateway(p.getPaymentGateway())
                .paymentId(p.getPaymentId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .failureReason(p.getFailureReason())
                .approvalUrl(approvalUrl)
                .createdAt(p.getCreatedAt())
                .build();
    }
}