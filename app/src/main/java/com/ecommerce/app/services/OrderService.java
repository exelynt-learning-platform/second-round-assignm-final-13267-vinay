package com.ecommerce.app.services;

import com.ecommerce.app.dto.request.OrderRequest;
import com.ecommerce.app.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse        placeOrder(Long userId, OrderRequest request);
    OrderResponse        getOrderById(Long userId, Long orderId);
    Page<OrderResponse>  getUserOrders(Long userId, Pageable pageable);
    OrderResponse        cancelOrder(Long userId, Long orderId);
}