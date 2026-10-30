package com.devtunde.posbackend.order.internal.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.OrderLine;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);
}
