package com.ecommerce.app.payment;

import com.ecommerce.app.exception.PaymentException;
import com.ecommerce.app.models.Order;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.models.*;
import com.paypal.sdk.http.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * PayPal implementation of the PaymentGatewayStrategy.
 *
 * Creates a PayPal order using the newer paypal-server-sdk.
 * Returns the PayPal order ID and the approval redirect URL.
 */
@Slf4j
@Component
public class PayPalPaymentStrategy implements PaymentGatewayStrategy {

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    @Value("${paypal.mode:sandbox}")
    private String mode;

    @Override
    public PaymentGatewayResult createPayment(Order order, String currency, String idempotencyKey) {
        try {
            Environment environment = "live".equalsIgnoreCase(mode)
                    ? Environment.PRODUCTION
                    : Environment.SANDBOX;

            PaypalServerSdkClient client = new PaypalServerSdkClient.Builder()
                    .environment(environment)
                    .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(clientId, clientSecret).build())
                    .build();

            OrdersController ordersController = client.getOrdersController();

            AmountWithBreakdown amount = new AmountWithBreakdown.Builder(currency, order.getTotalPrice().toPlainString()).build();

            PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest.Builder(amount)
                    .referenceId(order.getId().toString())
                    .build();

            OrderRequest orderRequest = new OrderRequest.Builder(
                    CheckoutPaymentIntent.CAPTURE,
                    Collections.singletonList(purchaseUnit)
            ).build();

            CreateOrderInput input = new CreateOrderInput();
            input.setBody(orderRequest);
            input.setPaypalRequestId(idempotencyKey);

            ApiResponse<com.paypal.sdk.models.Order> response = ordersController.createOrder(input);
            com.paypal.sdk.models.Order ppOrder = response.getResult();

            // Extract the approval link for frontend redirect
            String approvalLink = null;
            if (ppOrder.getLinks() != null) {
                approvalLink = ppOrder.getLinks().stream()
                        .filter(link -> "approve".equals(link.getRel()))
                        .map(LinkDescription::getHref)
                        .findFirst()
                        .orElse(null);
            }

            log.info("PayPal order created: id={}, orderId={}", ppOrder.getId(), order.getId());

            return PaymentGatewayResult.builder()
                    .transactionId(ppOrder.getId())
                    .approvalUrl(approvalLink)
                    .build();

        } catch (Exception e) {
            log.error("PayPal API error for orderId={}: {}", order.getId(), e.getMessage(), e);
            throw new PaymentException("PayPal payment creation failed: " + e.getMessage(), e);
        }
    }
}
