package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.OrderRequest;
import com.ecommerce.app.dto.response.OrderResponse;
import com.ecommerce.app.models.*;
import com.ecommerce.app.models.enums.OrderStatus;
import com.ecommerce.app.models.enums.PaymentGateway;
import com.ecommerce.app.models.enums.PaymentStatus;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.AccessDeniedException;
import com.ecommerce.app.exception.InsufficientStockException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.repository.CartRepository;
import com.ecommerce.app.repository.OrderRepository;
import com.ecommerce.app.repository.ProductRepository;
import com.ecommerce.app.repository.UserRepository;
import com.ecommerce.app.services.CartService;
import com.ecommerce.app.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository   orderRepository;
    private final CartRepository    cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository    userRepository;
    private final CartService       cartService;

    // Place order — validates stock, creates order + items, decrements stock
    @Override
    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {

        // 1. Load the user's cart (with items eagerly fetched)
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));

        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cannot place an order with an empty cart");
        }

        // 2. Validate gateway enum
        PaymentGateway gateway;
        try {
            gateway = PaymentGateway.valueOf(request.getPaymentGateway().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid payment gateway: " + request.getPaymentGateway()
                    + ". Accepted values: STRIPE, PAYPAL");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // 3. Validate and decrement stock atomically for each item
        List<OrderItem> orderItems = cart.getCartItems().stream().map(cartItem -> {
            Product product = cartItem.getProduct();
            int     qty     = cartItem.getQuantity();

            int updated = productRepository.decrementStock(product.getId(), qty);
            if (updated == 0) {
                throw new InsufficientStockException(product.getName(), qty, product.getStockQuantity());
            }

            return OrderItem.builder()
                    .product(product)
                    .quantity(qty)
                    .price(product.getPrice())           // price snapshot
                    .build();
        }).toList();

        // 4. Calculate total
        BigDecimal total = orderItems.stream()
                .map(oi -> oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Build and persist the Order
        Order order = Order.builder()
                .user(user)
                .totalPrice(total)
                .shippingAddress(request.getShippingAddress())
                .orderStatus(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        orderItems.forEach(item -> {
            item.setOrder(order);
            order.getOrderItems().add(item);
        });

        Order saved = orderRepository.save(order);

        // 6. Clear the cart after successful order placement
        cartService.clearCart(userId);

        return toResponse(saved);
    }

    // Get a single order — enforces user ownership
    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to view this order");
        }

        return toResponse(order);
    }

    // All orders for a user (paginated)
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    // Cancel order — only CREATED or CONFIRMED orders can be cancelled
    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getOrderStatus() == OrderStatus.SHIPPED
                || order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException(
                    "Cannot cancel an order that has already been " + order.getOrderStatus());
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled");
        }

        // Restore stock for each item
        order.getOrderItems().forEach(item ->
                productRepository.findById(item.getProduct().getId()).ifPresent(p -> {
                    p.setStockQuantity(p.getStockQuantity() + item.getQuantity());
                    productRepository.save(p);
                })
        );

        order.setOrderStatus(OrderStatus.CANCELLED);
        return toResponse(orderRepository.save(order));
    }

    // Mapper
    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items = order.getOrderItems().stream()
                .map(oi -> OrderResponse.OrderItemResponse.builder()
                        .productId(oi.getProduct().getId())
                        .productName(oi.getProduct().getName())
                        .quantity(oi.getQuantity())
                        .price(oi.getPrice())
                        .subtotal(oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .totalPrice(order.getTotalPrice())
                .shippingAddress(order.getShippingAddress())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }
}