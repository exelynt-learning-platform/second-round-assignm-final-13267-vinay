package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.response.PaymentResponse;
import com.ecommerce.app.exception.AccessDeniedException;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.Order;
import com.ecommerce.app.models.Payment;
import com.ecommerce.app.models.User;
import com.ecommerce.app.models.enums.OrderStatus;
import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.models.enums.PaymentStatus;
import com.ecommerce.app.payment.PaymentGatewayFactory;
import com.ecommerce.app.payment.PaymentGatewayResult;
import com.ecommerce.app.payment.PaymentGatewayStrategy;
import com.ecommerce.app.repository.OrderRepository;
import com.ecommerce.app.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock PaymentRepository     paymentRepository;
    @Mock OrderRepository       orderRepository;
    @Mock PaymentGatewayFactory gatewayFactory;

    @InjectMocks PaymentServiceImpl paymentService;

    private User  user;
    private Order order;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("alice@example.com").build();

        order = Order.builder()
                .id(100L).user(user)
                .totalPrice(new BigDecimal("999.99"))
                .orderStatus(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING)
                .shippingAddress("123 Main St")
                .orderItems(new ArrayList<>())
                .build();
    }

    // initiatePayment()
    @Nested
    @DisplayName("initiatePayment()")
    class InitiatePayment {

        @Test
        @DisplayName("should create PENDING payment record for STRIPE via strategy")
        void stripe_createsPendingPayment() {
            when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            // Mock the strategy factory + strategy
            PaymentGatewayStrategy mockStrategy = mock(PaymentGatewayStrategy.class);
            when(gatewayFactory.getStrategy(PaymentGateway.STRIPE)).thenReturn(mockStrategy);
            when(mockStrategy.createPayment(any(), eq("USD"), anyString()))
                    .thenReturn(PaymentGatewayResult.builder()
                            .transactionId("pi_test_123")
                            .approvalUrl("pi_secret_test")
                            .build());

            Payment saved = buildPayment(PaymentGateway.STRIPE, PaymentStatus.PENDING, null);
            saved.setPaymentId("pi_test_123");
            when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

            PaymentResponse res = paymentService.initiatePayment(
                    1L, 100L, PaymentGateway.STRIPE, "idempotency_key_1");

            assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(res.getPaymentGateway()).isEqualTo(PaymentGateway.STRIPE);
            assertThat(res.getAmount()).isEqualByComparingTo(new BigDecimal("999.99"));
            assertThat(res.getCurrency()).isEqualTo("USD");
            verify(gatewayFactory).getStrategy(PaymentGateway.STRIPE);
        }

        @Test
        @DisplayName("should create PENDING payment record for PAYPAL via strategy")
        void paypal_createsPendingPayment() {
            when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            PaymentGatewayStrategy mockStrategy = mock(PaymentGatewayStrategy.class);
            when(gatewayFactory.getStrategy(PaymentGateway.PAYPAL)).thenReturn(mockStrategy);
            when(mockStrategy.createPayment(any(), eq("USD"), anyString()))
                    .thenReturn(PaymentGatewayResult.builder()
                            .transactionId("paypal_order_123")
                            .approvalUrl("https://paypal.com/approve")
                            .build());

            Payment saved = buildPayment(PaymentGateway.PAYPAL, PaymentStatus.PENDING, null);
            saved.setPaymentId("paypal_order_123");
            when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

            PaymentResponse res = paymentService.initiatePayment(
                    1L, 100L, PaymentGateway.PAYPAL, "idempotency_key_2");

            assertThat(res.getPaymentGateway()).isEqualTo(PaymentGateway.PAYPAL);
            assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(gatewayFactory).getStrategy(PaymentGateway.PAYPAL);
        }

        @Test
        @DisplayName("should return existing payment when idempotency key matches (idempotent)")
        void idempotentHit_returnsExistingPayment() {
            Payment existing = buildPayment(PaymentGateway.STRIPE, PaymentStatus.PENDING, null);
            existing.setIdempotencyKey("idem_key_existing");
            when(paymentRepository.findByIdempotencyKey("idem_key_existing"))
                    .thenReturn(Optional.of(existing));

            PaymentResponse res = paymentService.initiatePayment(
                    1L, 100L, PaymentGateway.STRIPE, "idem_key_existing");

            assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
            // Should NOT call the gateway factory — no new payment created
            verifyNoInteractions(gatewayFactory);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user does not own the order")
        void wrongUser_throws() {
            User other = User.builder().id(99L).build();
            Order otherOrder = Order.builder()
                    .id(100L).user(other).orderStatus(OrderStatus.CREATED)
                    .totalPrice(BigDecimal.TEN).orderItems(new ArrayList<>()).build();

            when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(otherOrder));

            assertThatThrownBy(() ->
                    paymentService.initiatePayment(1L, 100L, PaymentGateway.STRIPE, null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should throw BadRequestException when order is not in CREATED status")
        void nonCreatedOrder_throws() {
            order.setOrderStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    paymentService.initiatePayment(1L, 100L, PaymentGateway.STRIPE, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("CONFIRMED");
        }

        @Test
        @DisplayName("should throw BadRequestException when active payment already exists")
        void duplicatePayment_throws() {
            when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));

            Payment existing = buildPayment(PaymentGateway.STRIPE, PaymentStatus.PENDING, null);
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    paymentService.initiatePayment(1L, 100L, PaymentGateway.STRIPE, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order does not exist")
        void orderNotFound_throws() {
            when(orderRepository.findByIdWithItems(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.initiatePayment(1L, 999L, PaymentGateway.STRIPE, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order");
        }
    }

    // handleWebhook()
    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("should set payment PAID and order CONFIRMED on success")
        void success_updatesPaymentAndOrder() {
            Payment payment = buildPayment(
                    PaymentGateway.STRIPE, PaymentStatus.PENDING, null);
            payment.setPaymentId("stripe_txn_success");

            when(paymentRepository.findByPaymentId("stripe_txn_success"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(orderRepository.save(any())).thenReturn(order);

            paymentService.handleWebhook("stripe_txn_success", true, null);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(payment.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("should set payment FAILED and store failure reason on failure")
        void failure_storesReasonAndStatusFailed() {
            Payment payment = buildPayment(
                    PaymentGateway.STRIPE, PaymentStatus.PENDING, null);
            payment.setPaymentId("stripe_txn_fail");

            when(paymentRepository.findByPaymentId("stripe_txn_fail"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(orderRepository.save(any())).thenReturn(order);

            paymentService.handleWebhook(
                    "stripe_txn_fail", false, "Your card has insufficient funds");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason())
                    .isEqualTo("Your card has insufficient funds");
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            // Order status should NOT change to CONFIRMED on failure
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for unknown paymentId")
        void unknownPaymentId_throws() {
            when(paymentRepository.findByPaymentId("unknown_txn"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.handleWebhook("unknown_txn", true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment");
        }
    }

    // getPaymentByOrder()
    @Nested
    @DisplayName("getPaymentByOrder()")
    class GetPaymentByOrder {

        @Test
        @DisplayName("should return payment response for order owner")
        void success_returnsPayment() {
            Payment payment = buildPayment(
                    PaymentGateway.STRIPE, PaymentStatus.PAID, null);

            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            PaymentResponse res = paymentService.getPaymentByOrder(1L, 100L);

            assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(res.getOrderId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user does not own the order")
        void wrongUser_throws() {
            User other = User.builder().id(99L).build();
            Order otherOrder = Order.builder()
                    .id(100L).user(other).orderStatus(OrderStatus.CREATED)
                    .totalPrice(BigDecimal.TEN).orderItems(new ArrayList<>()).build();

            when(orderRepository.findById(100L)).thenReturn(Optional.of(otherOrder));

            assertThatThrownBy(() -> paymentService.getPaymentByOrder(1L, 100L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no payment exists for order")
        void noPayment_throws() {
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentByOrder(1L, 100L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment");
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Payment buildPayment(PaymentGateway gateway,
                                 PaymentStatus status,
                                 String failureReason) {
        return Payment.builder()
                .id(1L).order(order)
                .paymentGateway(gateway)
                .paymentId("txn_" + System.currentTimeMillis())
                .amount(new BigDecimal("999.99"))
                .currency("USD")
                .status(status)
                .failureReason(failureReason)
                .build();
    }
}