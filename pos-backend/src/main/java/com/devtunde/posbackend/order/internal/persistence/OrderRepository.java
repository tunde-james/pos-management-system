package com.devtunde.posbackend.order.internal.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.posbackend.order.internal.domain.Order;
import com.devtunde.posbackend.order.internal.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.publicId = :publicId")
    Optional<Order> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findBySessionIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long sessionId, OrderStatus status, LocalDateTime now);
}
