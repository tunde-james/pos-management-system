package com.devtunde.posbackend.order.internal.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPublicId(UUID publicId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findBySessionIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long sessionId, OrderStatus status, LocalDateTime now);
}
