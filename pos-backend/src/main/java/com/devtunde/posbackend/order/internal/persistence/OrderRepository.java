package com.devtunde.posbackend.order.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPublicId(UUID publicId);
}
