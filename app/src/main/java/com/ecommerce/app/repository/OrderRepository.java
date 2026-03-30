package com.ecommerce.app.repository;

import com.ecommerce.app.models.Order;
import com.ecommerce.app.models.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetch order with items + products in one query (avoids N+1 on order detail page)
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.product " +
            "WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    // All orders belonging to a user (paginated for large order histories)
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // Ownership check — used by service layer to enforce users can only access their own orders
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    // Admin: filter by status
    Page<Order> findByOrderStatus(OrderStatus status, Pageable pageable);
}