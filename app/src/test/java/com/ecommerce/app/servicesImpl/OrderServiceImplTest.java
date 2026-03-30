package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.OrderRequest;
import com.ecommerce.app.dto.response.OrderResponse;
import com.ecommerce.app.exception.AccessDeniedException;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.InsufficientStockException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.*;
import com.ecommerce.app.models.enums.OrderStatus;
import com.ecommerce.app.models.enums.PaymentStatus;
import com.ecommerce.app.repository.CartRepository;
import com.ecommerce.app.repository.OrderRepository;
import com.ecommerce.app.repository.ProductRepository;
import com.ecommerce.app.repository.UserRepository;
import com.ecommerce.app.services.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock OrderRepository   orderRepository;
    @Mock CartRepository    cartRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository    userRepository;
    @Mock CartService       cartService;

    @InjectMocks OrderServiceImpl orderService;

    private User     user;
    private Product  product;
    private Cart     cart;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("alice@example.com").build();

        product = Product.builder()
                .id(5L).name("Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .build();

        cartItem = CartItem.builder()
                .id(1L).product(product).quantity(2).build();

        cart = Cart.builder()
                .id(10L).user(user)
                .cartItems(new ArrayList<>(List.of(cartItem)))
                .build();

        cartItem.setCart(cart);
    }

    // placeOrder()
    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test
        @DisplayName("should create order with correct total price from cart items")
        void success_correctTotal() {
            mockSuccessfulOrder();

            OrderResponse res = orderService.placeOrder(1L, buildRequest("STRIPE"));

            // 999.99 × 2 = 1999.98
            assertThat(res.getTotalPrice()).isEqualByComparingTo(new BigDecimal("1999.98"));
            assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("should decrement product stock for each cart item")
        void success_stockDecremented() {
            mockSuccessfulOrder();

            orderService.placeOrder(1L, buildRequest("STRIPE"));

            verify(productRepository).decrementStock(5L, 2);
        }

        @Test
        @DisplayName("should clear the cart after successful order placement")
        void success_cartCleared() {
            mockSuccessfulOrder();

            orderService.placeOrder(1L, buildRequest("PAYPAL"));

            verify(cartService).clearCart(1L);
        }

        @Test
        @DisplayName("should snapshot product price into OrderItem (not live price)")
        void success_priceSnapshotted() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.decrementStock(5L, 2)).thenReturn(1);
            doNothing().when(cartService).clearCart(1L);

            Order savedOrder = Order.builder()
                    .id(100L).user(user)
                    .totalPrice(new BigDecimal("1999.98"))
                    .shippingAddress("123 Main St")
                    .orderStatus(OrderStatus.CREATED)
                    .paymentStatus(PaymentStatus.PENDING)
                    .orderItems(List.of(OrderItem.builder()
                            .id(1L).product(product).quantity(2)
                            .price(new BigDecimal("999.99")).build()))
                    .build();

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse res = orderService.placeOrder(1L, buildRequest("STRIPE"));

            assertThat(res.getItems().get(0).getPrice())
                    .isEqualByComparingTo(new BigDecimal("999.99"));
        }

        @Test
        @DisplayName("should throw BadRequestException when cart is empty")
        void emptyCart_throws() {
            Cart emptyCart = Cart.builder().id(10L).user(user)
                    .cartItems(new ArrayList<>()).build();
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(emptyCart));

            assertThatThrownBy(() -> orderService.placeOrder(1L, buildRequest("STRIPE")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("empty cart");

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).clearCart(any());
        }

        @Test
        @DisplayName("should throw InsufficientStockException when stock too low")
        void outOfStock_throws() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.decrementStock(5L, 2)).thenReturn(0); // 0 rows = failed

            assertThatThrownBy(() -> orderService.placeOrder(1L, buildRequest("STRIPE")))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Laptop");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BadRequestException for unsupported gateway")
        void invalidGateway_throws() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> orderService.placeOrder(1L, buildRequest("BITCOIN")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid payment gateway");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when cart not found")
        void cartNotFound_throws() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(1L, buildRequest("STRIPE")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private void mockSuccessfulOrder() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.decrementStock(5L, 2)).thenReturn(1);
            doNothing().when(cartService).clearCart(1L);

            Order savedOrder = Order.builder()
                    .id(100L).user(user)
                    .totalPrice(new BigDecimal("1999.98"))
                    .shippingAddress("123 Main St")
                    .orderStatus(OrderStatus.CREATED)
                    .paymentStatus(PaymentStatus.PENDING)
                    .orderItems(new ArrayList<>())
                    .build();

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        }
    }

    // getOrderById()
    @Nested
    @DisplayName("getOrderById()")
    class GetOrderById {

        @Test
        @DisplayName("should return order when user is the owner")
        void success_ownerGetsOrder() {
            Order order = buildOrder(user, OrderStatus.CREATED);
            when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

            OrderResponse res = orderService.getOrderById(1L, 1L);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user is not the owner")
        void notOwner_throws() {
            User otherUser = User.builder().id(99L).build();
            Order order = buildOrder(otherUser, OrderStatus.CREATED);
            when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrderById(1L, 1L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("permission");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order does not exist")
        void orderNotFound_throws() {
            when(orderRepository.findByIdWithItems(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // getUserOrders()
    @Nested
    @DisplayName("getUserOrders()")
    class GetUserOrders {

        @Test
        @DisplayName("should return paginated orders for a user")
        void success_returnsPaginatedOrders() {
            Order order = buildOrder(user, OrderStatus.DELIVERED);
            Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
            when(orderRepository.findByUserId(1L, PageRequest.of(0, 10))).thenReturn(page);

            Page<OrderResponse> result =
                    orderService.getUserOrders(1L, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getOrderStatus())
                    .isEqualTo(OrderStatus.DELIVERED);
        }
    }

    // cancelOrder()
    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("should cancel CREATED order and restore product stock")
        void createdOrder_cancelledSuccessfully() {
            Order order = buildOrder(user, OrderStatus.CREATED);
            order.getOrderItems().add(OrderItem.builder()
                    .id(1L).product(product).quantity(2)
                    .price(new BigDecimal("999.99")).build());

            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(5L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenReturn(product);
            when(orderRepository.save(any(Order.class))).thenReturn(order);

            OrderResponse res = orderService.cancelOrder(1L, 1L);

            assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            // stock 10 + returned 2 = 12
            assertThat(product.getStockQuantity()).isEqualTo(12);
        }

        @Test
        @DisplayName("should throw BadRequestException when order is already SHIPPED")
        void shippedOrder_cannotBeCancelled() {
            Order order = buildOrder(user, OrderStatus.SHIPPED);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("SHIPPED");
        }

        @Test
        @DisplayName("should throw BadRequestException when order is already DELIVERED")
        void deliveredOrder_cannotBeCancelled() {
            Order order = buildOrder(user, OrderStatus.DELIVERED);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DELIVERED");
        }

        @Test
        @DisplayName("should throw BadRequestException when order is already CANCELLED")
        void alreadyCancelled_throws() {
            Order order = buildOrder(user, OrderStatus.CANCELLED);
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already cancelled");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private OrderRequest buildRequest(String gateway) {
        OrderRequest r = new OrderRequest();
        r.setShippingAddress("123 Main St");
        r.setPaymentGateway(gateway);
        return r;
    }

    private Order buildOrder(User owner, OrderStatus status) {
        return Order.builder()
                .id(1L).user(owner)
                .totalPrice(new BigDecimal("1999.98"))
                .shippingAddress("123 Main St")
                .orderStatus(status)
                .paymentStatus(PaymentStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build();
    }
}