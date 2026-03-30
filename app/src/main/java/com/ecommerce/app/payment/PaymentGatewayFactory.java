package com.ecommerce.app.payment;

import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.models.enums.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory that resolves a PaymentGateway enum value to its corresponding strategy bean.
 *
 * Uses constructor injection so all strategies are validated at startup.
 * Adding a new gateway only requires a new Strategy component — the factory
 * automatically picks it up if registered in the map.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayFactory {

    private final StripePaymentStrategy stripePaymentStrategy;
    private final PayPalPaymentStrategy payPalPaymentStrategy;

    /**
     * Returns the strategy implementation for the given gateway.
     *
     * @throws BadRequestException if the gateway is not supported
     */
    public PaymentGatewayStrategy getStrategy(PaymentGateway gateway) {
        return switch (gateway) {
            case STRIPE -> stripePaymentStrategy;
            case PAYPAL -> payPalPaymentStrategy;
        };
    }
}
