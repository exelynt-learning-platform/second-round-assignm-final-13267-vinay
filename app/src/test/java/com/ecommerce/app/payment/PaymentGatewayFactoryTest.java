package com.ecommerce.app.payment;

import com.ecommerce.app.models.enums.PaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGatewayFactory")
class PaymentGatewayFactoryTest {

    @Mock StripePaymentStrategy stripePaymentStrategy;
    @Mock PayPalPaymentStrategy payPalPaymentStrategy;

    @InjectMocks PaymentGatewayFactory factory;

    @Test
    @DisplayName("should return StripePaymentStrategy for STRIPE gateway")
    void stripe_returnsCorrectStrategy() {
        PaymentGatewayStrategy result = factory.getStrategy(PaymentGateway.STRIPE);
        assertThat(result).isSameAs(stripePaymentStrategy);
    }

    @Test
    @DisplayName("should return PayPalPaymentStrategy for PAYPAL gateway")
    void paypal_returnsCorrectStrategy() {
        PaymentGatewayStrategy result = factory.getStrategy(PaymentGateway.PAYPAL);
        assertThat(result).isSameAs(payPalPaymentStrategy);
    }
}
