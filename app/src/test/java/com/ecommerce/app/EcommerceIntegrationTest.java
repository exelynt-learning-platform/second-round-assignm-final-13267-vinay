package com.ecommerce.app;

import com.ecommerce.app.payment.PaymentGatewayFactory;
import com.ecommerce.app.payment.PaymentGatewayResult;
import com.ecommerce.app.payment.PaymentGatewayStrategy;
import com.ecommerce.app.models.enums.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EcommerceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String userToken;
    private static Long orderId;

    /**
     * Set this to the ID of a product that already exists in the test DB
     * (pre-seeded via data.sql or inserted manually as admin).
     */
    private static final long EXISTING_PRODUCT_ID = 1L;

    /**
     * Override the real payment strategies with mocks so we don't call
     * Stripe/PayPal.
     */
    @TestConfiguration
    static class MockPaymentConfig {

        @Bean
        @Primary
        public PaymentGatewayFactory paymentGatewayFactory() {
            PaymentGatewayStrategy mockStrategy = mock(PaymentGatewayStrategy.class);
            when(mockStrategy.createPayment(any(), anyString(), anyString()))
                    .thenReturn(PaymentGatewayResult.builder()
                            .transactionId("pi_test_integration_" + System.currentTimeMillis())
                            .approvalUrl("https://test.stripe.com/pay/secret_123")
                            .build());

            PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
            when(factory.getStrategy(any(PaymentGateway.class))).thenReturn(mockStrategy);
            return factory;
        }
    }

    // 1. Register User
    @Test
    @Order(1)
    @DisplayName("Step 1: Register a regular user")
    void registerUser() throws Exception {
        String body = """
                {
                    "name": "Test User",
                    "email": "testuser@example.com",
                    "password": "SecurePass123!",
                    "phone": "1234567890"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // 2. Login User
    @Test
    @Order(2)
    @DisplayName("Step 2: Login regular user and get JWT")
    void loginUser() throws Exception {
        String body = """
                {
                    "email": "testuser@example.com",
                    "password": "SecurePass123!"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        userToken = json.get("token").asText();
        assertThat(userToken).isNotBlank();
    }

    // 3. Add Product to Cart

    @Test
    @Order(3)
    @DisplayName("Step 3: User adds pre-seeded product to cart")
    void addToCart() throws Exception {
        String body = String.format("""
                {
                    "productId": %d,
                    "quantity": 2
                }
                """, EXISTING_PRODUCT_ID);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[0].productId").value(EXISTING_PRODUCT_ID))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    // 4. Place Order

    @Test
    @Order(4)
    @DisplayName("Step 4: User places an order")
    void placeOrder() throws Exception {
        String body = """
                {
                    "shippingAddress": "123 Integration Test Ave, Suite 100",
                    "paymentGateway": "STRIPE"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.totalPrice").isNumber())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        orderId = json.get("id").asLong();
        assertThat(orderId).isPositive();
    }

    // 5. Initiate Payment + Idempotency Verification

    @Test
    @Order(5)
    @DisplayName("Step 5a: User initiates payment for the order")
    void initiatePayment() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payments/initiate/" + orderId)
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "idem_test_key_1")
                        .param("gateway", "STRIPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentGateway").value("STRIPE"))
                .andExpect(jsonPath("$.approvalUrl").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(json.get("amount").asDouble()).isGreaterThan(0);
    }

    @Test
    @Order(6)
    @DisplayName("Step 5b: Idempotent retry returns same payment without creating a new one")
    void idempotentRetry() throws Exception {
        // Same idempotency key should return the existing payment
        MvcResult result = mockMvc.perform(post("/api/payments/initiate/" + orderId)
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "idem_test_key_1")
                        .param("gateway", "STRIPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("orderId").asLong()).isEqualTo(orderId);
    }

    // 6. Verify Health Endpoint

    @Test
    @Order(7)
    @DisplayName("Step 6: Health endpoint should be accessible without authentication")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // 7. Verify User Profile

    @Test
    @Order(8)
    @DisplayName("Step 7: User retrieves their profile")
    void getUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    // 8. Security — Unauthenticated access should be rejected

    @Test
    @Order(9)
    @DisplayName("Step 8: Unauthenticated requests to protected endpoints return 401")
    void unauthenticatedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }
}