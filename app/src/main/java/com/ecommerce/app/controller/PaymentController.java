package com.ecommerce.app.controller;

import com.ecommerce.app.dto.response.PaymentResponse;
import com.ecommerce.app.models.User;
import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.services.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper   objectMapper = new ObjectMapper();

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    // Initiate payment for an order
    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId,
            @RequestParam String gateway,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        PaymentGateway paymentGateway = PaymentGateway.valueOf(gateway.toUpperCase());

        // Generate idempotency key from orderId if not provided
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = "payment_" + orderId + "_" + paymentGateway.name();
        }

        PaymentResponse response = paymentService.initiatePayment(
                user.getId(), orderId, paymentGateway, idempotencyKey);

        return ResponseEntity.ok(response);
    }

    // Get payment status for an order
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId) {

        return ResponseEntity.ok(paymentService.getPaymentByOrder(user.getId(), orderId));
    }

    // Stripe Webhook — proper signature verification + Event parsing
    @PostMapping("/webhook/stripe")
    public ResponseEntity<Map<String, String>> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            // 1. Verify the webhook signature using the Stripe SDK
            Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);

            // 2. Route based on event type
            switch (event.getType()) {
                case "payment_intent.succeeded" -> {
                    PaymentIntent intent = (PaymentIntent)
                            event.getDataObjectDeserializer().getObject().orElse(null);
                    if (intent != null) {
                        log.info("Stripe webhook: payment_intent.succeeded, id={}", intent.getId());
                        paymentService.handleWebhook(intent.getId(), true, null);
                    }
                }
                case "payment_intent.payment_failed" -> {
                    PaymentIntent intent = (PaymentIntent)
                            event.getDataObjectDeserializer().getObject().orElse(null);
                    if (intent != null) {
                        String failureMessage = intent.getLastPaymentError() != null
                                ? intent.getLastPaymentError().getMessage()
                                : "Payment failed";
                        log.warn("Stripe webhook: payment_intent.payment_failed, id={}",
                                intent.getId());
                        paymentService.handleWebhook(intent.getId(), false, failureMessage);
                    }
                }
                default -> log.info("Stripe webhook: unhandled event type={}", event.getType());
            }

            return ResponseEntity.ok(Map.of("status", "received"));

        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed"));
        }
    }

    // PayPal Webhook — proper JSON parsing + event handling
    @PostMapping("/webhook/paypal")
    public ResponseEntity<Map<String, String>> paypalWebhook(@RequestBody String payload) {
        try {
            // Parse the webhook payload using Jackson
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event_type").asText();

            JsonNode resource = rootNode.path("resource");
            String paypalOrderId = resource.path("id").asText();

            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED", "CHECKOUT.ORDER.APPROVED" -> {
                    log.info("PayPal webhook: {}, orderId={}", eventType, paypalOrderId);
                    paymentService.handleWebhook(paypalOrderId, true, null);
                }
                case "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.DECLINED" -> {
                    String reason = resource.path("status_details")
                            .path("reason").asText("Payment denied");
                    log.warn("PayPal webhook: {}, orderId={}, reason={}",
                            eventType, paypalOrderId, reason);
                    paymentService.handleWebhook(paypalOrderId, false, reason);
                }
                default -> log.info("PayPal webhook: unhandled event type={}", eventType);
            }

            return ResponseEntity.ok(Map.of("status", "received"));

        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed"));
        }
    }
}